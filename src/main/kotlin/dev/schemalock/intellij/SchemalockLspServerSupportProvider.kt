package dev.schemalock.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerSupportProvider.LspServerStarter

class SchemalockLspServerSupportProvider : LspServerSupportProvider {

    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerStarter) {
        if (file.extension != "yaml") return
        if (!hasLockfile(project)) return
        serverStarter.ensureServerStarted(SchemalockLspServerDescriptor(project))
    }

    companion object {
        fun hasLockfile(project: Project): Boolean {
            val basePath = project.basePath ?: return false
            val base = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(basePath) ?: return false
            return base.findChild("schemalock.lock") != null
                || base.findChild("schemalock.yaml") != null
        }
    }
}
