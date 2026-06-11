package dev.schemalock.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.TextPresentation
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference

data class PickerItem(val label: String, val description: String? = null, val isShowAll: Boolean = false) {
    override fun toString() = buildString {
        append(label)
        if (description != null) append("  $description")
    }
}

class DocumentStatusWidget(private val project: Project) : StatusBarWidget, TextPresentation {

    companion object {
        const val ID = "SchemalockDocumentStatus"
    }

    @Volatile
    private var currentUri: String? = null
    private val currentState = AtomicReference<DocumentState?>(null)
    private var statusBar: StatusBar? = null

    override fun ID(): String = ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String = currentState.get()?.widgetText() ?: ""
    override fun getTooltipText(): String? = currentState.get()?.widgetTooltip()
    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { _ ->
        val uri = currentUri ?: return@Consumer
        val state = currentState.get() ?: return@Consumer
        handleClick(uri, state)
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        DocumentStateBus.getInstance(project).detach(this)
        statusBar = null
    }

    /**
     * Switch the displayed document (called on editor selection). Renders the
     * last-known state from the bus immediately; a still-pending resolution
     * arrives later via [onState]. Must be called on the EDT — every caller is
     * a platform editor listener, so the `updateWidget` call needs no
     * `invokeLater` (unlike [onState], which runs off a pooled request thread).
     */
    fun setCurrentFile(uri: String?) {
        currentUri = uri
        val cached = uri?.let { DocumentStateBus.getInstance(project).get(it) }
        currentState.set(cached)
        statusBar?.updateWidget(ID)
    }

    /** A freshly resolved state arrived (server notification or poll). */
    fun onState(uri: String, state: DocumentState) {
        if (uri != currentUri) return
        currentState.set(state)
        ApplicationManager.getApplication().invokeLater { statusBar?.updateWidget(ID) }
    }

    private fun handleClick(uri: String, state: DocumentState) {
        val group = state.group ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val versions = LspRequestHelper.listVersionsForGroup(project, group)
            if (versions.isEmpty()) return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater {
                showVersionPicker(uri, state, versions, recentOnly = true)
            }
        }
    }

    private fun showVersionPicker(uri: String, state: DocumentState, versions: List<String>, recentOnly: Boolean) {
        val items = if (recentOnly) buildPickerItems(versions, state.version)
                    else versions.map { PickerItem(label = it, description = buildDescription(it, state.version, versions.first() == it)) }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle("Select schema version for ${state.group}")
            .setItemChosenCallback { chosen ->
                if (chosen.isShowAll) {
                    showVersionPicker(uri, state, versions, recentOnly = false)
                    return@setItemChosenCallback
                }
                val newVersion = if (chosen.label == state.version) "" else chosen.label
                ApplicationManager.getApplication().executeOnPooledThread {
                    LspRequestHelper.sendVersionOverride(project, uri, newVersion)
                    LspRequestHelper.refreshState(project, uri)
                }
            }
            .createPopup()
            .showInFocusCenter()
    }

    private fun buildPickerItems(versions: List<String>, current: String?): List<PickerItem> {
        val recent = versions.take(5).mapIndexed { i, v ->
            PickerItem(label = v, description = buildDescription(v, current, i == 0))
        }
        return if (versions.size > 5)
            recent + PickerItem(label = "Show all ${versions.size} versions…", isShowAll = true)
        else recent
    }

    private fun buildDescription(version: String, current: String?, isFirst: Boolean): String? {
        val parts = buildList {
            if (isFirst) add("latest")
            if (version == current) add("current")
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }
}
