def dateFormat = "yyyy-MM-dd\'T\'HH:mm:ss.SSSZ"

Date start_date = Date.parse(dateFormat, worklog.started)
logger.warn("start_date = {}", start_date)
Date now = new Date()
def diff = now - start_date

if (diff > 2) {
    
    def issue_fields = get("/rest/api/2/issue/$worklog.issueId")
            .header('Content-Type', 'application/json')
            .asObject(Map)
            .body as Map
    
    def issue_key = issue_fields.find { it.key == 'key' }?.value as String
    
    def w_author = worklog.updateAuthor ?: worklog.author
    def authorName = (w_author as Map).find { it.key == 'displayName' }?.value as String
    def pretty_start = start_date.format('DD-MMM-YYYY')
    
    
    Map msg_dets = [text: ":pushpin: Worklog detected by ${authorName} for date ${pretty_start} (${diff} days ago) | ${worklog.timeSpent} \nhttps://tasks.atlassian.net/browse/${issue_key} \n${worklog.comment}"]
     
    postToSlack(msg_dets)
}


public postToSlack(Map message) {
    
    def SLACK_API_TOKEN = "xoxb-"

    
    def channelName = 'C04KZJ'
    def username = 'Scriptrunner'
    Map msg_meta = [ channel: channelName, username: username ,icon_emoji: ':rocket:']

    // Map msg_dets = [text: "A new issue was created with the details below: \n Issue key = ${issueKey} \n Issue Sumamry = ${summary} \n Issue Description = ${description}"]

// Post the constructed message to slack
def postToSlack = post('https://slack.com/api/chat.postMessage')
        .header('Content-Type', 'application/json')
        .header('Authorization', "Bearer ${SLACK_API_TOKEN}") // Store the API token as a script variable named SLACK_API_TOKEN
        .body(msg_meta + message)
        .asObject(Map)
        .body

assert postToSlack : "Failed to create Slack message check the logs tab for more details"
}
