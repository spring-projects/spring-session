package build;

import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

public class GemFireServerPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.tasks.create('gemFireServer', GemFireServerTask)

		project.tasks.integrationTest.doLast {
			println 'Stopping GemFire Server...'
			project.tasks.gemFireServer.process?.destroyForcibly()
		}

		project.tasks.prepareAppServerForIntegrationTests {
			dependsOn project.tasks.gemFireServer
			doFirst {
				project.gretty {
					jvmArgs = ["-Dspring.session.data.gemfire.port=${project.tasks.gemFireServer.port}"]
				}
			}
		}
	}

	static int availablePort() {
		new ServerSocket(0).withCloseable { socket ->
			socket.localPort
		}
	}

	static class GemFireServerTask extends DefaultTask {
		def mainClassName = "sample.ServerConfig"
		def process
		def port
		boolean debug

		@TaskAction
		def greet() {
			port = availablePort()
			println "Starting GemFire Server on port [$port]..."
	
			def out = debug ? System.out : new StringBuilder()
			def err = debug ? System.out : new StringBuilder()
	
			String classpath = project.sourceSets.main.runtimeClasspath.collect { it }.join(File.pathSeparator)
			String gemfireLogLevel = System.getProperty('spring.session.data.gemfire.log-level', 'warning')

			String[] commandLine = [
				'java', '-server', '-ea', '-classpath', classpath,
				//"-Dgemfire.log-file=gemfire-server.log",
				//"-Dgemfire.log-level=config",
				"-Dspring.session.data.gemfire.log-level=" + gemfireLogLevel,
				"-Dspring.session.data.gemfire.port=${port}",
				'sample.ServerConfig'
			]
	
			//println commandLine
			
			project.tasks.appRun.ext.process = process = commandLine.execute()

			process.consumeProcessOutput(out, err)
		}
	}
}
