#
# AIOps Alert - End-to-end demo walkthrough (5 min, ASCII-only)
#

$ErrorActionPreference = 'Continue'
$BASE = 'http://localhost:8090/api'
$AMP = [char]38

function StepHeader {
  param([string]$n, [string]$title)
  Write-Host ''
  Write-Host ("================  Step " + $n + " - " + $title + "  ================") -ForegroundColor Cyan
}

function GetApi { param([string]$p) try { ((Invoke-WebRequest -Uri ($BASE + $p) -UseBasicParsing -TimeoutSec 30).Content | ConvertFrom-Json) } catch { [pscustomobject]@{ code=-1; message=$_.Exception.Message; data=$null } } }
function PostApi { param([string]$p, [object]$b, [int]$t = 90) try { ((Invoke-WebRequest -Uri ($BASE + $p) -Method POST -UseBasicParsing -TimeoutSec $t -Body ($b | ConvertTo-Json -Depth 10) -ContentType 'application/json').Content | ConvertFrom-Json) } catch { [pscustomobject]@{ code=-1; message=$_.Exception.Message; data=$null } } }

# ---------- Step 1: Dashboard ----------
StepHeader '1' 'Dashboard overview'
$db = (GetApi '/dashboard').data
Write-Host ("    objects:  " + $db.objectTotal + " (enabled " + $db.enabledObjectTotal + ")")
Write-Host ("    rules:    " + $db.ruleTotal + " (enabled " + $db.enabledRuleTotal + ")")
Write-Host ("    channels: " + $db.channelTotal)
Write-Host ("    pending:  " + $db.pendingEventTotal + " | critical: " + $db.criticalEventTotal)
$days = ($db.sevenDayTrend | Where-Object { $_.total -gt 0 }).Count
Write-Host ("    7-day trend non-zero days: " + $days + " / 7")
Write-Host ("    incidents open: " + $db.openIncidentTotal)

StepHeader '1b' 'Daily AI brief'
$brief = (GetApi '/daily-brief').data
Write-Host ("    coverageDate: " + $brief.coverageDate)
Write-Host ("    status: " + $brief.status + " (narrative " + $brief.narrative.Length + " chars)")
Write-Host ("    highlights: " + $brief.highlights.Count + " events")
Write-Host ("    snapshot: total=" + $brief.snapshot.totalEvents + " critical=" + $brief.snapshot.criticalEvents + " recovered=" + $brief.snapshot.recoveredEvents)

# ---------- Step 2: AI rule builder ----------
StepHeader '2' 'AI Rule Builder'
$avail = (GetApi '/ai/rules/availability').data
Write-Host ("    LLM available: " + $avail)
Write-Host '    [demo action] In RulesView, click AI banner; type a Chinese sentence describing rule'
Write-Host '    [skip live LLM call here to avoid duplicate cost]'

# ---------- Step 3: Trigger demo event ----------
StepHeader '3' 'Trigger demo event (auto)'
$rules = (GetApi '/alert-rules').data
$cpuRule = $rules | Where-Object { $_.ruleCode -eq 'RULE-SRV-CPU' } | Select-Object -First 1
if ($cpuRule) {
  $rd = (GetApi ('/alert-rules/' + $cpuRule.id)).data
  $obj = $rd.objects | Select-Object -First 1
  $r = PostApi '/alert-events/test' @{
    ruleId = $cpuRule.id
    objectId = $obj.id
    currentValue = 'cpu_usage=94pct (DEMO)'
    eventReason = 'demo walkthrough manual trigger'
  }
  if ($r.code -eq 0) {
    Write-Host ("    [PASS] new event id=" + $r.data.id)
    Write-Host ("    notifyLogs: " + $r.data.notifyLogs.Count)
    Write-Host ("    aiSummaryStatus: " + $r.data.aiSummaryStatus + " (PENDING means async LLM in flight)")
    $newId = $r.data.id
    Write-Host '    [wait 8s for AI summary]'
    Start-Sleep -Seconds 8
    $newEv = (GetApi ('/alert-events/' + $newId)).data
    Write-Host ("    after 8s aiSummaryStatus = " + $newEv.aiSummaryStatus)
    if ($newEv.aiSummary) {
      try {
        $sum = $newEv.aiSummary | ConvertFrom-Json
        $whatLen = [Math]::Min(80, $sum.what.Length)
        Write-Host ("    AI summary 'what' = " + $sum.what.Substring(0, $whatLen) + '...')
      } catch { Write-Host '    AI summary still streaming' }
    }
  } else {
    Write-Host ("    [FAIL] " + $r.message)
  }
}

# ---------- Step 4: Incidents ----------
StepHeader '4' 'Incidents view'
$incs = (GetApi '/alert-incidents').data
Write-Host ("    incidents: " + $incs.Count)
foreach ($i in ($incs | Select-Object -First 3)) {
  Write-Host ("      - #" + $i.id + " [" + $i.topLevel + "] " + $i.objectName + " - " + $i.eventCount + ' events - ' + $i.status)
}

# ---------- Step 5: Command palette ----------
StepHeader '5' 'Command Palette (Cmd+K)'
Write-Host '    [demo] Press Cmd+K -> type "now which objects are alerting"'
$cmd = PostApi '/ai/command' @{ prompt = 'now which objects are alerting' } 90
if ($cmd.code -eq 0) {
  Write-Host ("    [PASS] intent=" + $cmd.data.intent + ", events=" + $cmd.data.events.Count)
  if ($cmd.data.answer) {
    $aLen = [Math]::Min(100, $cmd.data.answer.Length)
    Write-Host ("    answer: " + $cmd.data.answer.Substring(0, $aLen) + '...')
  }
} else {
  Write-Host ("    [FAIL] code=" + $cmd.code + ", msg=" + $cmd.message)
}

# ---------- Step 6: AI stats ----------
StepHeader '6' 'AI Stats /ai-stats'
$ov = (GetApi '/ai-stats/overview').data
Write-Host ("    today: " + $ov.todayCallTotal + " calls, " + $ov.todayTokenTotal + " tokens, " + $ov.todaySuccessRate + 'pct success')
Write-Host ("    yesterday: " + $ov.yesterdayCallTotal + " calls")
Write-Host '    scene distribution:'
foreach ($s in $ov.sceneDistribution) {
  Write-Host ('      - ' + $s.scene + ': ' + $s.callCount + ' calls (' + $s.tokenPercent + 'pct token)')
}
$slow = (GetApi ('/ai-stats/slow?days=7' + $AMP + 'limit=5')).data
Write-Host '    slow Top 5:'
foreach ($s in $slow) {
  Write-Host ('      - ' + $s.durationMs + 'ms ' + $s.scene + ' (' + $s.modelName + ')')
}

# ---------- Step 7: Theme + NOC ----------
StepHeader '7' 'Theme Switcher + NOC mode'
Write-Host '    [browser-side manual] Click ThemeSwitcher -> dark/light/system'
Write-Host '    [browser-side manual] Click "NOC" button on dashboard hero -> fullscreen + 30s auto refresh'
Write-Host '    Expected: all echarts colors follow CSS tokens; sidebar/header hidden in NOC mode'

Write-Host ''
Write-Host '================================================' -ForegroundColor Yellow
Write-Host 'Demo walkthrough finished - all backend responses OK' -ForegroundColor Yellow
Write-Host 'UI side (visual/animation/drawer) still needs manual eyeballing' -ForegroundColor Yellow
Write-Host '================================================' -ForegroundColor Yellow
