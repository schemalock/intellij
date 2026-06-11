package dev.schemalock.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
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
            override fun selectionChanged(event: FileEditorManagerEvent) = syncToSelection(project, widget)
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) = syncToSelection(project, widget)
        })
        return conn
    }

    /**
     * Re-point the widget at the editor's currently selected file, regardless of
     * which event fired. `selectionChanged` alone is unreliable for file
     * open/reopen, so [connect] also listens to `fileOpened` and both funnel
     * here. Renders the bus's cached state immediately; a poll + the
     * `schemalock/documentStateChanged` notification fill in pending resolutions.
     */
    fun syncToSelection(project: Project, widget: DocumentStatusWidget) {
        val selected = FileEditorManager.getInstance(project).selectedEditor?.file
        if (selected == null || selected.extension != "yaml") {
            widget.setCurrentFile(null)
            return
        }
        widget.setCurrentFile(selected.url)
        refreshState(project, selected.url)
    }

    /**
     * Poll the server for [uri]'s current state and publish it to the
     * [DocumentStateBus]. Seeds already-resolved (cache-hit) documents
     * instantly; pending resolutions are delivered separately via the
     * `schemalock/documentStateChanged` notification. Safe to call from the EDT
     * (the request runs on a pooled thread).
     */
    fun refreshState(project: Project, uri: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val state = getDocumentState(project, uri)
            // Ignore Unindexable (state 0): a poll can race document sync and hit
            // the server before it has the document text, returning a spurious
            // Unindexable that must not clobber a previously resolved state. The
            // server never *notifies* Unindexable either — real states arrive via
            // schemalock/documentStateChanged.
            if (state != null && state.state != 0) {
                DocumentStateBus.getInstance(project).onState(uri, state)
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
