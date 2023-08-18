package ski.chrzanow.foldableprojectview.settings

import com.intellij.ui.JBColor
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DEFAULT_RULE_NAME
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.DEFAULT_RULE_PATTERN
import java.awt.Color

interface FoldableProjectState {

    val foldingEnabled: Boolean
    val matchDirectories: Boolean
    val foldIgnoredFiles: Boolean
    val hideEmptyGroups: Boolean
    val hideAllGroups: Boolean
    val caseSensitive: Boolean
    val rules: MutableList<Rule>
}

data class Rule(
    var name: String = DEFAULT_RULE_NAME,

    var pattern: String = DEFAULT_RULE_PATTERN,

    @get:OptionTag(converter = ColorConverter::class)
    var background: Color? = null,

    @get:OptionTag(converter = ColorConverter::class)
    var foreground: Color? = null,
)

private class ColorConverter : Converter<Color>() {

    override fun toString(value: Color) = value.rgb.toString()

    override fun fromString(value: String) = runCatching { JBColor.decode(value) }.getOrNull()
}
