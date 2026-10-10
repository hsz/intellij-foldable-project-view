package ski.chrzanow.foldableprojectview.psi.search

/**
 * Gitignore-like glob pattern matching paths relative to a root directory.
 *
 * Supported syntax:
 * - `*` matches any number of characters except `/`
 * - `?` matches a single character except `/`
 * - `**` matches any number of path segments
 * - `[abc]`, `[a-z]`, `[!abc]` character classes
 * - `\` escapes the following character
 * - pattern without a slash (or with a trailing slash only) matches on any level
 * - leading `/` anchors the pattern to the root directory
 * - trailing `/` makes the pattern match directories only
 * - leading `!` negates the pattern, see [GlobPatterns]
 *
 * @see GlobPatterns for matching a list of patterns
 */
class GlobPattern private constructor(
    /** The pattern as written, including the `!` and `/` modifiers. */
    val source: String,
    /** Whether the pattern excludes the matched paths instead of including them. */
    val negated: Boolean,
    /** Whether the pattern matches directories only. */
    val directoryOnly: Boolean,
    private val regex: Regex,
) {

    fun matches(path: String, isDirectory: Boolean = false) = (isDirectory || !directoryOnly) && regex.matches(path)

    override fun toString() = source

    companion object {

        private const val SEGMENT = "[^/]*"
        private const val ANY_SEGMENTS = "(?:[^/]*/)*"
        private const val REGEX_SPECIAL_CHARACTERS = ".()+|^$@%{}*?[]\\"

        /**
         * Compiles the [glob], or returns `null` if it is blank or invalid (e.g., contains a malformed character class).
         */
        fun compile(glob: String, caseSensitive: Boolean = true): GlobPattern? {
            val source = glob.trimUnescaped()
            if (source.isEmpty()) {
                return null
            }

            var pattern = source
            val negated = pattern.startsWith("!")
            if (negated) {
                pattern = pattern.drop(1)
            }
            val directoryOnly = pattern.endsWith("/") && !pattern.endsWith("\\/")
            if (directoryOnly) {
                pattern = pattern.dropLast(1)
            }
            if (pattern.isEmpty()) {
                return null
            }

            val regex = toRegex(pattern, caseSensitive) ?: return null
            return GlobPattern(source, negated, directoryOnly, regex)
        }

        fun isValid(glob: String) = compile(glob) != null

        private fun toRegex(glob: String, caseSensitive: Boolean): Regex? {
            var pattern = glob
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

        /**
         * Trims the whitespace, except the trailing whitespace escaped with a backslash (`foo\ `).
         */
        private fun String.trimUnescaped(): String {
            var result = trimStart()
            while (result.isNotEmpty() && result.last().isWhitespace() && !result.endsWith("\\" + result.last())) {
                result = result.dropLast(1)
            }
            return result
        }

        private fun StringBuilder.appendEscaped(char: Char) = apply {
            if (char in REGEX_SPECIAL_CHARACTERS) {
                append('\\')
            }
            append(char)
        }
    }
}

/**
 * Ordered list of [GlobPattern]s parsed from a single text, like the `pattern` of a folding rule.
 *
 * Patterns are separated with whitespace or commas. A pattern containing spaces is wrapped in double quotes
 * (`"My Notes.md"`) or has the spaces escaped with a backslash (`My\ Notes.md`).
 *
 * As in gitignore, the last matching pattern decides: a path matched by a negated pattern (`!docker-compose.yml`)
 * is excluded unless it is matched again by a later pattern. Invalid patterns are skipped, see [invalidPatterns].
 */
class GlobPatterns private constructor(val patterns: List<GlobPattern>) {

    val isEmpty get() = patterns.isEmpty()

    fun matches(path: String, isDirectory: Boolean = false) =
        patterns.lastOrNull { it.matches(path, isDirectory) }?.negated == false

    companion object {

        private const val SEPARATORS = " \t\n\r,"

        fun parse(text: String, caseSensitive: Boolean = true) = GlobPatterns(
            split(text).mapNotNull { GlobPattern.compile(it, caseSensitive) }
        )

        /**
         * Returns the patterns of the [text] that cannot be compiled, see [GlobPattern.compile].
         */
        fun invalidPatterns(text: String) = split(text).filterNot(GlobPattern::isValid)

        /**
         * Splits the [text] into single patterns, honoring the double quotes and the backslash escapes.
         * The escapes are preserved, the quotes are removed.
         */
        fun split(text: String): List<String> {
            val patterns = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false

            fun flush() {
                if (current.isNotEmpty()) {
                    patterns += current.toString()
                    current.clear()
                }
            }

            var index = 0
            while (index < text.length) {
                val char = text[index]
                when {
                    char == '\\' && index + 1 < text.length -> {
                        current.append(char).append(text[index + 1])
                        index++
                    }

                    char == '"' -> quoted = !quoted
                    !quoted && char in SEPARATORS -> flush()
                    else -> current.append(char)
                }
                index++
            }
            flush()

            return patterns
        }
    }
}
