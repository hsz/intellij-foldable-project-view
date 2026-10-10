package ski.chrzanow.foldableprojectview.settings

import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message

/**
 * Ready-made folding rules offered by the `+` button of the rules table, so a new project can be decluttered
 * without writing the patterns by hand. The rules are created anew on each call, as [Rule]s are mutable.
 */
object RulePresets {

    fun create() = listOf(
        Rule(message("foldableProjectView.settings.presets.docs"), "*.md *.rst *.txt LICENSE* NOTICE* docs/"),
        Rule(message("foldableProjectView.settings.presets.ci"), ".github/ .gitlab-ci.yml .travis.yml .circleci/ Jenkinsfile azure-pipelines.yml"),
        Rule(message("foldableProjectView.settings.presets.build"), "gradle/ gradlew* *.gradle *.gradle.kts gradle.properties pom.xml Makefile *.lock package-lock.json"),
        Rule(message("foldableProjectView.settings.presets.config"), "*.yml *.yaml *.toml *.properties .editorconfig .env*"),
        Rule(message("foldableProjectView.settings.presets.dotfiles"), ".*"),
    )
}
