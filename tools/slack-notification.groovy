def buildNotifyMessage(env, allowedEmailDomain) {
    def userId
    def usersMessage
    def triggeredEmail
    wrap([$class: 'BuildUser']) {
        triggeredEmail = env.BUILD_USER_EMAIL
    }
    if (triggeredEmail) {
        println('Triggered email: ' + triggeredEmail)
        userId = slackUserIdFromEmail(triggeredEmail)
        if (userId) {
            usersMessage = "\n*Job trigger:* <@$userId>\n"
        }
    }

    def committerDetails = sh(returnStdout: true, script: 'git --no-pager show -s --format=\'%ae\'')
    if (committerDetails) {
        committerDetails = committerDetails.trim()
        println('Last committer email: ' + committerDetails)
        if (committerDetails.endsWith(allowedEmailDomain)) {
            userId = slackUserIdFromEmail(committerDetails)
            if (userId) {
                usersMessage = usersMessage + "\n*Last committer:* <@$userId>"
            }
        } else {
            usersMessage = usersMessage + "\n*Last committer* without ${allowedEmailDomain} email: ${committerDetails}"
        }
    }

    def gitMessage = sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
    if (gitMessage) {
        usersMessage = "${usersMessage}\n*Last git comment:*\n${gitMessage}"
    }

    return usersMessage
}

def notifySuccess(currentBuild, usersMessage, slackChannel, env) {
    if (currentBuild.previousBuild != null && currentBuild.previousBuild.result != "SUCCESS") {
        def message = "${env.JOB_NAME} - #${env.BUILD_NUMBER} back no normal: ${env.BUILD_URL}"
        if (usersMessage) {
            message = message + usersMessage
        }
        slackSend(color: "good",
                channel: slackChannel,
                message: message
        )
    }
}

def notifyFail(currentBuild, usersMessage, slackChannel, env, finishDeploy) {
    def color = "danger"
    if (finishDeploy == true) {
        currentBuild.result = "UNSTABLE"
        color = "warning";
    }
    def message = "${env.JOB_NAME} - #${env.BUILD_NUMBER} was failed: ${env.BUILD_URL}"
    if (usersMessage) {
        message = message + usersMessage
    }
    slackSend(color: color,
            channel: slackChannel,
            message: message
    )
}

return this
