package dev.schemalock.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.eclipse.lsp4j.services.LanguageServer

class SchemalockLspServerDescriptor(project: Project) :
    ProjectWideLspServerDescriptor(project, "SchemaLock") {

    override val lsp4jServerClass: Class<out LanguageServer> get() = SchemalockLspServer::class.java

    override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient =
        SchemalockLsp4jClient(handler, project)

    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "yaml"

    override fun createCommandLine(): GeneralCommandLine =
        GeneralCommandLine(BinaryResolver.resolve().absolutePath, "serve", "--stdio")
            .withWorkDirectory(project.basePath)
}
