package dev.schemalock.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.util.messages.MessageBusConnection

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
            server.sendRequestSync { (it as SchemalockLspServer).getDocumentState(GetDocumentStateParams(uri)) }
        } catch (_: Throwable) {
            null
        }
    }

    fun sendVersionOverride(project: Project, uri: String, version: String) {
        val server = findServer(project) ?: return
        try {
            server.sendRequestSync { (it as SchemalockLspServer).setDocumentVersionOverride(SetVersionOverrideParams(uri, version)) }
        } catch (_: Throwable) {}
    }

    fun listVersionsForGroup(project: Project, group: String): List<String> {
        val server = findServer(project) ?: return emptyList()
        return try {
            server.sendRequestSync { (it as SchemalockLspServer).listVersionsForGroup(ListVersionsParams(group)) }?.versions
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findServer(project: Project): LspServer? =
        LspServerManager.getInstance(project)
            .getServersForProvider(SchemalockLspServerSupportProvider::class.java)
            .firstOrNull()
}
