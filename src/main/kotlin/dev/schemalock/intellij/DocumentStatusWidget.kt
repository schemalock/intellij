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

    private data class Current(val uri: String, val state: DocumentState)

    private val current = AtomicReference<Current?>(null)
    private var statusBar: StatusBar? = null

    override fun ID(): String = ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String = current.get()?.state?.widgetText() ?: ""
    override fun getTooltipText(): String? = current.get()?.state?.widgetTooltip()
    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { _ ->
        val c = current.get() ?: return@Consumer
        handleClick(c.uri, c.state)
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    fun update(uri: String?, state: DocumentState?) {
        current.set(if (uri != null && state != null) Current(uri, state) else null)
        statusBar?.updateWidget(ID)
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
