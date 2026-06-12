# SchemaLock — JetBrains Plugin

Versioned, integrity-pinned schemas for YAML — autocomplete, hover docs,
and validation for Kubernetes CRDs from
[cdn.schemalock.dev](https://cdn.schemalock.dev), inside IntelliJ-based IDEs.

Open any Kubernetes CRD YAML and SchemaLock fetches the right JSON Schema
from the SchemaLock CDN, validates the document, and powers completion and
hover. Commit a `schemalock.yaml` to pin exact CRD versions across your
team with integrity hashes — every schema verified before use, no
trust-on-first-use, no API key, no cloud sync.

The bundled `schemalock` binary lives in
[schemalock/schemalock](https://github.com/schemalock/schemalock); this repo is the
thin JetBrains shell around it.

## Requirements

The plugin uses the IntelliJ Platform LSP API, which is **Ultimate-only**.
It runs in IU-based IDEs — **GoLand, IntelliJ IDEA Ultimate, WebStorm,
PyCharm Professional**, etc. — and does **not** run in Community editions
or Android Studio.

## Install

**JetBrains Marketplace**

Search for **SchemaLock** in *Settings → Plugins → Marketplace*, or install
the [`dev.schemalock`](https://plugins.jetbrains.com/plugin/dev.schemalock)
plugin from the Marketplace site.

**Manual**

Download the plugin ZIP from the [Releases](https://github.com/schemalock/intellij/releases)
page and install via *Settings → Plugins → ⚙ → Install Plugin from Disk…*.

## Quick start — no lockfile required

1. Open any Kubernetes CRD YAML. The plugin starts the bundled LSP server
   automatically for YAML files.
2. The schema for the document's `apiVersion`+`kind` is fetched from
   `cdn.schemalock.dev` (cached locally after the first request) and used
   for:
   - **Autocomplete** for properties and enum values.
   - **Hover docs** from the operator's CRD `description` fields.
   - **Red squiggles** on type errors.
3. The status-bar widget shows the resolution state for the active file:
   🔒 *Pinned*, 🔓 *Unpinned*, 👁 *Preview*, or ⚠ *Error*. Click it to pick a
   different version for the active file (preview only — does not modify any
   `schemalock.yaml`).

## Pin CRD versions for your team

For reproducible validation across CI and contributors, commit a
`schemalock.yaml` to your repo.

1. Add an operator pin (`schemalock` CLI is bundled with the plugin, or
   install separately with
   `go install github.com/schemalock/schemalock/cmd/schemalock@latest`):

   ```bash
   schemalock add operator.victoriametrics.com@0.70.0
   ```

   This writes (or updates) the nearest `schemalock.yaml`:

   ```yaml
   version: 1
   ecosystems:
     kubernetes:
       - operator.victoriametrics.com@0.70.0
   ```

2. The server reloads automatically on save — no generation step. Files
   whose `apiVersion`+`kind` match a pinned entry are served from the
   integrity-verified schema; everything else uses the CDN fallback.

### Hierarchical pinning

`schemalock.yaml` files nest. Place a root file at the repo root and
overlay files in sub-directories — the effective pin set for each manifest
is the union of every `schemalock.yaml` walking up from that file's
directory, with the closest file winning on conflicts. A nested file with
`root: true` stops the walk (useful for monorepo sub-projects).

## How it works

A single `schemalock serve --stdio` subprocess runs as the LSP server for
`yaml` documents. For documents whose `apiVersion`+`kind` resolve through
the nearest `schemalock.yaml` (or the CDN fallback), SchemaLock provides
diagnostics, completion, and hover using the integrity-verified schema.
Documents not covered by any intent file auto-fetch the latest available
schema from `cdn.schemalock.dev`.

### Coexistence with bundled IDE inspections

IU-based IDEs bundle a Kubernetes plugin and a spell checker that both run
on YAML. The Kubernetes plugin doesn't know CDN-served CRD schemas, so it
would otherwise flag valid resources as "Unknown API version" / "Unknown
resource", and the spell checker flags CRD field names (`vmselect`,
`tolerations`, …) as typos. SchemaLock suppresses those native inspections
**only on files it owns** (a resolved CRD), so plain Kubernetes manifests
and non-CRD YAML keep the IDE's built-in validation untouched.

## Configuration

The plugin works with no configuration. The bundled binary is resolved
per-platform from the installed plugin directory; no settings are required
for the default flow.

## Troubleshooting

**No diagnostics / no autocomplete**

1. Confirm the document has a recognised `apiVersion` and `kind`.
2. Check the IDE log (*Help → Show Log in Finder/Explorer*) for
   `dev.schemalock` entries and confirm the LSP server started.
3. Verify your IDE is an Ultimate/IU-based edition (see Requirements).

**Wrong binary architecture**

The plugin bundles binaries for `darwin-arm64`, `darwin-x64`,
`linux-arm64`, `linux-x64`, and `win32-x64`. On an unsupported platform,
build the `schemalock` binary yourself
(`go install github.com/schemalock/schemalock/cmd/schemalock@latest`).

## Building from source

Toolchain: Kotlin 2.2.0, **JDK 21**, Gradle 9.5.1 wrapper, IntelliJ Platform
Gradle Plugin 2.16, IDE target `IU` 2024.2.

```bash
# Cross-compile the bundled schemalock binaries from ../app first
scripts/build-binaries.sh

# Build / test / verify / run a sandbox IDE
./gradlew buildPlugin     # → build/distributions/schemalock-<version>.zip
./gradlew test
./gradlew verifyPlugin
./gradlew runIde
```

`build-binaries.sh` expects the sibling [schemalock/schemalock](https://github.com/schemalock/schemalock)
checkout; `.app-version` pins which annotated app tag the bundled binaries
are built from.

## License

[Apache License 2.0](LICENSE).
