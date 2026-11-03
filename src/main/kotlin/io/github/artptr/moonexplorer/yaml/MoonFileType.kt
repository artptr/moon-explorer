package io.github.artptr.moonexplorer.yaml

import com.intellij.openapi.fileTypes.LanguageFileType
import io.github.artptr.moonexplorer.MoonExplorerBundle
import io.github.artptr.moonexplorer.MoonIcons
import org.jetbrains.yaml.YAMLLanguage
import javax.swing.Icon

/**
 * The file type of `moon.yml` and `moon.yaml`. JetBrains Marketplace finds these file names. Then the IDE can suggest the plugin.
 * The file type is secondary. Thus `YAMLFileType.YML` is the primary file type of the YAML language.
 * Do not use this file type to identify a moon configuration file. It does not contain the files in `.moon/`.
 * Refer to docs/adr/0005-yaml-file-type-and-task-hints.md.
 */
object MoonFileType : LanguageFileType(YAMLLanguage.INSTANCE, true) {
    /** The IDE keeps this name in the settings. Do not change it. */
    override fun getName(): String = "Moon"

    override fun getDescription(): String = MoonExplorerBundle.message("filetype.moon.description")

    override fun getDefaultExtension(): String = "yml"

    override fun getIcon(): Icon = MoonIcons.Moon
}
