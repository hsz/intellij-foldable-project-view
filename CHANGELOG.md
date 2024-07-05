<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Foldable Project View Changelog

## [Unreleased]

This is an EAP release.
Please report any issues or ideas
via [GitHub Issues](https://github.com/pavankjadda/intellij-foldable-project-view/issues).

Thanks!
@pavankjadda

### Added

- Support for multiple rules/groups in the Project View
- Possibility for specifying the background/foreground of the rule

### Changed

- Use a separated storage file within the `.idea` directory: `FoldableProjectView.xml`

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
- Initial scaffold created
  from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

[Unreleased]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.4...HEAD

[1.1.4]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.1...v1.1.4

[1.1.1]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.1.0...v1.1.1

[1.1.0]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.0.2...v1.1.0

[1.0.2]: https://github.com/hsz/intellij-foldable-projectview/compare/v1.0.0...v1.0.2

[1.0.0]: https://github.com/hsz/intellij-foldable-projectview/commits/v1.0.0
