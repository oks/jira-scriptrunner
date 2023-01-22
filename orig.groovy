def spent_p = 'customfield_10040'
def current_update = 'customfield_10054'
def ie = 'customfield_10035'
def scope_des = 'customfield_10500'
def scope_pm = 'customfield_10049'
def sumie = 'customfield_10074'
def sum_ie_d = 'customfield_10039'
def sum_ie_p = 'customfield_10038'
def sum_orig_d = 'customfield_10042'
def sum_orig_p = 'customfield_10041'
def sum_orig_subtasks = 'customfield_10069'
def sum_spent_d = 'customfield_10037'
def sum_spent_p = 'customfield_10040'

def epic_link = 'customfield_10014'

def issue_type_pm_task = '10012'
def issue_type_communication = '10015'
def issue_type_design_component = '10011'
def issue_type_design_subtask = '10016'




def epic_field_to_update = 'customfield_10041'
def workType = 'PM'

//Epic Link Custom Field ID
final epicLinkCf = get("/rest/api/2/field")
    .asObject(List)
    .body
    .find {
        (it as Map).name == 'Epic Link'
    } as Map

logger.warn("TRIGGERED ON ISSUE UPD: $issue.key")

def issueFields = get("/rest/api/2/issue/$issue.key")
    .asObject(Map)
    .body
    .fields as Map


logger.warn("issue fields = {}", issueFields)


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
    logger.warn("issue parent req")
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


def is_finished = true

int startAt = 0
def sum = 0.0
def scope = ''

while (is_finished) {

//Obtain all related epic issues, including sub-tasks
def allChildIssues = get("/rest/api/2/search")
    .queryString('jql', "linkedissue = $epicKey AND (issuetype = 'PM Task')")
    .queryString('startAt', startAt)
    .header('Content-Type', 'application/json')
    .asObject(Map)
    .body
    .issues as List<Map>


    int scope_count = (int) allChildIssues.size()
    if (scope_count == 0) {
    is_finished = false
    } else {
       startAt += scope_count
    }

def issues = allChildIssues.findAll { it.key != epicKey }

issues.each {
    def fields = get("/rest/api/2/issue/$it.key")
        .header('Content-Type', 'application/json')
        .asObject(Map)
        .body
        .fields as Map
        
    def numberCfValue = fields["timeoriginalestimate"] ?: 0
    sum += numberCfValue as Float 
}
}

logger.warn("sum = {}", sum)

put("/rest/api/2/issue/$epicKey")
    .header("Content-Type", "application/json")
    .body([
        fields: [
            "${epic_field_to_update}": (sum / 3600).round(1) as Float
        ]
    ]).asString()
    
