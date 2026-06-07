package dev.schemalock.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class DocumentStatusWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = DocumentStatusWidget.ID
    override fun getDisplayName(): String = "SchemaLock Schema Version"
    override fun isAvailable(project: Project): Boolean =
        SchemalockLspServerSupportProvider.hasLockfile(project)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        val widget = DocumentStatusWidget(project)
        val conn = LspRequestHelper.connect(project, widget)
        Disposer.register(widget, conn)
        return widget
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }
}
