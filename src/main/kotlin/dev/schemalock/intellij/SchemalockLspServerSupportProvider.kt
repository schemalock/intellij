package dev.schemalock.intellij

import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerSupportProvider.LspServerStarter
import java.nio.file.Path

class SchemalockLspServerSupportProvider : LspServerSupportProvider, PluginAware {

    // The platform calls setPluginDescriptor on extension beans implementing
    // PluginAware before any fileOpened event, giving us the plugin's install path
    // without the (now-internal) PluginManager lookup APIs.
    private lateinit var pluginPath: Path

    override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
        pluginPath = pluginDescriptor.pluginPath
    }

    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerStarter) {
        if (file.extension != "yaml") return
        serverStarter.ensureServerStarted(SchemalockLspServerDescriptor(project, pluginPath))
    }
}
