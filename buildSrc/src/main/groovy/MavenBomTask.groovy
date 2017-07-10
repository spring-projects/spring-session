import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class MavenBomTask extends DefaultTask {

	Set<Project> projects = []

	File bomFile

	MavenBomTask() {
		this.group = 'Generate'
		this.description = 'Generates a Maven Bill of Materials (BOM). See http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies'
		this.projects = project.subprojects
		this.bomFile = project.file("${->project.buildDir}/maven-bom/${->project.name}-${->project.version}.txt")
	}

	@TaskAction
	void configureBom() {
		project.configurations.archives.artifacts.clear()

		bomFile.parentFile.mkdirs()
		bomFile.write('Maven Bill of Materials (BOM). See http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies')
		project.artifacts {
			// work around GRADLE-2406 by attaching text artifact
			archives(bomFile)
		}
		project.install {
			repositories.mavenInstaller {
				pom.whenConfigured {
					packaging = 'pom'
					withXml {
						asNode().children().last() + {
							delegate.dependencyManagement {
								delegate.dependencies {
									projects.sort { dep -> "$dep.group:$dep.name" }.each { p ->
										delegate.dependency {
											delegate.groupId(p.group)
											delegate.artifactId(p.name)
											delegate.version(p.version)
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}
