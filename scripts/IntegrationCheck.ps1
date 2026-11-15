. "$PSScriptRoot\Environment.ps1"
$base = $env:API_BASE_URL
$passed = 0
$created = @{}
function Api($method,$path,$body=$null) {
    $argsRequest = @{ Method=$method; Uri="$base/$path"; TimeoutSec=10 }
    if ($null -ne $body) { $argsRequest.Body = ($body | ConvertTo-Json -Depth 8 -Compress); $argsRequest.ContentType='application/json; charset=utf-8' }
    Invoke-RestMethod @argsRequest
}
function Check($condition,$label) { if (-not $condition) { throw "FAILED: $label" }; $script:passed++; Write-Host "PASS $label" }
function Reject($method,$path,$body,$status,$label) {
    $actual=0
    try { $null=Api $method $path $body } catch { $actual=[int]$_.Exception.Response.StatusCode }
    Check ($actual -eq $status) "$label (HTTP $actual)"
}
$suffix = [guid]::NewGuid().ToString('N').Substring(0,8)
try {
    $users = Api GET 'users'; Check ($users.Count -ge 3) 'Seed users loaded'
    $categories = Api GET 'categories'; Check ($categories.Count -ge 3) 'Seed categories loaded'
    $equipment = Api GET 'equipment'; Check ($equipment.Count -ge 3) 'Seed equipment loaded'
    $tickets = Api GET 'tickets'; Check ($tickets.Count -ge 4) 'Seed tickets loaded'
    $user = Api POST 'users' @{fullName='Integration User'; email="check-$suffix@example.edu"; department='QA'; technician=$true}; $created.user=$user.id
    Check ($user.id -gt 0) 'Create user'
    $user.fullName='Integration Updated'; $user=Api PUT "users/$($user.id)" $user
    Check ($user.fullName -eq 'Integration Updated') 'Edit user'
    Reject POST 'users' @{fullName='Duplicate';email=$user.email;department='QA';technician=$false} 409 'Duplicate email rejected'
    $category = Api POST 'categories' @{name="QA-$suffix";description='Integration category'}; $created.category=$category.id
    Check ($category.id -gt 0) 'Create category'
    $category.description='Updated'; $category=Api PUT "categories/$($category.id)" $category
    Check ($category.description -eq 'Updated') 'Edit category'
    $item = Api POST 'equipment' @{inventoryNumber="QA-$suffix";name='QA PC';location='Lab';criticality=75}; $created.equipment=$item.id
    Check ($item.criticality -eq 75) 'Create equipment'
    $item.location='Lab 2'; $item=Api PUT "equipment/$($item.id)" $item
    Check ($item.location -eq 'Lab 2') 'Edit equipment'
    Reject POST 'equipment' @{inventoryNumber="BAD-$suffix";name='Bad';location='Lab';criticality=101} 400 'Invalid factor rejected'
    $body=@{title='Integration ticket';description='Check CRUD and state';requesterId=1;assigneeId=$user.id;equipmentId=$item.id;categoryId=$category.id;state='NEW';urgency=100;impact=50;criticality=0;priority=0}
    $ticket=Api POST 'tickets' $body; $created.ticket=$ticket.id
    Check ($ticket.state -eq 'NEW') 'Ticket initial state'
    Check ($ticket.priority -eq 76.25 -and $ticket.criticality -eq 75) 'Server computes priority and ignores supplied score'
    $body.title='Updated title'; $ticket=Api PUT "tickets/$($ticket.id)" $body
    Check ($ticket.title -eq 'Updated title') 'Edit ticket'
    Reject POST "tickets/$($ticket.id)/transition" @{state='CLOSED'} 400 'State skip rejected'
    $body.state='CLOSED'; Reject PUT "tickets/$($ticket.id)" $body 400 'State bypass rejected'
    Reject DELETE "equipment/$($item.id)" $null 409 'Referenced equipment protected'
    Reject DELETE "users/$($user.id)" $null 409 'Referenced user protected'
    Reject DELETE "categories/$($category.id)" $null 409 'Referenced category protected'
    foreach($state in @('ASSIGNED','IN_PROGRESS','CLOSED')) {
        $ticket=Api POST "tickets/$($ticket.id)/transition" @{state=$state}
        Check ($ticket.state -eq $state) "Transition to $state"
    }
    Reject POST "tickets/$($ticket.id)/transition" @{state='NEW'} 400 'Reverse transition rejected'
    $read=Api GET "tickets/$($ticket.id)"; Check ($read.state -eq 'CLOSED') 'Read ticket by ID'
    Reject GET 'tickets/999999999' $null 404 'Missing ticket returns 404'
    Reject GET 'tickets/not-a-number' $null 404 'Unsupported path returns 404'
    Api DELETE "tickets/$($ticket.id)" | Out-Null; $created.Remove('ticket')
    Reject GET "tickets/$($ticket.id)" $null 404 'Delete ticket'
    Api DELETE "equipment/$($item.id)" | Out-Null; $created.Remove('equipment')
    Reject GET "equipment/$($item.id)" $null 404 'Delete equipment'
    Api DELETE "categories/$($category.id)" | Out-Null; $created.Remove('category')
    Reject GET "categories/$($category.id)" $null 404 'Delete category'
    Api DELETE "users/$($user.id)" | Out-Null; $created.Remove('user')
    Reject GET "users/$($user.id)" $null 404 'Delete user'
    Write-Host "Integration checks passed: $passed"
} finally {
    foreach ($pair in @(@('ticket','tickets'),@('equipment','equipment'),@('category','categories'),@('user','users'))) {
        if ($created.ContainsKey($pair[0])) { try { Api DELETE "$($pair[1])/$($created[$pair[0]])" | Out-Null } catch { Write-Warning "Cleanup failed for $($pair[0])" } }
    }
}
