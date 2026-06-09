package dev.schemalock.intellij

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentStateTest {

    @Test fun `pinned shows lock icon with kind and version`() {
        val s = DocumentState(state = 1, kind = "VMCluster", version = "0.48.4", group = "operator.victoriametrics.com")
        assertEquals("🔒 VMCluster · 0.48.4", s.widgetText())
        assertEquals("SchemaLock: pinned operator.victoriametrics.com@0.48.4 (from schemalock.yaml)", s.widgetTooltip())
    }

    @Test fun `unpinned shows unlock icon`() {
        val s = DocumentState(state = 2, kind = "Ingress", version = "1.0.0", group = "networking.k8s.io")
        assertEquals("🔓 Ingress · 1.0.0", s.widgetText())
        assertTrue(s.widgetTooltip()!!.contains("latest from CDN"))
    }

    @Test fun `preview shows eye icon`() {
        val s = DocumentState(state = 3, kind = "VMCluster", version = "0.52.0", group = "operator.victoriametrics.com")
        assertEquals("👁 VMCluster · 0.52.0", s.widgetText())
        assertTrue(s.widgetTooltip()!!.contains("session only"))
    }

    @Test fun `error shows warning with errMsg`() {
        val s = DocumentState(state = 4, kind = "VMCluster", group = "operator.victoriametrics.com", errMsg = "timeout")
        assertEquals("⚠ VMCluster", s.widgetText())
        assertTrue(s.widgetTooltip()!!.contains("timeout"))
    }

    @Test fun `unindexable returns null`() {
        val s = DocumentState(state = 0)
        assertNull(s.widgetText())
        assertNull(s.widgetTooltip())
    }

    @Test fun `error falls back to group when kind is null`() {
        val s = DocumentState(state = 4, group = "cert-manager.io", errMsg = "cdn unreachable")
        assertEquals("⚠ cert-manager.io", s.widgetText())
    }
}
