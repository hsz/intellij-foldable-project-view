package ski.chrzanow.foldableprojectview.json

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import ski.chrzanow.foldableprojectview.FoldableProjectViewBundle.message
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.RULE_SET_FILE
import ski.chrzanow.foldableprojectview.FoldableProjectViewConstants.RULE_SET_SCHEMA_PATH
import ski.chrzanow.foldableprojectview.rules.RuleSetRepository

/**
 * Attaches the rule set JSON schema to the `.foldableprojectview.yaml` files and to the YAML files of the user directory,
 * so the IDE offers completion, documentation, and validation while they are edited. Available only with the bundled
 * JSON plugin, see the `ski.chrzanow.foldableprojectview.json` module.
 */
class RuleSetSchemaProviderFactory : JsonSchemaProviderFactory {

    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(RuleSetSchemaProvider())

    private class RuleSetSchemaProvider : JsonSchemaFileProvider {

        override fun isAvailable(file: VirtualFile) = when {
            file.isDirectory -> false
            file.name == RULE_SET_FILE -> true
            file.extension?.lowercase() !in setOf("yaml", "yml") -> false
            else -> service<RuleSetRepository>().isUserFile(file.path)
        }

        override fun getName() = message("foldableProjectView.schema.name")

        override fun getSchemaFile() = JsonSchemaProviderFactory.getResourceFile(RuleSetSchemaProviderFactory::class.java, RULE_SET_SCHEMA_PATH)

        override fun getSchemaType() = SchemaType.embeddedSchema

        override fun getRemoteSource() = "https://github.com/hsz/intellij-foldable-project-view#rule-set-files"
    }
}
