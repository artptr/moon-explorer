package io.github.artptr.moonexplorer

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.NewUI
import javax.swing.Icon

/**
 * The icons of the plugin. The IDE uses the file with the suffix `_dark` for a dark theme,
 * and the file with the suffix `@20x20` where it shows a 20 × 20 icon, for example the tool window bar of the new UI.
 */
object MoonIcons {
    /**
     * The moon icon in the moon brand color. It marks moon tasks and the moon run configurations.
     * The new UI shows an outline with a light fill, the same as the node icons of the new UI. The classic UI shows a solid icon.
     */
    @JvmField
    val Moon: Icon = IconLoader.getIcon(if (NewUI.isEnabled()) "/icons/expui/moon.svg" else "/icons/moon.svg", MoonIcons::class.java)

    /** The outline of the moon icon for the button of the **Moon** tool window. It has the gray color of the other tool window icons. */
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/toolWindowMoon.svg", MoonIcons::class.java)
}
