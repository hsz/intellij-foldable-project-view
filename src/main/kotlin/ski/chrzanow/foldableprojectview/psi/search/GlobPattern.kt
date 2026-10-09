package ski.chrzanow.foldableprojectview.psi.search

/**
 * Converts gitignore-like glob patterns into [Regex] matching paths relative to a root directory.
 *
 * Supported syntax:
 * - `*` matches any number of characters except `/`
 * - `?` matches a single character except `/`
 * - `**` matches any number of path segments
 * - `[abc]`, `[a-z]`, `[!abc]` character classes
 * - `\` escapes the following character
 * - pattern without a slash (or with a trailing slash only) matches on any level
 * - leading `/` anchors the pattern to the root directory
 */
object GlobPattern {

    private const val SEGMENT = "[^/]*"
    private const val ANY_SEGMENTS = "(?:[^/]*/)*"
    private const val REGEX_SPECIAL_CHARACTERS = ".()+|^$@%{}*?[]\\"

    fun compile(glob: String, caseSensitive: Boolean = true): Regex? {
        var pattern = glob.trim().removeSuffix("/")
        if (pattern.isEmpty()) {
            return null
        }

        val regex = StringBuilder("^")
        when {
            pattern.startsWith("/") -> pattern = pattern.drop(1)
            pattern.startsWith("**/") -> Unit
            '/' !in pattern -> regex.append(ANY_SEGMENTS)
        }

        var index = 0
        while (index < pattern.length) {
            val char = pattern[index]
            when {
                pattern.startsWith("**/", index) -> {
                    regex.append(ANY_SEGMENTS)
                    index += 3
                    continue
                }

                pattern.startsWith("**", index) -> {
                    regex.append(".*")
                    index += 2
                    continue
                }

                char == '*' -> regex.append(SEGMENT)
                char == '?' -> regex.append("[^/]")

                char == '[' -> {
                    val end = pattern.indexOf(']', index + 1)
                    when {
                        end > index + 1 -> {
                            val content = pattern.substring(index + 1, end).replaceFirst(Regex("^!"), "^")
                            regex.append('[').append(content.replace("\\", "\\\\")).append(']')
                            index = end + 1
                            continue
                        }

                        else -> regex.append("\\[")
                    }
                }

                char == '\\' && index + 1 < pattern.length -> {
                    regex.appendEscaped(pattern[index + 1])
                    index += 2
                    continue
                }

                else -> regex.appendEscaped(char)
            }
            index++
        }
        regex.append("/?$")

        val options = when (caseSensitive) {
            true -> emptySet()
            false -> setOf(RegexOption.IGNORE_CASE)
        }

        return runCatching { Regex(regex.toString(), options) }.getOrNull()
    }

    private fun StringBuilder.appendEscaped(char: Char) = apply {
        if (char in REGEX_SPECIAL_CHARACTERS) {
            append('\\')
        }
        append(char)
    }
}
