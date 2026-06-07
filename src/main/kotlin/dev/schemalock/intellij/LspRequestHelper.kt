package dev.schemalock.intellij

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.util.messages.MessageBusConnection
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints

private val gson = Gson()

object LspRequestHelper {

    fun connect(project: Project, widget: DocumentStatusWidget): MessageBusConnection {
        val conn = project.messageBus.connect()
        conn.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val file = event.newFile
                if (file == null || file.extension != "yaml") {
                    widget.update(null, null)
                    return
                }
                refreshState(project, file, widget)
            }
        })
        return conn
    }

    fun refreshState(project: Project, file: VirtualFile, widget: DocumentStatusWidget) {
        val uri = file.url
        ApplicationManager.getApplication().executeOnPooledThread {
            val state = getDocumentState(project, uri)
            ApplicationManager.getApplication().invokeLater {
                widget.update(uri, state)
            }
        }
    }

    private fun getDocumentState(project: Project, uri: String): DocumentState? {
        val server = findServer(project) ?: return null
        return try {
            val result = sendCustomRequest(server, "schemalock/getDocumentState", mapOf("uri" to uri))
            gson.fromJson(result as? JsonObject ?: return null, DocumentState::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun sendVersionOverride(project: Project, uri: String, version: String) {
        val server = findServer(project) ?: return
        try {
            sendCustomRequest(server, "schemalock/setDocumentVersionOverride", mapOf("uri" to uri, "version" to version))
        } catch (_: Exception) {}
    }

    fun listVersionsForGroup(project: Project, group: String): List<String> {
        val server = findServer(project) ?: return emptyList()
        return try {
            val result = sendCustomRequest(server, "schemalock/listVersionsForGroup", mapOf("group" to group))
            val obj = result as? JsonObject ?: return emptyList()
            val arr = obj.getAsJsonArray("versions") ?: return emptyList()
            arr.map { it.asString }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findServer(project: Project): LspServer? =
        LspServerManager.getInstance(project)
            .getServersForProvider(SchemalockLspServerSupportProvider::class.java)
            .firstOrNull()

    private fun sendCustomRequest(server: LspServer, method: String, params: Any): Any? {
        val endpoint = ServiceEndpoints.toEndpoint(server.lsp4jServer)
        return endpoint.request(method, params).get()
    }
}
