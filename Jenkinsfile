properties([
		buildDiscarder(logRotator(numToKeepStr: '10')),
		pipelineTriggers([
				cron('@daily')
		]),
])

def SUCCESS = hudson.model.Result.SUCCESS.toString()
currentBuild.result = SUCCESS

try {
	parallel docs: {
		stage('Deploy Docs') {
			node('linux') {
				checkout scm
				sh "git clean -dfx"
				try {
					withCredentials([file(credentialsId: 'docs.spring.io-jenkins_private_ssh_key', variable: 'DEPLOY_SSH_KEY')]) {
						withEnv(["JAVA_HOME=${tool 'jdk8'}"]) {
							sh './gradlew deployDocs --no-daemon --stacktrace -PdeployDocsSshKeyPath=$DEPLOY_SSH_KEY -PdeployDocsSshUsername=$SPRING_DOCS_USERNAME'
						}
					}
				}
				catch (e) {
					currentBuild.result = 'FAILED: docs'
					throw e
				}
			}
		}
	}
}
finally {
	def buildStatus = currentBuild.result
	def buildNotSuccess = !SUCCESS.equals(buildStatus)
	def lastBuildNotSuccess = !SUCCESS.equals(currentBuild.previousBuild?.result)

	if (buildNotSuccess || lastBuildNotSuccess) {
		stage('Notify') {
			node {
				final def RECIPIENTS = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]

				def subject = "${buildStatus}: Build ${env.JOB_NAME} ${env.BUILD_NUMBER} status is now ${buildStatus}"
				def details = "The build status changed to ${buildStatus}. For details see ${env.BUILD_URL}"

				emailext(
						subject: subject,
						body: details,
						recipientProviders: RECIPIENTS,
						to: "$SPRING_SESSION_TEAM_EMAILS"
				)
			}
		}
	}
}
