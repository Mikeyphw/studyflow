import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinLibrarySerializationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("studyflow.kotlin.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
    }
}
