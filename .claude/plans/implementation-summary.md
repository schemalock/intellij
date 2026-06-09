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
