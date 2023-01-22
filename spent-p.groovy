//spent-p

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

def issue_type_pm_task = '10012'
def issue_type_communication = '10015'
def issue_type_design_component = '10011'
def issue_type_design_subtask = '10016'


//Scenario 
//When work is logged / updated / deleted recalculate Epic total time spent 

logger.warn("TRIGGERED ON ISSUE UPD: $worklog.issueId")

     
def epicKey = get_epic_key(worklog.issueId)

if (!epicKey) {
    logger.warn("script abort - no epic found or its epic")
    return
} 

     
def pm = calc_issues("linkedissue = $epicKey AND (issuetype = 'PM Task')", epicKey)
def design = calc_issues("linkedissue = $epicKey AND (issuetype != 'PM Task')", epicKey)      

logger.warn("sum_all_design = {}", design)
logger.warn("sum_all_pm = {}", pm)
       
// Update Jira fields with calculated values    
put("/rest/api/2/issue/$epicKey")
    .header("Content-Type", "application/json")
    .body([
        fields: [
            "${sum_spent_d}": design["sum_spent"],
            "${sum_spent_p}": pm["sum_spent"]
        ]
    ]).asString()       
      
      
      ///
      ///Helpers
      ///
    
public Map calc_issues(String JQL, Object epicKey) {
    def is_finished = true

    int startAt = 0
    
    def Map result = [ : ]

    def sum_estimate = 0.0
    def sum_spent = 0.0
    def sum_scope = ''
    def sum_ie = 0.0
    def ie = 'customfield_10035'

    while (is_finished) {

        //Obtain all related epic issues, including sub-tasks
        def allChildIssues = get("/rest/api/2/search")
             .queryString('jql', JQL)
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
        sum_estimate += numberCfValue as Float 
        
        def timeObject = fields.find { it.key == 'timetracking' }?.value as Map
        def time = timeObject.find { it.key == 'timeSpentSeconds' }?.value as Float ?: 0
        sum_spent += time
        
        def numberCValue = fields[ie] ?: 0
        sum_ie += numberCValue as Float
    
        def issueSummary = fields.find { it.key == 'summary' }?.value as String
        sum_scope += it.key + ' ' + issueSummary + '\n'
        }
    }

        result["sum_spent"] = (sum_spent / 3600).round(1) ?: 0
        result["sum_estimate"] = (sum_estimate / 3600).round(1)
        result["sum_ie"] = sum_ie
        result["sum_scope"] = sum_scope
        
        return result
}

      
      
      
      
      
      
    public Object get_epic_key(String issue_id) {
       
       def epic_link = 'customfield_10014'
       
       
        def issueFields = get("/rest/api/2/issue/$issue_id")
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

        def epicKey = issueFields.find { it.key == epic_link }?.value


        //If the issue is sub-task, Epic Key is obtained from the parent issue

        if (isSubTask == 'true') {
             def parentIssueField = get("/rest/api/2/issue/$issueParent.key")
                .asObject(Map)
                .body
                .fields as Map

            epicKey = parentIssueField.find { it.key == epic_link }.value
        }
    
        return epicKey
    }  
