# Foldable Project View

![Build](https://github.com/hsz/intellij-foldable-projectview/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/17288.svg)](https://plugins.jetbrains.com/plugin/17288)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/17288.svg)](https://plugins.jetbrains.com/plugin/17288)

<!-- Plugin description -->

> **Support Foldable Project View**
>
> If you find this plugin useful, support its development via **[GitHub Sponsors](https://github.com/sponsors/hsz)**, **[Ko-fi](https://ko-fi.com/hszanowski)**, or **[PayPal](https://paypal.me/hsz)**.
>
> You can also find my other projects on [GitHub](https://github.com/hsz).

The Foldable Project View is a plugin for the IntelliJ-based IDEs that lets you fold files located in the root of your project.

Available features:
- Fold matching root elements of the project modules in the Project View
- Multiple named folding rules with custom foreground/background colors
- Enable/disable folding via Preferences or Project View options menu
- Optionally fold directories
- Fold VCS-ignored files
- Hide empty groups
- Hide all groups
- Case-insensitive matching
- Search scopes based on the folding rules
- Live Project View preview

## Demo

<img src="https://github.com/hsz/intellij-foldable-projectview/raw/main/.github/readme/foldable-projectview.gif" alt="Foldable Project View Demo" />

<!-- Plugin description end -->

## Screenshots

### Project View
![Project View](.github/readme/project-view.png)

### Preferences
![Preferences](.github/readme/preferences.png)

## Usage

Open <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Foldable Project View</kbd> and define the folding rules.
Each rule has a name, which is used as the group label in the Project View, and a list of space-separated
gitignore-like glob patterns, e.g.:

```
README.md *.yml gradle* .github
```

Patterns are matched against paths relative to the module content root. Patterns without a slash match on any level,
and a leading slash anchors the pattern to the root (`/README.md`). Rules are applied in order – the first matching rule
takes precedence. Folding rules are also available as search scopes (e.g., in <kbd>Find in Files</kbd>).

## Development

```bash
./gradlew buildPlugin   # build the plugin distribution into build/distributions
./gradlew test          # run tests
./gradlew runIde        # run a sandboxed IDE with the plugin installed
./gradlew verifyPlugin  # run the IntelliJ Plugin Verifier
```

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Foldable Project View"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/hsz/intellij-foldable-projectview/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
