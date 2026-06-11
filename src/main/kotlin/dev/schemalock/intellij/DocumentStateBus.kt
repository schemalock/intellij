package dev.schemalock.intellij

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-level rendezvous between the LSP client (which receives
 * `schemalock/documentStateChanged` server-push notifications) and the status
 * bar widget. Holds the last-known [DocumentState] per document URI.
 *
 * The server resolves a document's schema version asynchronously (a CDN
 * round-trip for unpinned resources), then pushes the resolved state. Polling
 * `getDocumentState` on editor selection races that resolution, so the widget
 * is driven primarily by these pushed notifications; the poll only seeds
 * already-resolved (cache-hit) documents instantly.
 */
@Service(Service.Level.PROJECT)
class DocumentStateBus(private val project: Project) {

    private val states = ConcurrentHashMap<String, DocumentState>()

    @Volatile
    private var widget: DocumentStatusWidget? = null

    /** Registered by the widget factory; cleared on widget dispose. */
    fun attach(w: DocumentStatusWidget) { widget = w }
    fun detach(w: DocumentStatusWidget) {
        if (widget === w) widget = null
    }

    /** Last-known state for [uri], or null if never resolved this session. */
    fun get(uri: String): DocumentState? = states[uri]

    /**
     * Record a freshly resolved state (from a server notification or a poll)
     * and refresh the widget if the URI is the one currently displayed.
     */
    fun onState(uri: String, state: DocumentState) {
        states[uri] = state
        widget?.onState(uri, state)
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val vf = VirtualFileManager.getInstance().findFileByUrl(uri) ?: return@invokeLater
            val psi = PsiManager.getInstance(project).findFile(vf) ?: return@invokeLater
            DaemonCodeAnalyzer.getInstance(project).restart(psi)
        }
    }

    companion object {
        fun getInstance(project: Project): DocumentStateBus = project.service()
    }
}
