package dev.schemalock.intellij

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLKeyValue

internal val KUBERNETES_UNKNOWN_TOOL_IDS = setOf(
    "KubernetesUnknownResourcesInspection",
    "KubernetesUnknownKeys",
    "KubernetesUnknownValues"
)
internal const val SPELL_CHECK_TOOL_ID = "SpellCheckingInspection"

/**
 * Returns true when [toolId] should be suppressed given ownership and element position.
 * Pure function — no PSI/platform types — so it is directly unit-testable.
 */
internal fun shouldSuppress(toolId: String, owned: Boolean, isKey: Boolean): Boolean {
    if (!owned) return false
    if (toolId in KUBERNETES_UNKNOWN_TOOL_IDS) return true
    if (toolId == SPELL_CHECK_TOOL_ID && isKey) return true
    return false
}

/**
 * YAML-scoped [InspectionSuppressor] that mutes Kubernetes "Unknown*" inspections
 * and key-position spell-check on YAML files owned by SchemaLock (state != 0).
 * Files not resolved by SchemaLock are unaffected.
 */
class SchemalockInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        val url = element.containingFile?.virtualFile?.url ?: return false
        val project = element.project
        val state = DocumentStateBus.getInstance(project).get(url) ?: return false
        // state 0 = Unindexable; the poll path skips storing it, but guard here in
        // case a notification ever delivers one — only real resolutions are owned.
        val owned = state.state != 0
        val kv = PsiTreeUtil.getParentOfType(element, YAMLKeyValue::class.java, false)
        val isKey = kv != null && kv.key != null && PsiTreeUtil.isAncestor(kv.key!!, element, false)
        return shouldSuppress(toolId, owned, isKey)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY
}
