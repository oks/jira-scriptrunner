//spent-p

def epic_field_to_update = 'customfield_10040'

//Epic Link Custom Field ID
final epicLinkCf = get("/rest/api/2/field")
    .asObject(List)
    .body
    .find {
        (it as Map).name == 'Epic Link'
    } as Map

logger.warn("TRIGGERED ON worklog: $worklog")
logger.warn("TRIGGERED ON ISSUE UPD: $worklog.issueId")


def issueFields = get("/rest/api/2/issue/$worklog.issueId")
    .asObject(Map)
    .body
    .fields as Map


//If issue has no parent or is not related with an epic, the script will not be executed
def issueParent = issueFields.find { it.key == 'parent' }?.value as Map

def issueType = issueFields.find { it.key == 'issuetype' }?.value as Map
def isSubTask = issueType.find { it.key == 'subtask' }?.value as String
logger.warn("is subtask = {}", isSubTask)

def issueTypeName = issueType.find { it.key == 'name' }?.value as String
def isEpic = issueTypeName == "Epic"

logger.warn("is Epic = {}", isEpic)

def epicKey = issueFields.find { it.key == epicLinkCf.id }?.value


//If the issue is sub-task, Epic Key is obtained from the parent issue

if (isSubTask == 'true') {
    def parentIssueField = get("/rest/api/2/issue/$issueParent.key")
        .asObject(Map)
        .body
        .fields as Map

    epicKey = parentIssueField.find { it.key == epicLinkCf.id }.value
}

if (!epicKey) {
    logger.warn("script abort - no epic found or its epic")
    return
}


//Obtain all related epic issues, including sub-tasks
def allChildIssues = get("/rest/api/2/search")
    .queryString('jql', "linkedissue = $epicKey AND (issuetype = 'PM Task')")
    .header('Content-Type', 'application/json')
    .asObject(Map)
    .body
    .issues as List<Map>

def sum = 0
def issues = allChildIssues.findAll { it.key != epicKey }

issues.each {
    def issuesForTime = get("/rest/api/2/issue/$it.key")
        .header('Content-Type', 'application/json')
        .asObject(Map)
        .body
        .fields as Map
        
    
    def timeObject = issuesForTime.find { it.key == 'timetracking' }?.value as Map
    def time = timeObject.find { it.key == 'timeSpentSeconds' }?.value as Float ?: 0
    logger.warn("time object = {}", timeObject)
    logger.warn("time = {}", time)
    logger.warn("key = {}", it.key)
    sum += time / 3600
}

logger.warn("sum = {}", sum)



// // get legacy field from jira
// def epicFields = get("/rest/api/2/issue/$epicKey")
//     .asObject(Map)
//     .body
//     .fields as Map

// def legacyJiraWorklogs = epicFields["customfield_10044"] ?: 0
// sum += legacyJiraWorklogs as Float


put("/rest/api/2/issue/$epicKey")
    .header("Content-Type", "application/json")
    .body([
        fields: [
            "${epic_field_to_update}": sum as Float
        ]
    ]).asString()
    
