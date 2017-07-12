import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin

class MavenBomPlugin implements Plugin<Project> {
	static String MAVEN_BOM_TASK_NAME = 'mavenBom'

	void apply(Project project) {
		project.plugins.apply(JavaPlugin)
		project.plugins.apply(MavenPlugin)

		project.group = project.rootProject.group
		project.task(MAVEN_BOM_TASK_NAME, type: MavenBomTask, group: 'Generate', description: 'Configures the pom as a Maven Bill of Materials (BOM)')
		project.install.dependsOn project.mavenBom
	}
}
