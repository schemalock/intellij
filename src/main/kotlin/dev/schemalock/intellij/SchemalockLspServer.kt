package dev.schemalock.intellij

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

data class GetDocumentStateParams(val uri: String)
data class SetVersionOverrideParams(val uri: String, val version: String)
data class ListVersionsParams(val group: String)
data class ListVersionsResult(val versions: List<String> = emptyList())

interface SchemalockLspServer : LanguageServer {
    @JsonRequest("schemalock/getDocumentState")
    fun getDocumentState(params: GetDocumentStateParams): CompletableFuture<DocumentState>

    @JsonRequest("schemalock/setDocumentVersionOverride")
    fun setDocumentVersionOverride(params: SetVersionOverrideParams): CompletableFuture<Void?>

    @JsonRequest("schemalock/listVersionsForGroup")
    fun listVersionsForGroup(params: ListVersionsParams): CompletableFuture<ListVersionsResult>
}
