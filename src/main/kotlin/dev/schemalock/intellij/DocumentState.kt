package dev.schemalock.intellij

/**
 * Mirrors the DocumentState shape sent by the schemalock binary over LSP.
 * state values: 0=Unindexable, 1=Pinned, 2=Unpinned, 3=Preview, 4=Error
 */
data class DocumentState(
    val state: Int = 0,
    val group: String? = null,
    val kind: String? = null,
    val version: String? = null,
    val source: String? = null,
    val errMsg: String? = null
)

fun DocumentState.widgetText(): String? = when (state) {
    1 -> "🔒 ${kind ?: ""} · ${version ?: ""}"
    2 -> "🔓 ${kind ?: ""} · ${version ?: ""}"
    3 -> "👁 ${kind ?: ""} · ${version ?: ""}"
    4 -> "⚠ ${kind ?: group ?: "schemalock"}"
    else -> null
}

fun DocumentState.widgetTooltip(): String? = when (state) {
    1 -> "SchemaLock: pinned ${group}@${version} (from schemalock.yaml)"
    2 -> "SchemaLock: unpinned ${group}@${version} (latest from CDN). Click to pin a version."
    3 -> "SchemaLock: preview ${group}@${version} (this session only). Click to change."
    4 -> "SchemaLock: CDN unreachable — ${errMsg ?: "unknown error"}. Click to retry."
    else -> null
}
