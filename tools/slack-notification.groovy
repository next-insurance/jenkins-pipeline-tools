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

    def gitMessage = sh(returnStdout: true, script: 'git log -2 --pretty=%B').trim()
    if (gitMessage) {
        usersMessage = "${usersMessage}\n*Last git comment:*\n${gitMessage}"
    }

    return usersMessage
}

def notifySuccess(currentBuild, usersMessage, slackChannel, env, alwaysNotify=false) {
    if (alwaysNotify || currentBuild.previousBuild != null && currentBuild.previousBuild.result != "SUCCESS") {
        def message = "🍾 Success!\n*Job:* ${env.JOB_NAME}\n*Build:* #${env.BUILD_NUMBER}\n*Url:* ${env.BUILD_URL}"
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
    def message = "😓 Failed!\n*Job:* ${env.JOB_NAME}\n*Build:* #${env.BUILD_NUMBER}\n*Url:* ${env.BUILD_URL}"
    if (usersMessage) {
        message = message + usersMessage
    }
    slackSend(color: color,
            channel: slackChannel,
            message: message
    )
}

def notifyToApi(currentBuild, env, failedStage, url) {
    if (currentBuild.result == "SUCCESS") {
        failedStage = ''
    }
    def body = """{
                  "buildNumber": ${env.BUILD_NUMBER},
                  "jobName": "${env.JOB_NAME}",
                  "status": "${currentBuild.result}",
                  "prevStatus": "${currentBuild.previousBuild.result}",
                  "failedStep": "${failedStage}"
              }"""
    if (url) {
        println("sending current job info: " + body)
        httpRequest acceptType: 'APPLICATION_JSON',contentType: 'APPLICATION_JSON',
                httpMode: 'POST',
                requestBody: body,
                url: url
    }
}

return this
