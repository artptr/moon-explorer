package io.github.artptr.moonexplorer.yaml

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.artptr.moonexplorer.config.ConfigConverter
import io.github.artptr.moonexplorer.config.ConfigKey
import io.github.artptr.moonexplorer.config.ConfigSyntax
import io.github.artptr.moonexplorer.config.InheritedTasksEntry
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

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
