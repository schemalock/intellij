package dev.schemalock.intellij

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.util.SystemInfo
import java.io.File

object BinaryResolver {

    fun resolve(): File {
        // PluginManager.getPluginByClass is the public, non-internal way to obtain
        // our own plugin descriptor: it resolves the plugin from this class's
        // PluginAwareClassLoader, so no hardcoded plugin id is needed.
        // (PluginManagerCore.getPlugin is @ApiStatus.Internal — Marketplace rejects it.)
        val plugin = PluginManager.getPluginByClass(BinaryResolver::class.java)
            ?: error("SchemaLock plugin not found in plugin manager")
        val dir = platformDir(System.getProperty("os.name"), System.getProperty("os.arch"))
        val name = if (SystemInfo.isWindows) "schemalock.exe" else "schemalock"
        val binary = plugin.pluginPath.resolve("bin/$dir/$name").toFile()
        if (!SystemInfo.isWindows) {
            binary.setExecutable(true, false)
        }
        check(binary.canExecute()) {
            "SchemaLock binary not found or not executable: ${binary.absolutePath}. Reinstall the plugin."
        }
        return binary
    }

    internal fun platformDir(osName: String, osArch: String): String {
        val isArm = osArch.contains("aarch64") || osArch.contains("arm64")
        return when {
            osName.contains("mac", ignoreCase = true) ||
            osName.contains("darwin", ignoreCase = true) ->
                if (isArm) "darwin-arm64" else "darwin-x64"
            osName.contains("linux", ignoreCase = true) ->
                if (isArm) "linux-arm64" else "linux-x64"
            osName.contains("windows", ignoreCase = true) -> "win32-x64"
            else -> error("Unsupported OS: $osName")
        }
    }
}
