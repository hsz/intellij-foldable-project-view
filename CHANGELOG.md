<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Foldable Project View Changelog

## [Unreleased]

### Added

- `Fold in nested directories` option to apply the folding rules to the content of all directories, not only to the module roots ([#6](https://github.com/hsz/intellij-foldable-project-view/issues/6), [#30](https://github.com/hsz/intellij-foldable-project-view/issues/30))
- Per-rule `Action` to choose whether the matched elements are grouped or hidden from the Project View
- Per-rule `Enabled` checkbox to switch a rule off without removing it
- The `Ignored files` group is now a built-in rule listed in the rules table: it can be renamed, colored, reordered to change its precedence, hidden, or disabled, but not removed
- `Use as Defaults for New Projects` and `Copy from Defaults` buttons in the settings to store the current settings as the defaults for new projects, or to apply the defaults to the current project ([#12](https://github.com/hsz/intellij-foldable-project-view/issues/12), [#68](https://github.com/hsz/intellij-foldable-project-view/issues/68))
- The settings page is available in the settings for new projects (`New Projects Setup`), where it edits the defaults directly
- Negated patterns (`*.yml !docker-compose.yml`) to exclude elements from a rule; as in gitignore, the last matching pattern decides
- Patterns ending with a slash (`build/`) match directories only
- Patterns with spaces can be wrapped in double quotes (`"My Notes.md"`) or escaped with a backslash (`My\ Notes.md`)
- Invalid patterns are reported with a warning in the rule editor and in the rules table instead of being silently ignored
- Rules can read their patterns from ignore-like files kept in the repository (`.foldignore`, `.dockerignore`, etc.), one gitignore-like pattern per line; the Project View is refreshed when the files change, and missing files are reported in the rule editor ([#4](https://github.com/hsz/intellij-foldable-project-view/issues/4), [#29](https://github.com/hsz/intellij-foldable-project-view/issues/29))
- Rule presets (Documentation, CI/CD, Build, Configuration, Dotfiles) offered by the `+` button of the rules table
- The rule editor shows how many elements of the project the edited rule matches with the current options
- Rules with a blank name or no patterns are reported with a warning in the rule editor and in the rules table; the fields show placeholders with examples
- Theme-aware palette of named group colors (Blue, Green, Orange, Rose, Violet, Yellow, Gray) in the rule editor; the backgrounds reuse the platform File Colors, so they follow the light/dark theme and its overrides, while `Custom…` still opens the color picker

### Changed

- The global `Hide all groups` option was replaced with the per-rule `Hide` action; existing settings are migrated automatically
- The global `Fold ignored files` option was replaced with the built-in `Ignored files` rule; the `Fold Ignored Files` action in the Project View options menu now toggles that rule, and existing settings are migrated automatically
- The defaults for new projects are no longer overwritten every time the settings of any project are saved; they change only when stored explicitly
- A trailing slash in a pattern is no longer ignored: `build/` now matches directories only, use `build` to match both files and directories
- Settings page layout: the pattern field and the table column are called `Patterns` (a rule is a name plus patterns and a style), `Match directories` was renamed to `Also fold directories`, the options depending on `Enable folding` are indented, and the support note moved to the bottom of the page
- New rules start with an empty name and patterns instead of `Rule name` and `*.md`; rules with a blank name are labeled `Unnamed rule` in the Project View, and rules with no patterns are kept (but not applied) instead of being silently dropped
- Group colors picked from the palette are stored by name (`Blue`) and newly enabled colors default to the palette instead of a color derived from the current theme; custom colors stored by earlier versions are kept and still used as is in both themes
- The live preview spans the full height of the settings page next to the scrollable options and rules, so it stays visible while a rule is edited; selecting a rule highlights its folding group in the preview and vice versa. The preview is read-only: it offers no Project View context menu and does not open files behind the Settings dialog

### Fixed

- The settings and the rules are scrollable when they do not fit the Settings dialog
- The selected rule stays selected after `Apply`, `Reset`, and `Copy from Defaults`, and the first rule is selected when the settings page opens, so the rule editor no longer goes blank
- The `?` button of the settings page opens the plugin documentation instead of a non-existent JetBrains help page
- The settings preview and the match counter are refreshed once the typing pauses instead of on every keystroke, and the Project View is restructured once per burst of VCS file status or patterns file changes instead of on every event, which used to block the IDE during VCS refreshes
- Files are folded in the scope-based Project View panes (`Project Files`, custom scopes) as well; directories cannot be folded there, as the platform passes files only ([#1](https://github.com/hsz/intellij-foldable-project-view/issues/1))

## [2.1.0] - 2026-10-10

### Added

- Support for multiple rules/groups in the Project View
- Possibility for specifying the background/foreground of the rule
- Folding rules are available as search scopes (e.g., in Find in Files)
- Ignored files (VCS ignore files like `.gitignore`) are folded into a dedicated `Ignored files` group ([#4](https://github.com/hsz/intellij-foldable-project-view/issues/4))
- Folding groups show the number of folded elements
- Support for IDE Settings Sync: the last saved settings are synchronized and used as defaults for projects without stored settings ([#41](https://github.com/hsz/intellij-foldable-project-view/issues/41))

### Changed

- Use a separated storage file within the `.idea` directory: `FoldableProjectView.xml`
- Patterns are matched against the path relative to the module content root, using gitignore-like glob syntax
- Folding groups are sorted right after directories
- Require IntelliJ Platform 2025.3+

### Fixed

- Ignored files located outside of a module were not folded into the `Ignored files` group ([#11](https://github.com/hsz/intellij-foldable-project-view/issues/11))
- Avoid Project View refresh on the wrong thread, which could freeze Rider ([#28](https://github.com/hsz/intellij-foldable-project-view/issues/28))
- Folding was not applied to folders attached to a Rider solution ([#46](https://github.com/hsz/intellij-foldable-project-view/issues/46))
- Matched elements were duplicated in the Project View instead of being moved into the group, and folding was not applied to module roots reached through other Project View parent nodes or content roots ([#92](https://github.com/hsz/intellij-foldable-project-view/issues/92))
- `Hide empty groups`, `Hide all groups`, and `Fold ignored files` options were not applied
- Project View was not refreshed after applying the settings
- Space-separated patterns in a single rule were treated as one pattern; patterns can now be separated by spaces, commas, or both ([#64](https://github.com/hsz/intellij-foldable-project-view/issues/64))
- `Select Opened File` was delayed on large projects due to excessive pattern matching ([#93](https://github.com/hsz/intellij-foldable-project-view/issues/93))
- `Select Opened File` did not highlight files located in folded groups or folded directories ([#15](https://github.com/hsz/intellij-foldable-project-view/issues/15))
- `Fold Root Files` and `Fold Ignored Files` actions were shown on the Welcome screen, where no project is available ([#82](https://github.com/hsz/intellij-foldable-project-view/issues/82))
- Folders matched by VCS ignore rules, like `.idea`, stopped folding after `.gitignore` was added to git ([#9](https://github.com/hsz/intellij-foldable-project-view/issues/9))

## [1.1.4] - 2022-11-13

### Added

- Support 231.* IDE releases

## [1.1.1]

### Fixed

- Fixed incorrect `projectConfigurable` name

## [1.1.0]

### Added

- Hide files or folders that are ignored or excluded (@Recks11)

## [1.0.2]

### Added

- Dark/light icons
- Support for 2021.3

## [1.0.0]

### Added

- Fold matching root elements of the project modules in the Project View
- Enable/disable folding via Preferences or Project View options menu
- Optionally fold directories
- Hide empty groups
- Hide all groups
- Case-insensitive matching
- Live ProjectView preview
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

[Unreleased]: https://github.com/hsz/intellij-foldable-projectview/compare/v2.1.0...HEAD
[2.1.0]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.4...v2.1.0
[1.1.4]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.1...v1.1.4
[1.1.1]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.0.0...v1.0.2
[1.0.0]: https://github.com/hsz/intellij-foldable-projectview/commits/v1.0.0
