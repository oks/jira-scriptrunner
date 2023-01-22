//spent-p

def spent-p = 'customfield_10040'
def current-update = 'customfield_10054'
def ie = 'customfield_10035'
def scope-des = 'customfield_10500'
def scope-pm = 'customfield_10049'
def sumie = 'customfield_10074'
def sum-ie-d = 'customfield_10039'
def sum-ie-p = 'customfield_10038'
def sum-orig-d = 'customfield_10042'
def sum-orig-p = 'customfield_10041'
def sum-orig-subtasks = 'customfield_10069'
def sum-spent-d = 'customfield_10037'
def sum-spent-p = 'customfield_10040'

def epic-link = 'customfield_10014'

def issue-type-pm-task = '10012'
def issue-type-communication = '10015'
def issue-type-design-component = '10011'
def issue-type-design-subtask = '10016'


//Scenario 
//When work is logged / updated / deleted recalculate Epic total time spent 

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
     
        
def sum-all-design =  sumWorklogs("linkedissue = $epicKey AND (issuetype = 'PM Task')")    
def sum-all-pm =  sumWorklogs("linkedissue = $epicKey")         
        
// Update Jira fields with calculated values    
put("/rest/api/2/issue/$epicKey")
    .header("Content-Type", "application/json")
    .body([
        fields: [
            "${sum-spent-d}": sum-all-design as Float,
            "${sum-spent-p}": sum-all-pm as Float
        ]
    ]).asString()       
        




///
///Helpers
///
   public int sumWorklogs(String jql) { 
       
        def sum-worklogs = 0
        def allChildIssues = get("/rest/api/2/search")
             .queryString('jql', jql)
             .header('Content-Type', 'application/json')
             .asObject(Map)
             .body
             .issues as List<Map>
      
     //Get all issues in Epic and skip Epic itself
     def issues = allChildIssues.findAll { it.key != epicKey }   
       
     
     issues.each {
         def issuesForTime = get("/rest/api/2/issue/$it.key")
               .header('Content-Type', 'application/json')
               .asObject(Map)
               .body
               .fields as Map
        
    
         def timeObject = issuesForTime.find { it.key == 'timetracking' }?.value as Map
         def time = timeObject.find { it.key == 'timeSpentSeconds' }?.value as Float ?: 0

   
         sum-worklogs += time / 3600
    
         logger.warn("time object = {}", timeObject)
         logger.warn("time = {}", time)
         logger.warn("key = {}", it.key)
      }
  
      return sum-worklogs; 
   } 
        



    
