# Architecture Decisions — intellij/

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-06-09 | Migrate to IntelliJ Platform Gradle Plugin 2.x; pin `2.6.1`, keep Gradle 8.7 wrapper | Plugin 1.17.4 is EOL for 2024.2+ and resolves a stale LSP artifact (`getLsp4jServer()` runtime crash). Plugin 2.12.0+ forces Gradle 9.0; 2.0–2.7.x accept 8.5/8.6 → 2.6.1 is the minimal-blast-radius pin. |
| 2026-06-09 | Custom LSP requests use typed `@JsonRequest` interface + `lsp4jServerClass` + `sendRequestSync` | The `server.lsp4jServer` + `ServiceEndpoints` raw-JSON-RPC path is unsupported and breaks across IDE runtimes. Typed interface is the documented JetBrains API. |
| 2026-06-09 | Plugin versions independently of `../app/`; `version = "0.1.0"` hardcoded | Decouple the plugin's Marketplace lifecycle from the bundled app binary version (binaries stay pinned via `.app-version`, currently v0.3.1). |
| 2026-06-09 | Renamed plugin id `dev.schemalock.intellij`→`dev.schemalock`, name `schemalock-intellij`→`SchemaLock` | v2 `verifyPlugin` rejects "intellij"/"jetbrains" template words in id/name, and these **cannot be muted at Marketplace upload** — rename was the only real fix. Kotlin package `dev.schemalock.intellij` left unchanged (verifier checks id/name, not the code namespace). Sandbox dir = `rootProject.name` (`schemalock`), so binary graft targets `schemalock/bin`. |
