package dev.schemalock.intellij

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SchemalockInspectionSuppressorTest {

    // --- not owned → always false ---

    @Test fun `not owned k8s unknown resources inspection not suppressed`() {
        assertFalse(shouldSuppress("KubernetesUnknownResourcesInspection", owned = false, isKey = false))
        assertFalse(shouldSuppress("KubernetesUnknownResourcesInspection", owned = false, isKey = true))
    }

    @Test fun `not owned k8s unknown keys inspection not suppressed`() {
        assertFalse(shouldSuppress("KubernetesUnknownKeys", owned = false, isKey = false))
        assertFalse(shouldSuppress("KubernetesUnknownKeys", owned = false, isKey = true))
    }

    @Test fun `not owned k8s unknown values inspection not suppressed`() {
        assertFalse(shouldSuppress("KubernetesUnknownValues", owned = false, isKey = false))
        assertFalse(shouldSuppress("KubernetesUnknownValues", owned = false, isKey = true))
    }

    @Test fun `not owned spell check not suppressed`() {
        assertFalse(shouldSuppress(SPELL_CHECK_TOOL_ID, owned = false, isKey = false))
        assertFalse(shouldSuppress(SPELL_CHECK_TOOL_ID, owned = false, isKey = true))
    }

    // --- owned + Kubernetes inspections → always suppressed ---

    @Test fun `owned k8s unknown resources inspection suppressed regardless of isKey`() {
        assertTrue(shouldSuppress("KubernetesUnknownResourcesInspection", owned = true, isKey = false))
        assertTrue(shouldSuppress("KubernetesUnknownResourcesInspection", owned = true, isKey = true))
    }

    @Test fun `owned k8s unknown keys inspection suppressed regardless of isKey`() {
        assertTrue(shouldSuppress("KubernetesUnknownKeys", owned = true, isKey = false))
        assertTrue(shouldSuppress("KubernetesUnknownKeys", owned = true, isKey = true))
    }

    @Test fun `owned k8s unknown values inspection suppressed regardless of isKey`() {
        assertTrue(shouldSuppress("KubernetesUnknownValues", owned = true, isKey = false))
        assertTrue(shouldSuppress("KubernetesUnknownValues", owned = true, isKey = true))
    }

    // --- owned + spell check → key-position only ---

    @Test fun `owned spell check on key is suppressed`() {
        assertTrue(shouldSuppress(SPELL_CHECK_TOOL_ID, owned = true, isKey = true))
    }

    @Test fun `owned spell check on value is not suppressed`() {
        assertFalse(shouldSuppress(SPELL_CHECK_TOOL_ID, owned = true, isKey = false))
    }

    // --- owned + unrelated inspection → not suppressed ---

    @Test fun `owned unrelated inspection not suppressed`() {
        assertFalse(shouldSuppress("SomeOtherInspection", owned = true, isKey = false))
        assertFalse(shouldSuppress("SomeOtherInspection", owned = true, isKey = true))
    }
}
