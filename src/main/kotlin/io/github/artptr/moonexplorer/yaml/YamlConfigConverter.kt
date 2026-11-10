package io.github.artptr.moonexplorer.yaml

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.artptr.moonexplorer.config.*
import org.jetbrains.yaml.psi.*

/** Gives the [ConfigSyntax] of a YAML configuration file. The converter reads only the first YAML document of the file. */
class YamlConfigConverter : ConfigConverter {

    override fun convert(file: PsiFile): ConfigSyntax? {
        val yaml = file as? YAMLFile ?: return null
        val root = yaml.documents.firstOrNull()?.topLevelValue as? YAMLMapping
        val tasks = root?.getKeyValueByKey(TASKS_KEY)
        val taskKeys = (tasks?.value as? YAMLMapping)?.keyValues.orEmpty().mapNotNull { keyValue ->
            keyLeaf(keyValue)?.let { ConfigKey(keyValue.keyText, it) }
        }
        return ConfigSyntax(firstLeaf(file), tasks?.let(::keyLeaf), taskKeys, inheritedTasks(root))
    }

    override fun inheritedTasksEntryRemoval(entry: InheritedTasksEntry): ConfigTextChange? {
        val item = PsiTreeUtil.getParentOfType(entry.element, YAMLSequenceItem::class.java, YAMLKeyValue::class.java) ?: return null
        val collection = item.parent as? YAMLCompoundValue ?: return null
        val (others, brackets) = when (collection) {
            is YAMLSequence -> collection.items.filter { it != item } to "[]"
            is YAMLMapping -> collection.keyValues.filter { it != item } to "{}"
            else -> return null
        }
        // The text of a flow item does not contain the comma. Thus the function makes the list or map again from the other items.
        val flowText = brackets[0] + others.joinToString(", ") { it.text } + brackets[1]
        if (isFlow(collection)) return ConfigTextChange(collection.textRange, flowText)
        if (others.isNotEmpty()) return ConfigTextChange(blockItemRange(item), "")
        // A block list or map without items becomes an empty flow list or map on the line of the key.
        val colon = (collection.parent as? YAMLKeyValue)?.key?.let(PsiTreeUtil::nextLeaf)?.takeIf { it.text == ":" } ?: return null
        return ConfigTextChange(TextRange(colon.textRange.endOffset, collection.textRange.endOffset), " $flowText")
    }

    /** Gives true if [collection] has the flow style, for example `[a, b]` or `{a: b}`. */
    private fun isFlow(collection: PsiElement): Boolean = collection.firstChild?.text.let { it == "[" || it == "{" }

    /**
     * Gives the text of [item] of a block list or map and the line of the item. If the item is the first item, the range also contains
     * the white space after the item. Otherwise it contains the white space before the item.
     */
    private fun blockItemRange(item: PsiElement): TextRange {
        val previous = generateSequence(item.prevSibling) { it.prevSibling }.firstOrNull { it.text.isNotBlank() }
        if (previous != null) return TextRange(previous.textRange.endOffset, item.textRange.endOffset)
        val next = generateSequence(item.nextSibling) { it.nextSibling }.firstOrNull { it.text.isNotBlank() }
        return TextRange(item.textRange.startOffset, next?.textRange?.startOffset ?: item.textRange.endOffset)
    }

    private fun inheritedTasks(root: YAMLMapping?): List<InheritedTasksEntry> {
        val workspace = root?.getKeyValueByKey("workspace")?.value as? YAMLMapping
        val settings = workspace?.getKeyValueByKey("inheritedTasks")?.value as? YAMLMapping ?: return emptyList()
        val include = items(settings, "include").map { (id, element) -> InheritedTasksEntry.Include(id, element) }
        val exclude = items(settings, "exclude").map { (id, element) -> InheritedTasksEntry.Exclude(id, element) }
        val rename = (settings.getKeyValueByKey("rename")?.value as? YAMLMapping)?.keyValues.orEmpty().mapNotNull { keyValue ->
            val localId = (keyValue.value as? YAMLScalar)?.textValue ?: return@mapNotNull null
            keyLeaf(keyValue)?.let { InheritedTasksEntry.Rename(keyValue.keyText, localId, it) }
        }
        return include + exclude + rename
    }

    /** Gives the text and the first leaf of each scalar in the list [key] of [settings]. */
    private fun items(settings: YAMLMapping, key: String): List<Pair<String, PsiElement>> =
        (settings.getKeyValueByKey(key)?.value as? YAMLSequence)?.items.orEmpty().mapNotNull { item ->
            (item.value as? YAMLScalar)?.let { it.textValue to PsiTreeUtil.getDeepestFirst(it) }
        }

    private fun keyLeaf(keyValue: YAMLKeyValue): PsiElement? = keyValue.key?.let(PsiTreeUtil::getDeepestFirst)

    private fun firstLeaf(file: PsiFile): PsiElement? {
        var leaf: PsiElement? = PsiTreeUtil.getDeepestFirst(file)
        // The YAML lexer does not always give a line end as PsiWhiteSpace. Thus the function examines the text.
        while (leaf != null && leaf.text.isBlank()) leaf = PsiTreeUtil.nextLeaf(leaf)
        return leaf
    }
}
