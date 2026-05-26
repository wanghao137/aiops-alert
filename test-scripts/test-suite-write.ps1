#
# AIOps Alert - Write-side workflow test (ASCII only)
# Cover: manual trigger / event lifecycle / story mode / AI command
#

$ErrorActionPreference = 'Continue'
$BASE = 'http://localhost:8090/api'
$AMP = [char]38

$global:results = @()
$global:passCount = 0
$global:failCount = 0

function Assert-Test {
  param([string]$name, [bool]$cond, [string]$detail = '')
  if ($cond) {
    $global:passCount++
    $global:results += [pscustomobject]@{ Name = $name; Status = 'PASS'; Detail = $detail }
    Write-Host ('  [PASS] ' + $name + ' ' + $detail) -ForegroundColor Green
  } else {
    $global:failCount++
    $global:results += [pscustomobject]@{ Name = $name; Status = 'FAIL'; Detail = $detail }
    Write-Host ('  [FAIL] ' + $name + ' ' + $detail) -ForegroundColor Red
  }
}

function Get-Api { param([string]$path)
  try {
    $r = Invoke-WebRequest -Uri ($BASE + $path) -UseBasicParsing -TimeoutSec 30
    return ($r.Content | ConvertFrom-Json)
  } catch {
    return [pscustomobject]@{ code = -1; message = $_.Exception.Message; data = $null }
  }
}

function Post-Api { param([string]$path, [object]$body = $null, [int]$timeout = 60)
  try {
    $params = @{ Uri = ($BASE + $path); Method = 'POST'; UseBasicParsing = $true; TimeoutSec = $timeout }
    if ($null -ne $body) {
      $params['Body'] = ($body | ConvertTo-Json -Depth 10)
      $params['ContentType'] = 'application/json'
    }
    $r = Invoke-WebRequest @params
    return ($r.Content | ConvertFrom-Json)
  } catch {
    return [pscustomobject]@{ code = -1; message = $_.Exception.Message; data = $null }
  }
}

function Form-Api { param([string]$path, [hashtable]$form, [int]$timeout = 60)
  try {
    $r = Invoke-WebRequest -Uri ($BASE + $path) -Method POST -Body $form -UseBasicParsing -TimeoutSec $timeout
    return ($r.Content | ConvertFrom-Json)
  } catch {
    return [pscustomobject]@{ code = -1; message = $_.Exception.Message; data = $null }
  }
}

function Get-Count { param($v); if ($null -eq $v) { return 0 }; return @($v).Count }

# ============================================================
# Workflow 1: manual trigger
# ============================================================
Write-Host ''
Write-Host '== Workflow 1: Manual trigger event ============' -ForegroundColor Cyan

$rules = (Get-Api '/alert-rules').data
$enabledRule = $rules | Where-Object { $_.status -eq 'ENABLED' } | Select-Object -First 1
$ruleDetail = (Get-Api ('/alert-rules/' + $enabledRule.id)).data
$obj = $ruleDetail.objects | Select-Object -First 1

if ($enabledRule -and $obj) {
  $beforeCount = (Get-Count (Get-Api '/alert-events').data)
  $resp = Post-Api '/alert-events/test' @{
    ruleId = $enabledRule.id
    objectId = $obj.id
    currentValue = 'cpu_usage=99% (test)'
    eventReason = 'system test manual trigger'
  }
  Assert-Test 'POST /alert-events/test' ([bool]($resp.code -eq 0)) ("code=" + $resp.code)
  Assert-Test 'manual trigger returns event id' ([bool]($resp.data.id -gt 0)) ("id=" + $resp.data.id)
  Start-Sleep -Seconds 1
  $afterCount = (Get-Count (Get-Api '/alert-events').data)
  Assert-Test 'event count +1 after manual trigger' ([bool]($afterCount -eq $beforeCount + 1)) ("before=" + $beforeCount + ", after=" + $afterCount)

  $newEventId = $resp.data.id
  $newEvent = (Get-Api ('/alert-events/' + $newEventId)).data
  Assert-Test 'new event status = PENDING' ([bool]($newEvent.eventStatus -eq 'PENDING')) ("status=" + $newEvent.eventStatus)
  Assert-Test 'new event ai_summary_status = PENDING' ([bool]($newEvent.aiSummaryStatus -eq 'PENDING')) ("aiStatus=" + $newEvent.aiSummaryStatus)

  # ============================================================
  # Workflow 2: event state machine
  # ============================================================
  Write-Host ''
  Write-Host '== Workflow 2: Event state machine =============' -ForegroundColor Cyan

  $r = Post-Api '/alert-events/action' @{
    eventId = $newEventId
    actionType = 'CONFIRM'
    operatorName = 'test-suite'
    actionComment = 'auto confirm'
  }
  Assert-Test 'CONFIRM action' ([bool]($r.code -eq 0)) ("code=" + $r.code)
  $afterConfirm = (Get-Api ('/alert-events/' + $newEventId)).data
  Assert-Test 'status -> CONFIRMED' ([bool]($afterConfirm.eventStatus -eq 'CONFIRMED')) ("status=" + $afterConfirm.eventStatus)
  Assert-Test 'confirmedAt set' ([bool]($afterConfirm.confirmedAt)) ("at=" + $afterConfirm.confirmedAt)

  $r = Post-Api '/alert-events/action' @{
    eventId = $newEventId
    actionType = 'RECOVER'
    operatorName = 'test-suite'
    actionComment = 'auto recover'
  }
  Assert-Test 'RECOVER action' ([bool]($r.code -eq 0)) ("code=" + $r.code)
  $afterRec = (Get-Api ('/alert-events/' + $newEventId)).data
  Assert-Test 'status -> RECOVERED' ([bool]($afterRec.eventStatus -eq 'RECOVERED')) ("status=" + $afterRec.eventStatus)
  Assert-Test 'recoveredAt set' ([bool]($afterRec.recoveredAt)) ("at=" + $afterRec.recoveredAt)

  $r = Post-Api '/alert-events/action' @{
    eventId = $newEventId
    actionType = 'CLOSE'
    operatorName = 'test-suite'
  }
  Assert-Test 'CLOSE action' ([bool]($r.code -eq 0)) ("code=" + $r.code)
  $afterClose = (Get-Api ('/alert-events/' + $newEventId)).data
  Assert-Test 'status -> CLOSED' ([bool]($afterClose.eventStatus -eq 'CLOSED')) ("status=" + $afterClose.eventStatus)
  Assert-Test 'closedAt set' ([bool]($afterClose.closedAt)) ("at=" + $afterClose.closedAt)

  Assert-Test 'handle logs >= 3' ([bool]((Get-Count $afterClose.handleLogs) -ge 3)) ("n=" + (Get-Count $afterClose.handleLogs))
  Assert-Test 'notify logs >= 1' ([bool]((Get-Count $afterClose.notifyLogs) -ge 1)) ("n=" + (Get-Count $afterClose.notifyLogs))
}

# ============================================================
# Workflow 3: Story Mode
# ============================================================
Write-Host ''
Write-Host '== Workflow 3: Story mode ======================' -ForegroundColor Cyan

$numRule = $rules | Where-Object { $_.objectType -in @('SERVER','DATABASE') -and $_.status -eq 'ENABLED' } | Select-Object -First 1
if ($numRule) {
  $rd = (Get-Api ('/alert-rules/' + $numRule.id)).data
  $cond = $rd.conditions | Where-Object { $_.compareOp -in @('GT','GE','LT','LE') } | Select-Object -First 1
  $obj2 = $rd.objects | Select-Object -First 1
  if ($cond -and $obj2) {
    $r = Form-Api '/simulator/force-story' @{ objectId = $obj2.id; metricCode = $cond.metricCode }
    Assert-Test 'POST /simulator/force-story' ([bool]($r.code -eq 0)) ("code=" + $r.code)
  }
}

# ============================================================
# Workflow 4: AI command
# ============================================================
Write-Host ''
Write-Host '== Workflow 4: AI command (LLM, may be slow) ===' -ForegroundColor Cyan

$promptText = 'now which objects are alerting'
$cmd = Post-Api '/ai/command' @{ prompt = $promptText } 90
if ($cmd.code -eq 0) {
  Assert-Test 'POST /ai/command' $true ("intent=" + $cmd.data.intent)
} else {
  Assert-Test 'POST /ai/command' $false ("code=" + $cmd.code)
}

# ============================================================
# Workflow 5: AI stats reflect new call
# ============================================================
Write-Host ''
Write-Host '== Workflow 5: AI stats reflect new calls ======' -ForegroundColor Cyan

$ovAfter = (Get-Api '/ai-stats/overview').data
Assert-Test 'today calls > 0 after AI command' ([bool]($ovAfter.todayCallTotal -gt 0)) ("today=" + $ovAfter.todayCallTotal)
$chatScene = $ovAfter.sceneDistribution | Where-Object { $_.scene -eq 'CHAT' }
if ($chatScene) {
  Assert-Test 'scene distribution includes CHAT' ([bool]($chatScene.callCount -gt 0)) ("chatCalls=" + $chatScene.callCount)
}

# ============================================================
# Summary
# ============================================================
Write-Host ''
Write-Host '========================================' -ForegroundColor Yellow
$total = $global:passCount + $global:failCount
Write-Host ("Workflow tests: " + $total + " | PASS: " + $global:passCount + " | FAIL: " + $global:failCount) -ForegroundColor Yellow
Write-Host '========================================' -ForegroundColor Yellow

if ($global:failCount -gt 0) {
  Write-Host ''
  Write-Host 'FAILED cases:' -ForegroundColor Red
  $global:results | Where-Object { $_.Status -eq 'FAIL' } | ForEach-Object {
    Write-Host ('  - ' + $_.Name + ' :: ' + $_.Detail) -ForegroundColor Red
  }
}
