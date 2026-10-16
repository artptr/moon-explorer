package io.github.artptr.moonexplorer

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.MoonExplorerBundle"

object MoonExplorerBundle : DynamicBundle(MoonExplorerBundle::class.java, BUNDLE) {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getMessage(key, *params)
}
