<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Foldable Project View Changelog

## [Unreleased]

It has been a while since the last Foldable Project View release, right?
A lot was on my plate during this time, trust me.

However, I decided to reboot this project and start with the 2.0.0 release, which may work.
Or not. 
But we have to start with anything after this time!

Please, report any issues or ideas via [GitHub Issues](https://github.com/hsz/intellij-foldable-project-view/issues).

Thanks!
Jakub

### Added
- Support for multiple rules/groups in the Project View
- Possibility for specifying the background/foreground of the rule
- Folding rules are available as search scopes (e.g., in Find in Files)
- Ignored files are folded into a dedicated `Ignored files` group
- Folding groups show the number of folded elements

### Changed
- Use a separated storage file within the `.idea` directory: `FoldableProjectView.xml`
- Patterns are matched against the path relative to the module content root, using gitignore-like glob syntax
- Folding groups are sorted right after directories
- Require IntelliJ Platform 2025.3+

### Fixed
- Matched elements were duplicated in the Project View instead of being moved into the group, and folding was not applied to module roots reached through other Project View parent nodes or content roots ([#92](https://github.com/hsz/intellij-foldable-project-view/issues/92))
- `Hide empty groups`, `Hide all groups`, and `Fold ignored files` options were not applied
- Project View was not refreshed after applying the settings
- Space-separated patterns in a single rule were treated as one pattern; patterns can now be separated by spaces, commas, or both ([#64](https://github.com/hsz/intellij-foldable-project-view/issues/64))
- `Select Opened File` was delayed on large projects due to excessive pattern matching ([#93](https://github.com/hsz/intellij-foldable-project-view/issues/93))
- `Fold Root Files` and `Fold Ignored Files` actions were shown on the Welcome screen, where no project is available ([#82](https://github.com/hsz/intellij-foldable-project-view/issues/82))

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

[Unreleased]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.4...HEAD
[1.1.4]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.1...v1.1.4
[1.1.1]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.0.0...v1.0.2
[1.0.0]: https://github.com/hsz/intellij-foldable-projectview/commits/v1.0.0
