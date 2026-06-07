package dev.schemalock.intellij

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BinaryResolverTest {

    @Test fun `mac arm64`() =
        assertEquals("darwin-arm64", BinaryResolver.platformDir("Mac OS X", "aarch64"))

    @Test fun `mac x64`() =
        assertEquals("darwin-x64", BinaryResolver.platformDir("Mac OS X", "x86_64"))

    @Test fun `linux arm64`() =
        assertEquals("linux-arm64", BinaryResolver.platformDir("Linux", "aarch64"))

    @Test fun `linux x64`() =
        assertEquals("linux-x64", BinaryResolver.platformDir("Linux", "amd64"))

    @Test fun `windows x64`() =
        assertEquals("win32-x64", BinaryResolver.platformDir("Windows 10", "x86_64"))

    @Test fun `unknown OS throws`() {
        assertThrows<IllegalStateException> {
            BinaryResolver.platformDir("SunOS", "sparc")
        }
    }
}
