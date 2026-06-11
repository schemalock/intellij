package dev.schemalock.intellij

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification

/** Payload of the `schemalock/documentStateChanged` server-push notification. */
data class DocumentStateChangedParams(
    val uri: String = "",
    val state: Int = 0,
    val group: String? = null,
    val kind: String? = null,
    val version: String? = null,
    val source: String? = null,
    val errMsg: String? = null,
)

private fun DocumentStateChangedParams.toState() =
    DocumentState(state, group, kind, version, source, errMsg)

/**
 * Custom LSP client that handles SchemaLock's server-to-client notifications.
 * The server fires `schemalock/documentStateChanged` whenever a document's
 * resolved schema state changes (initial resolution, version override, intent
 * reload); we forward it to the [DocumentStateBus] which drives the status bar.
 */
class SchemalockLsp4jClient(
    handler: LspServerNotificationsHandler,
    private val project: Project,
) : Lsp4jClient(handler) {

    @JsonNotification("schemalock/documentStateChanged")
    fun documentStateChanged(params: DocumentStateChangedParams) {
        if (params.uri.isEmpty()) return
        DocumentStateBus.getInstance(project).onState(params.uri, params.toState())
    }
}
