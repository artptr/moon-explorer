package io.github.artptr.moonexplorer.config

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Reads the PSI of a moon configuration file in one format and gives a [ConfigSyntax].
 * To support one more format, add a converter. Then register the converter in the descriptor file of the format. Refer to ADR 0005.
 */
interface ConfigConverter {
    /** Gives the structure of [file], or null if the converter cannot read the file. Call this function in a read action. */
    fun convert(file: PsiFile): ConfigSyntax?
}

/** The extension point `configConverter`. Each language has one converter. */
private val CONVERTERS = LanguageExtension<ConfigConverter>("io.github.artptr.moonexplorer.configConverter")

private val SYNTAX_KEY = Key.create<CachedValue<ConfigSyntax?>>("io.github.artptr.moonexplorer.configSyntax")

/**
 * Gives the structure of [file], or null if no converter can read the file. The IDE keeps the result until the file changes.
 * Call this function in a read action.
 */
fun configSyntax(file: PsiFile): ConfigSyntax? = CachedValuesManager.getCachedValue(file, SYNTAX_KEY) {
    CachedValueProvider.Result.create(CONVERTERS.forLanguage(file.language)?.convert(file), file)
}
