#
# AIOps Alert system end-to-end smoke test (PowerShell 5.x compatible)
# Outputs PASS/FAIL per case with summary at the end
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

function Get-Api {
  param([string]$path)
  try {
    $r = Invoke-WebRequest -Uri ($BASE + $path) -UseBasicParsing -TimeoutSec 30
    return ($r.Content | ConvertFrom-Json)
  } catch {
    return [pscustomobject]@{ code = -1; message = $_.Exception.Message; data = $null }
  }
}

function Get-Count {
  param($v)
  if ($null -eq $v) { return 0 }
  return @($v).Count
}

# ============================================================
# Domain 1: Catalog dictionaries
# ============================================================
Write-Host ''
Write-Host '== Domain 1: Catalog ===========================' -ForegroundColor Cyan

$objTypes = Get-Api '/catalog/object-types'
Assert-Test 'GET /catalog/object-types' ([bool]($objTypes.code -eq 0 -and (Get-Count $objTypes.data) -eq 4)) ("count=" + (Get-Count $objTypes.data))

$chTypes = Get-Api '/catalog/channel-types'
Assert-Test 'GET /catalog/channel-types' ([bool]($chTypes.code -eq 0 -and (Get-Count $chTypes.data) -eq 3)) ("count=" + (Get-Count $chTypes.data))

$levels = Get-Api '/catalog/alert-levels'
Assert-Test 'GET /catalog/alert-levels' ([bool]($levels.code -eq 0 -and (Get-Count $levels.data) -eq 4)) ("count=" + (Get-Count $levels.data))

$ops = Get-Api '/catalog/compare-ops'
Assert-Test 'GET /catalog/compare-ops' ([bool]($ops.code -eq 0 -and (Get-Count $ops.data) -ge 6)) ("count=" + (Get-Count $ops.data))

$metrics = Get-Api '/catalog/metrics'
$metricTypes = if ($metrics.data) { ($metrics.data.PSObject.Properties | Measure-Object).Count } else { 0 }
Assert-Test 'GET /catalog/metrics 4 object types' ([bool]($metrics.code -eq 0 -and $metricTypes -ge 4)) ("types=" + $metricTypes)

# ============================================================
# Domain 2: Dashboard
# ============================================================
Write-Host ''
Write-Host '== Domain 2: Dashboard =========================' -ForegroundColor Cyan

$db = Get-Api '/dashboard'
Assert-Test 'GET /dashboard ok' ([bool]($db.code -eq 0)) ("code=" + $db.code)
Assert-Test 'dashboard objectTotal>=7' ([bool]($db.data.objectTotal -ge 7)) ("n=" + $db.data.objectTotal)
Assert-Test 'dashboard ruleTotal>=6' ([bool]($db.data.ruleTotal -ge 6)) ("n=" + $db.data.ruleTotal)
Assert-Test 'dashboard channelTotal>=3' ([bool]($db.data.channelTotal -ge 3)) ("n=" + $db.data.channelTotal)
Assert-Test 'dashboard sevenDayTrend.length=7' ([bool]((Get-Count $db.data.sevenDayTrend) -eq 7)) ("n=" + (Get-Count $db.data.sevenDayTrend))

$nonZeroDays = @($db.data.sevenDayTrend | Where-Object { $_.total -gt 0 }).Count
Assert-Test 'P0-3: 7-day trend non-zero days >= 4' ([bool]($nonZeroDays -ge 4)) ("nonZeroDays=" + $nonZeroDays)

$totalSum = ($db.data.sevenDayTrend | Measure-Object total -Sum).Sum
if ($null -eq $totalSum) { $totalSum = 0 }
Assert-Test 'P0-3: 7-day total events >= 12' ([bool]($totalSum -ge 12)) ("totalSum=" + $totalSum)

Assert-Test 'dashboard recentEvents non-empty' ([bool]((Get-Count $db.data.recentEvents) -gt 0)) ("n=" + (Get-Count $db.data.recentEvents))
Assert-Test 'dashboard objectTypeDistribution=4' ([bool]((Get-Count $db.data.objectTypeDistribution) -eq 4)) ("n=" + (Get-Count $db.data.objectTypeDistribution))

# ============================================================
# Domain 3: CRUD endpoints
# ============================================================
Write-Host ''
Write-Host '== Domain 3: CRUD ==============================' -ForegroundColor Cyan

$objs = Get-Api '/monitor-objects'
Assert-Test 'GET /monitor-objects' ([bool]($objs.code -eq 0 -and (Get-Count $objs.data) -ge 7)) ("n=" + (Get-Count $objs.data))

$objStats = Get-Api '/monitor-objects/stats'
Assert-Test 'GET /monitor-objects/stats' ([bool]($objStats.code -eq 0 -and $objStats.data.total -ge 7)) ("total=" + $objStats.data.total)

$chs = Get-Api '/alert-channels'
Assert-Test 'GET /alert-channels' ([bool]($chs.code -eq 0 -and (Get-Count $chs.data) -ge 3)) ("n=" + (Get-Count $chs.data))

$rules = Get-Api '/alert-rules'
Assert-Test 'GET /alert-rules' ([bool]($rules.code -eq 0 -and (Get-Count $rules.data) -ge 6)) ("n=" + (Get-Count $rules.data))

$ruleStats = Get-Api '/alert-rules/stats'
Assert-Test 'GET /alert-rules/stats' ([bool]($ruleStats.code -eq 0 -and $ruleStats.data.total -ge 6)) ("total=" + $ruleStats.data.total)

if ((Get-Count $rules.data) -gt 0) {
  $firstRuleId = $rules.data[0].id
  $ruleDetail = Get-Api ("/alert-rules/" + $firstRuleId)
  Assert-Test 'GET /alert-rules/{id} detail' ([bool]($ruleDetail.code -eq 0 -and $ruleDetail.data.id -eq $firstRuleId)) ("id=" + $ruleDetail.data.id)
  Assert-Test 'rule has conditions' ([bool]($ruleDetail.data.conditions -and (Get-Count $ruleDetail.data.conditions) -gt 0)) ("conds=" + (Get-Count $ruleDetail.data.conditions))
  Assert-Test 'rule has objects rel' ([bool]($ruleDetail.data.objects -and (Get-Count $ruleDetail.data.objects) -gt 0)) ("objs=" + (Get-Count $ruleDetail.data.objects))
  Assert-Test 'rule has channels rel' ([bool]($ruleDetail.data.channels -and (Get-Count $ruleDetail.data.channels) -gt 0)) ("chs=" + (Get-Count $ruleDetail.data.channels))
}

# ============================================================
# Domain 4: Events / Incidents
# ============================================================
Write-Host ''
Write-Host '== Domain 4: Events / Incidents ================' -ForegroundColor Cyan

$events = Get-Api '/alert-events'
Assert-Test 'GET /alert-events' ([bool]($events.code -eq 0 -and (Get-Count $events.data) -ge 12)) ("n=" + (Get-Count $events.data))

# Property 1: distinct day count
$datesSet = @{}
foreach ($e in $events.data) {
  if ($e.firstTriggeredAt) {
    $d = $e.firstTriggeredAt.ToString().Substring(0, 10)
    $datesSet[$d] = $true
  }
}
Assert-Test 'P1: events span >= 7 distinct days' ([bool]($datesSet.Keys.Count -ge 7)) ("distinctDays=" + $datesSet.Keys.Count)

# Property 2: pre-generated SUCCESS summaries
$successEvents = @($events.data | Where-Object { $_.aiSummaryStatus -eq 'SUCCESS' })
Assert-Test 'P2: SUCCESS summaries >= 2' ([bool]($successEvents.Count -ge 2)) ("successN=" + $successEvents.Count)

$validSummaryCount = 0
foreach ($e in $successEvents) {
  try {
    $sum = $e.aiSummary | ConvertFrom-Json
    if ($sum.what -and $sum.impact -and $sum.causes -and (Get-Count $sum.causes) -ge 1 -and $sum.actions -and (Get-Count $sum.actions) -ge 1) {
      $validSummaryCount++
    }
  } catch { }
}
Assert-Test 'P2: all SUCCESS summaries are valid JSON with 4 fields' ([bool]($validSummaryCount -eq $successEvents.Count -and $successEvents.Count -gt 0)) ("valid=" + $validSummaryCount + "/" + $successEvents.Count)

# Property 3: pending residual
$pendingCount = @($events.data | Where-Object { $_.aiSummaryStatus -eq 'PENDING' -or -not $_.aiSummaryStatus }).Count
Assert-Test 'P3: PENDING residual >= 1' ([bool]($pendingCount -ge 1)) ("pending=" + $pendingCount)

# Status diversity
$statusSet = @{}
foreach ($e in $events.data) { if ($e.eventStatus) { $statusSet[$e.eventStatus] = $true } }
Assert-Test 'Req 1.5: status coverage >= 3' ([bool]($statusSet.Keys.Count -ge 3)) ("states=" + ($statusSet.Keys -join ','))
Assert-Test 'Req 1.5: at least 1 RECOVERED' ([bool]($statusSet.ContainsKey('RECOVERED'))) ("hasRecovered=" + $statusSet.ContainsKey('RECOVERED'))

# Level diversity
$levelSet = @{}
foreach ($e in $events.data) { if ($e.alertLevel) { $levelSet[$e.alertLevel] = $true } }
Assert-Test 'Req 1.3: level coverage >= 2' ([bool]($levelSet.Keys.Count -ge 2)) ("levels=" + ($levelSet.Keys -join ','))

# Object type diversity
$typeSet = @{}
foreach ($e in $events.data) { if ($e.objectType) { $typeSet[$e.objectType] = $true } }
Assert-Test 'Req 1.4: object type coverage >= 3' ([bool]($typeSet.Keys.Count -ge 3)) ("types=" + ($typeSet.Keys -join ','))

if ((Get-Count $events.data) -gt 0) {
  $firstEventId = $events.data[0].id
  $evDetail = Get-Api ("/alert-events/" + $firstEventId)
  Assert-Test 'GET /alert-events/{id} detail' ([bool]($evDetail.code -eq 0)) ("id=" + $evDetail.data.id)
}

$incidents = Get-Api '/alert-incidents'
Assert-Test 'GET /alert-incidents' ([bool]($incidents.code -eq 0)) ("n=" + (Get-Count $incidents.data))

# ============================================================
# Domain 5: AI Stats (P1-6)
# ============================================================
Write-Host ''
Write-Host '== Domain 5: AI Stats ==========================' -ForegroundColor Cyan

$ov = Get-Api '/ai-stats/overview'
Assert-Test 'GET /ai-stats/overview' ([bool]($ov.code -eq 0)) ("code=" + $ov.code)
Assert-Test 'overview hero 3 numbers' ([bool]($null -ne $ov.data.todayCallTotal -and $null -ne $ov.data.todayTokenTotal -and $null -ne $ov.data.todaySuccessRate)) ''
Assert-Test 'overview sevenDayTrend.length=7' ([bool]((Get-Count $ov.data.sevenDayTrend) -eq 7)) ("n=" + (Get-Count $ov.data.sevenDayTrend))
Assert-Test 'overview costCurrency=CNY' ([bool]($ov.data.costCurrency -eq 'CNY')) ("cur=" + $ov.data.costCurrency)

# Property 9: scene normalization total
$sceneSum = ($ov.data.sceneDistribution | Measure-Object callCount -Sum).Sum
if ($null -eq $sceneSum) { $sceneSum = 0 }
Assert-Test 'P9: scene callCount sum equals todayCallTotal' ([bool]($sceneSum -eq $ov.data.todayCallTotal)) ("sceneSum=" + $sceneSum + ", today=" + $ov.data.todayCallTotal)

# Property 8: cost
$todayCost = [decimal]$ov.data.todayCost
$monthCost = [decimal]$ov.data.monthCost
Assert-Test 'P8: cost >= 0' ([bool]($todayCost -ge 0 -and $monthCost -ge 0)) ("today=" + $todayCost + ", month=" + $monthCost)
Assert-Test 'P8: monthCost >= todayCost' ([bool]($monthCost -ge $todayCost)) ("month=" + $monthCost + ", today=" + $todayCost)

$slow = Get-Api ("/ai-stats/slow?days=7" + $AMP + "limit=10")
Assert-Test 'GET /ai-stats/slow' ([bool]($slow.code -eq 0)) ("n=" + (Get-Count $slow.data))
Assert-Test 'slow.length <= 10' ([bool]((Get-Count $slow.data) -le 10)) ("n=" + (Get-Count $slow.data))

if ((Get-Count $slow.data) -gt 0) {
  $hasPayload = $false
  foreach ($s in $slow.data) {
    if ($s.requestPayload -or $s.responsePayload -or $s.reasoningContent) { $hasPayload = $true; break }
  }
  Assert-Test 'P12: /slow strips large payload fields' ([bool](-not $hasPayload)) ("stripped=" + (-not $hasPayload))

  $logId = $slow.data[0].id
  $logDetail = Get-Api ("/ai-stats/logs/" + $logId)
  Assert-Test 'GET /ai-stats/logs/{id}' ([bool]($logDetail.code -eq 0)) ("code=" + $logDetail.code)
  $hasPayloadInDetail = ($null -ne $logDetail.data.requestPayload) -or ($null -ne $logDetail.data.responsePayload) -or ($null -ne $logDetail.data.reasoningContent)
  Assert-Test 'P12: /logs/{id} returns large payload fields' ([bool]$hasPayloadInDetail) ("hasPayload=" + $hasPayloadInDetail)
}

$logs = Get-Api ("/ai-stats/logs?page=1" + $AMP + "size=10")
Assert-Test 'GET /ai-stats/logs paged' ([bool]($logs.code -eq 0)) ("code=" + $logs.code)
Assert-Test 'logs.records.length <= 10' ([bool]((Get-Count $logs.data.records) -le 10)) ("n=" + (Get-Count $logs.data.records))
Assert-Test 'logs.total >= records.length' ([bool]($logs.data.total -ge (Get-Count $logs.data.records))) ("total=" + $logs.data.total)

$logsNl = Get-Api ("/ai-stats/logs?scene=NL2RULE" + $AMP + "page=1" + $AMP + "size=20")
Assert-Test 'GET /ai-stats/logs?scene=NL2RULE' ([bool]($logsNl.code -eq 0)) ("n=" + (Get-Count $logsNl.data.records))
$nonNl = @($logsNl.data.records | Where-Object { $_.scene -ne 'NL2RULE' }).Count
Assert-Test 'scene filter purity' ([bool]($nonNl -eq 0)) ("nonMatch=" + $nonNl)

$logsFail = Get-Api ("/ai-stats/logs?status=FAILED" + $AMP + "page=1" + $AMP + "size=20")
Assert-Test 'GET /ai-stats/logs?status=FAILED' ([bool]($logsFail.code -eq 0)) ("n=" + (Get-Count $logsFail.data.records))

$notFound = Get-Api '/ai-stats/logs/99999999'
Assert-Test 'GET /ai-stats/logs/{not-exist} returns error' ([bool]($notFound.code -ne 0)) ("code=" + $notFound.code)

# ============================================================
# Domain 6: Threshold Recommend
# ============================================================
Write-Host ''
Write-Host '== Domain 6: Threshold Recommend ===============' -ForegroundColor Cyan

$numericRule = $rules.data | Where-Object { $_.objectType -in @('SERVER','DATABASE') } | Select-Object -First 1
if ($numericRule) {
  $ruleDetail = Get-Api ("/alert-rules/" + $numericRule.id)
  $cond = $ruleDetail.data.conditions | Where-Object { $_.compareOp -in @('GT','GE','LT','LE') } | Select-Object -First 1
  $obj = $ruleDetail.data.objects | Select-Object -First 1
  if ($cond -and $obj) {
    $reco = Get-Api ("/ai/threshold?objectType=" + $numericRule.objectType + $AMP + "metricCode=" + $cond.metricCode + $AMP + "objectId=" + $obj.id)
    Assert-Test 'GET /ai/threshold' ([bool]($reco.code -eq 0)) ("code=" + $reco.code)
    Assert-Test 'P4: source=HISTORY' ([bool]($reco.data.source -eq 'HISTORY')) ("source=" + $reco.data.source + "; samples=" + $reco.data.samples)
    Assert-Test 'P4: P95 > P50' ([bool]([double]$reco.data.p95 -gt [double]$reco.data.p50)) ("p50=" + $reco.data.p50 + ", p95=" + $reco.data.p95)
    Assert-Test 'P4: samples >= 100' ([bool]($reco.data.samples -ge 100)) ("samples=" + $reco.data.samples)
    Assert-Test 'recommendations >= 3 levels' ([bool]((Get-Count $reco.data.recommendations) -ge 3)) ("n=" + (Get-Count $reco.data.recommendations))
  } else {
    Assert-Test 'threshold prereq data' $false 'no numeric condition / no object'
  }
}

# ============================================================
# Domain 7: SSE & LLM
# ============================================================
Write-Host ''
Write-Host '== Domain 7: SSE & LLM =========================' -ForegroundColor Cyan

try {
  $req = [System.Net.HttpWebRequest]::Create($BASE + '/stream/alerts')
  $req.Timeout = 3000
  $req.ReadWriteTimeout = 3000
  $resp = $req.GetResponse()
  Assert-Test 'GET /stream/alerts 200' ([bool]($resp.StatusCode -eq 'OK')) ("status=" + $resp.StatusCode)
  Assert-Test 'SSE Content-Type' ([bool]($resp.ContentType -match 'event-stream')) ("ct=" + $resp.ContentType)
  $resp.Close()
} catch {
  Assert-Test 'GET /stream/alerts 200' $false ("err: " + $_.Exception.Message.Split([Environment]::NewLine)[0])
}

$llm = Get-Api '/llm-configs'
Assert-Test 'GET /llm-configs' ([bool]($llm.code -eq 0)) ("n=" + (Get-Count $llm.data))

$nlAv = Get-Api '/ai/rules/availability'
Assert-Test 'GET /ai/rules/availability' ([bool]($nlAv.code -eq 0)) ("available=" + $nlAv.data)

# ============================================================
# Summary
# ============================================================
Write-Host ''
Write-Host '========================================' -ForegroundColor Yellow
$total = $global:passCount + $global:failCount
Write-Host ("Total: " + $total + " | PASS: " + $global:passCount + " | FAIL: " + $global:failCount) -ForegroundColor Yellow
Write-Host '========================================' -ForegroundColor Yellow

if ($global:failCount -gt 0) {
  Write-Host ''
  Write-Host 'FAILED cases:' -ForegroundColor Red
  $global:results | Where-Object { $_.Status -eq 'FAIL' } | ForEach-Object {
    Write-Host ('  - ' + $_.Name + ' :: ' + $_.Detail) -ForegroundColor Red
  }
}


# ============================================================
# Domain 8: Daily Brief (P2-9)
# ============================================================
Write-Host ''
Write-Host '== Domain 8: Daily Brief =======================' -ForegroundColor Cyan

$brief = Get-Api '/daily-brief'
Assert-Test 'GET /daily-brief' ([bool]($brief.code -eq 0)) ("code=" + $brief.code)
Assert-Test 'brief.status one of expected' ([bool]($brief.data.status -in @('SUCCESS','FALLBACK','FAILED'))) ("status=" + $brief.data.status)
Assert-Test 'brief.coverageDate yyyy-MM-dd' ([bool]($brief.data.coverageDate -match '^\d{4}-\d{2}-\d{2}$')) ("d=" + $brief.data.coverageDate)
Assert-Test 'brief.narrative non-empty' ([bool]($brief.data.narrative -and $brief.data.narrative.Length -gt 5)) ("len=" + $brief.data.narrative.Length)
Assert-Test 'brief.snapshot present' ([bool]($null -ne $brief.data.snapshot)) ''
Assert-Test 'brief.snapshot.totalEvents >= 0' ([bool]($brief.data.snapshot.totalEvents -ge 0)) ("total=" + $brief.data.snapshot.totalEvents)
Assert-Test 'brief.highlights array' ([bool]($null -ne $brief.data.highlights)) ("n=" + (Get-Count $brief.data.highlights))
