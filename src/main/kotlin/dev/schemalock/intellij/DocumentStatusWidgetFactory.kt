package dev.schemalock.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class DocumentStatusWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = DocumentStatusWidget.ID
    override fun getDisplayName(): String = "SchemaLock Schema Version"
    override fun isAvailable(project: Project): Boolean = true
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        val widget = DocumentStatusWidget(project)
        DocumentStateBus.getInstance(project).attach(widget)
        val conn = LspRequestHelper.connect(project, widget)
        Disposer.register(widget, conn)
        // selectionChanged won't fire for an already-open editor, so seed the
        // widget with whatever file is currently selected.
        LspRequestHelper.syncToSelection(project, widget)
        return widget
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }
}
