package ski.chrzanow.foldableprojectview.settings

import com.intellij.ui.JBColor
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import java.awt.Color
import java.util.function.Supplier

/**
 * A named, theme-aware color from the [RuleColors] palette. Unlike a custom color picked by the user, which is the same
 * in every theme, it is resolved when painted, so it follows the light/dark theme switches. It is stored by its [id].
 */
class RuleColor private constructor(val id: String, private val delegate: Color) : JBColor(Supplier { delegate }) {

    /**
     * Localized name shown in the settings.
     */
    val displayName: String
        get() = when (id) {
            "Blue" -> message("foldableProjectView.settings.color.blue")
            "Green" -> message("foldableProjectView.settings.color.green")
            "Orange" -> message("foldableProjectView.settings.color.orange")
            "Rose" -> message("foldableProjectView.settings.color.rose")
            "Violet" -> message("foldableProjectView.settings.color.violet")
            "Yellow" -> message("foldableProjectView.settings.color.yellow")
            "Gray" -> message("foldableProjectView.settings.color.gray")
            else -> id
        }

    override fun equals(other: Any?) = other is RuleColor && other.id == id && other.delegate == delegate

    override fun hashCode() = id.hashCode()

    override fun toString() = "RuleColor($id)"

    companion object {

        /**
         * Background reusing the platform File Color of the same [id] (`Settings | Appearance & Behavior | File Colors`),
         * including its theme-specific overrides, so the groups look like the scope-colored files around them.
         */
        internal fun background(id: String, light: Int, dark: Int) = RuleColor(id, namedColor("FileColor.$id", JBColor(light, dark)))

        /**
         * Text color matching the hue of the background of the same [id] in the light and dark themes.
         */
        internal fun foreground(id: String, light: Int, dark: Int) = RuleColor(id, JBColor(light, dark))
    }
}

/**
 * Theme-aware colors offered for the folding groups in the rule editor, see [Rule.background] and [Rule.foreground].
 * The user may still pick any custom color, which is then used as is in both the light and dark themes.
 */
object RuleColors {

    val backgrounds = listOf(
        RuleColor.background("Blue", 0xeaf6ff, 0x4f556b),
        RuleColor.background("Green", 0xeffae7, 0x49544a),
        RuleColor.background("Orange", 0xf6e9dc, 0x806052),
        RuleColor.background("Rose", 0xf2dcda, 0x6e535b),
        RuleColor.background("Violet", 0xe6e0f1, 0x534a57),
        RuleColor.background("Yellow", 0xffffe4, 0x4f4b41),
        RuleColor.background("Gray", 0xf5f5f5, 0x45484a),
    )

    val foregrounds = listOf(
        RuleColor.foreground("Blue", 0x3574f0, 0x548af7),
        RuleColor.foreground("Green", 0x208a3c, 0x5fad65),
        RuleColor.foreground("Orange", 0xe56d17, 0xe08855),
        RuleColor.foreground("Rose", 0xe55765, 0xe35252),
        RuleColor.foreground("Violet", 0x834df0, 0x955ae0),
        RuleColor.foreground("Yellow", 0xa46704, 0xd6ae58),
        RuleColor.foreground("Gray", 0x818594, 0x868a91),
    )

    fun background(id: String) = backgrounds.firstOrNull { it.id == id }

    fun foreground(id: String) = foregrounds.firstOrNull { it.id == id }
}

/**
 * Whether the color was picked by the user rather than taken from the [RuleColors] palette.
 */
val Color?.isCustom
    get() = this != null && this !is RuleColor
