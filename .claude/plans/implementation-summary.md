# Implementation Summary — 2026-06-11 — suppress-native-yaml-inspections

## Agent
implementer

## Plan Reference
`active-plan.md` — "Eliminate false diagnostics shown on SchemaLock-owned CRD YAML files" (InspectionSuppressor + daemon restart)

## Changes Made

### New files

- `src/main/kotlin/dev/schemalock/intellij/SchemalockInspectionSuppressor.kt`
  — `InspectionSuppressor` for YAML. Pure top-level `shouldSuppress(toolId, owned, isKey)` function (unit-testable, no PSI deps). PSI glue in `isSuppressedFor`: resolves `url` from `element.containingFile?.virtualFile?.url`, looks up `DocumentStateBus.getInstance(project).get(url)`, checks `state.state != 0` for ownership, uses `PsiTreeUtil.isAncestor(kv.key!!, element, false)` for key-position detection. `getSuppressActions` returns `SuppressQuickFix.EMPTY_ARRAY`. Constants `KUBERNETES_UNKNOWN_TOOL_IDS` (set of 3) and `SPELL_CHECK_TOOL_ID`.

- `src/test/kotlin/dev/schemalock/intellij/SchemalockInspectionSuppressorTest.kt`
  — JUnit 5 unit tests for `shouldSuppress`. Covers: not-owned (all 4 tool IDs, both isKey values) → false; owned + each k8s tool (both isKey values) → true; owned + spell check + isKey=true → true; owned + spell check + isKey=false → false; owned + unrelated tool → false. Mirrors `DocumentStateTest` style.

### Edited files

- `src/main/kotlin/dev/schemalock/intellij/DocumentStateBus.kt`
  — Added `private val project: Project` ctor parameter. Added `invokeLater` block in `onState` after the existing `states[uri] = state` + `widget?.onState` lines: resolves `VirtualFile` + `PsiFile`, calls `DaemonCodeAnalyzer.getInstance(project).restart(psi)` on EDT with `project.isDisposed` guard. New imports: `DaemonCodeAnalyzer`, `ApplicationManager`, `VirtualFileManager`, `PsiManager`. `attach`/`detach`/`get`/`getInstance` untouched.

- `src/main/resources/META-INF/plugin.xml`
  — Added `<depends>org.jetbrains.plugins.yaml</depends>` after the platform depends. Added `<lang.inspectionSuppressor language="yaml" implementationClass="dev.schemalock.intellij.SchemalockInspectionSuppressor"/>` inside the existing extensions block.

- `build.gradle.kts`
  — Added `bundledPlugin("org.jetbrains.plugins.yaml")` inside `dependencies { intellijPlatform { … } }`.

## API Surface Changes
No new public API. `DocumentStateBus` ctor now takes `Project` — constructor injection handled by the platform service container; `getInstance` / call sites unchanged.

## How to Test

1. `./gradlew clean test buildPlugin` — green (all 13 `shouldSuppress` test cases pass, plugin ZIP built).
2. Manual GoLand smoke check on a SchemaLock-owned CRD YAML (`state != 0`): Kubernetes "Unknown*" ERROR markers and spell-check "Typo" warnings on field keys should be absent. Spell-check on string values / descriptions / comments should still appear.
3. On a non-owned YAML (no SchemaLock resolution), native inspections must still fire.
4. Open an owned CRD file fresh (resolution arrives async): after the server-push `onState` fires, the daemon restarts and false markers clear without a manual edit.

## Gradle Result

`./gradlew clean test buildPlugin` — **BUILD SUCCESSFUL in 26s**. All tasks clean; 21 actionable tasks, 19 executed, 2 from cache. JUnit: all tests green (includes new `SchemalockInspectionSuppressorTest`).

`./gradlew verifyPlugin` — **BUILD SUCCESSFUL in 1m 2s**. All 7 recommended IDEs: **Compatible**. Deprecation notice on IDEs 253/261/262 for `DaemonCodeAnalyzer.restart(PsiFile)` — warning only, not an error; method still present and functional within the `sinceBuild=242` target range. Pre-existing internal-API notice for `BinaryResolver` (`PluginManagerCore.getPlugin`) — not introduced by this change.

## Deviations from Plan
None. All five deliverables implemented exactly as specified. The `DaemonCodeAnalyzer.restart(PsiFile)` deprecation on newer IDE versions was anticipated as a known risk in the plan ("no debouncing, simplicity first") and does not affect the target platform (IU-2024.2 / sinceBuild 242).

## Follow-ups for Other Agents
- Reviewer: check `isSuppressedFor` PSI glue (url → owned → isKey), the `onState` restart block (threading + guard), and the `plugin.xml` / `build.gradle.kts` changes for correctness and plan conformance.
- Manual GoLand smoke check required (criteria 3–5 in the plan) before tagging/publishing.
- `DaemonCodeAnalyzer.restart(PsiFile)` is deprecated in platform 253+; if the plugin eventually raises `sinceBuild` past 253, switch to the replacement API (check platform release notes at that time).

---

# Implementation Summary

## Agent
implementer

## Plan Reference
Tasks 5, 6, 7 — Status bar widget, LSP request wiring, version picker

## Changes Made

### New files

- `src/main/kotlin/dev/schemalock/intellij/DocumentStatusWidget.kt`
  — StatusBarWidget + TextPresentation impl; shows widgetText/widgetTooltip from DocumentState; on click launches the version picker popup.

- `src/main/kotlin/dev/schemalock/intellij/DocumentStatusWidgetFactory.kt`
  — StatusBarWidgetFactory registration; createWidget wires LspRequestHelper.connect and registers the MessageBusConnection as a child Disposable.

- `src/main/kotlin/dev/schemalock/intellij/LspRequestHelper.kt`
  — FileEditorManagerListener subscription; sends custom LSP requests via ServiceEndpoints.toEndpoint(server.lsp4jServer).request(method, params); implements getDocumentState, sendVersionOverride, listVersionsForGroup.

## API Issues Found

`LspServer.sendRequest(method, params)` does not exist in IntelliJ 2024.2. The actual API is:
- `LspServer.sendRequestSync(timeoutMs, lambda: LanguageServer -> CompletableFuture<T>)` — for lsp4j standard methods only
- `LspServer.lsp4jServer` — returns the `LanguageServer` proxy (marked `@Deprecated`)

Resolution: used `ServiceEndpoints.toEndpoint(server.lsp4jServer).request(method, params)` from `org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints`. This converts the proxy back to the underlying `Endpoint` (which is the `RemoteEndpoint`) and calls `request(String, Object): CompletableFuture<Object>`. The `lsp4jServer` deprecation warning fires but no non-deprecated path exists for custom method names in 2024.2.

## How to Test

1. Open a project with a `schemalock.lock` file and at least one `.yaml` file
2. Open the YAML file — the status bar widget should show the schema state (e.g., "🔒 CRD · v1.0.0")
3. Click the widget — a popup should appear listing the 5 most recent versions for the schema group
4. Selecting a version sends `schemalock/setDocumentVersionOverride` to the LSP server
5. "Show all N versions…" reopens the picker with the full list

## Follow-ups for Other Agents

- The `lsp4jServer` deprecation: when the plugin targets IntelliJ 2025.1+, check if a non-deprecated custom-request API is available (e.g., via `LspServer.getRequestExecutor()`).
- The Task 7 commit ("feat: add version picker popup to DocumentStatusWidget") was merged into the Task 5 commit since both tasks write to `DocumentStatusWidget.kt` and the code was authored together. The logical deliverable is complete.

---

# 2026-06-09 — Gradle Plugin 2.x migration + widget fix (Phases A–C)

Branch `feature/gradle-plugin-2x-widget-fix`. Implements `.claude/plans/active-plan.md`.
**Supersedes the "API Issues Found" section above** — the `ServiceEndpoints` /
`lsp4jServer` hack is now removed; that was the root cause of the runtime
`NoSuchMethodError: getLsp4jServer()`.

## Phase A — Gradle Plugin 1.x → 2.x (build)
- `settings.gradle.kts` — added `org.jetbrains.intellij.platform.settings` **2.7.1**
  (latest line accepting Gradle 8.6/8.7) + `intellijPlatform { defaultRepositories() }`
  in `dependencyResolutionManagement`. **Kept Gradle 8.7 wrapper (Path 1)** — no
  wrapper/JDK bump. `rootProject.name = "schemalock"`.
- `build.gradle.kts` — plugin id → `org.jetbrains.intellij.platform` (version from
  settings plugin); IDE via `dependencies { intellijPlatform { create("IU","2024.2") } }`;
  `intellijPlatform { pluginConfiguration { name="SchemaLock"; ideaVersion { sinceBuild="242"; untilBuild=null } }; pluginVerification { ides { recommended() } } }`;
  `version = "0.1.0"` hardcoded (dropped `.app-version` derivation — independent
  plugin lifecycle); `PrepareSandboxTask` import → v2 package; binary graft retargeted.

## Phase B — typed custom LSP requests (code)
- New `SchemalockLspServer.kt` — typed interface extending lsp4j `LanguageServer`,
  three `@JsonRequest` methods (contract strings byte-identical) + param/result data classes.
- `SchemalockLspServerDescriptor.kt` — `override val lsp4jServerClass = SchemalockLspServer::class.java`.
- `LspRequestHelper.kt` — dispatch via `server.sendRequestSync { (it as SchemalockLspServer).method(params) }`.
  Removed `sendCustomRequest`, `Gson`, `ServiceEndpoints`, `server.lsp4jServer`.

## Resolved-at-build-time (the plan's open unknowns)
1. **`sendRequestSync` overload** — the single-arg lambda form
   `sendRequestSync { it.method(params) }` compiles cleanly in 2024.2 (no timeout arg
   needed). Confirmed by successful compile, not docs.
2. **v2 sandbox task name** — `prepareTestSandbox` (not the 1.x `prepareTestingSandbox`).
   Verified via `gradle tasks --all | grep sandbox`.
3. **Sandbox plugin dir** is derived from `rootProject.name` (`schemalock`), **not**
   `pluginConfiguration.name`. The binary graft must therefore target `schemalock/bin`;
   `pluginConfiguration.name="SchemaLock"` only sets the displayed `<name>`.

## Rename (verifyPlugin gate)
v2 `verifyPlugin` rejected the old identity (`TemplateWordInPluginId/Name` — "intellij").
These **cannot be muted at Marketplace upload**, so renamed (not muted):
- plugin `<id>` `dev.schemalock.intellij` → **`dev.schemalock`** (plugin.xml + `BinaryResolver`
  `PluginId.getId`, kept in lockstep). The Kotlin **package** `dev.schemalock.intellij` is
  unchanged — the verifier checks the plugin id/name, not the code namespace.
- effective name `schemalock-intellij` → **`SchemaLock`**.
- `.gitignore` — added `.intellijPlatform/`.

## Verification (real output)
`./gradlew clean buildPlugin verifyPlugin test` → **BUILD SUCCESSFUL in 2m 30s**.
- `verifyPlugin`: `dev.schemalock:0.1.0` **Compatible** against all 7 recommended IDEs
  (IU-242, 243, 251, 252, 253, 261, 262). 0 problems.
- `test`: green (`DocumentStateTest`, `BinaryResolverTest`).
- `buildPlugin`: `build/distributions/schemalock-0.1.0.zip` with all 5 platform binaries
  at `schemalock/bin/<platform>/` (the path `BinaryResolver.resolve()` reads via pluginPath).
- Only a Gradle-9-deprecation warning (expected on Path 1; harmless).

## Not done (out of scope this pass)
- **C3** manual GoLand smoke test — needs a human + IDE. The original `NoSuchMethodError`
  scenario should be re-run there before release.
- **Phase D** release/tag/Marketplace publish.
- Changes left **uncommitted** in the working tree on the feature branch.

## Reviewer note
Warrants a reviewer pass on the rename ripple (id/name/dir-name coupling) and the
typed-request rewrite before commit.
