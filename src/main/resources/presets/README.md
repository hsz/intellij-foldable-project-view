# Built-in presets

Every YAML file in this directory is a rule set offered by the `+` button of the rules table (`Settings | Tools |
Foldable Project View`) and reachable from other rule set files as `extends: [builtin:<file name without extension>]`.
Presets are **never applied on their own** – a user picks one, or a project file extends it.

The format is the one of the `.foldableprojectview.yaml` files, see the [documentation](../../../../README.md#rule-set-files)
and the [JSON schema](../schemas/foldableprojectview.schema.json):

```yaml
name: Gradle                               # required; shown in the `+` menu
description: Gradle wrapper and scripts    # recommended; shown as the menu item description
tags: [gradle, kotlin, build]              # optional; used to group and suggest the presets
rules:
  - id: gradle                             # required and unique within the file; stable, lowercase, dash-separated
    name: Gradle
    patterns: ["gradle/", "gradlew*", "*.gradle.kts"]
    background: Green                      # optional; a palette color (Blue, Green, Orange, Rose, Violet, Yellow, Gray)
```

## Contributing a preset

1. Add a `<name>.yaml` file here and list it in [`index.txt`](index.txt) – the order of the index is the order in the menu.
2. Give every rule a unique, stable `id`: other rule sets rely on it to override the rule (`enabled: false`, another color).
3. Keep the patterns conservative: a preset should fold the noise of a well-known tool, not sources, and should not use
   the `hide` action – the users can switch a copied rule to `hide` themselves.
4. Do not fold `.foldableprojectview.yaml` itself from a broad pattern (`.*`, `*.yaml`): add `!.foldableprojectview.yaml`.
5. Run `./gradlew test` – `RuleSetPresetsTest` checks that every preset parses, is listed in the index, has unique ids,
   valid glob patterns, and known colors.
