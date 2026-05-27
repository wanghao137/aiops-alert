#
# AIOps Alert - UI static test
# Check route, sidebar, asset chunks via running vite dev server
#

$ErrorActionPreference = 'Continue'
$VITE = 'http://localhost:5173'

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

function Get-Url {
  param([string]$url, [int]$timeout = 5)
  try {
    $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $timeout
    return [pscustomobject]@{ status = $r.StatusCode; body = $r.Content; ct = $r.Headers['Content-Type'] }
  } catch {
    return [pscustomobject]@{ status = $_.Exception.Response.StatusCode; body = $null; ct = '' }
  }
}

# ============================================================
# UI 1: Vite dev server
# ============================================================
Write-Host ''
Write-Host '== UI 1: vite dev server =======================' -ForegroundColor Cyan

$root = Get-Url $VITE
Assert-Test 'GET / 200' ([bool]($root.status -eq 'OK' -or $root.status -eq 200)) ("status=" + $root.status)
Assert-Test 'index.html has Vue app shell' ([bool]($root.body -match '<div id="app"')) ''
Assert-Test 'main.ts loaded' ([bool]($root.body -match '/src/main\.ts')) ''
Assert-Test 'index.html has favicon' ([bool]($root.body -match '/favicon\.svg')) ''
Assert-Test 'AIOps title in head' ([bool]($root.body -match 'AIOps Alert')) ''

# ============================================================
# UI 2: route module sources reachable
# ============================================================
Write-Host ''
Write-Host '== UI 2: route module sources ==================' -ForegroundColor Cyan

$routes = @(
  @{ Name = 'DashboardView'; Path = '/src/views/DashboardView.vue' },
  @{ Name = 'EventsView';    Path = '/src/views/EventsView.vue' },
  @{ Name = 'IncidentsView'; Path = '/src/views/IncidentsView.vue' },
  @{ Name = 'RulesView';     Path = '/src/views/RulesView.vue' },
  @{ Name = 'ObjectsView';   Path = '/src/views/ObjectsView.vue' },
  @{ Name = 'ChannelsView';  Path = '/src/views/ChannelsView.vue' },
  @{ Name = 'SettingsView';  Path = '/src/views/SettingsView.vue' },
  @{ Name = 'AiStatsView';   Path = '/src/views/AiStatsView.vue' },
  @{ Name = 'ErrorPage';     Path = '/src/views/ErrorPage.vue' }
)

foreach ($r in $routes) {
  $resp = Get-Url ($VITE + $r.Path)
  Assert-Test ($r.Name + ' module reachable') ([bool]($resp.status -eq 'OK' -or $resp.status -eq 200)) ("status=" + $resp.status)
}

# ============================================================
# UI 3: new components reachable
# ============================================================
Write-Host ''
Write-Host '== UI 3: new components reachable ==============' -ForegroundColor Cyan

$comps = @(
  @{ Name = 'SkeletonList';   Path = '/src/components/common/SkeletonList.vue' },
  @{ Name = 'NetworkBanner';  Path = '/src/components/layout/NetworkBanner.vue' },
  @{ Name = 'AiSummaryCard';  Path = '/src/components/alert/AiSummaryCard.vue' },
  @{ Name = 'AppSidebar';     Path = '/src/components/layout/AppSidebar.vue' },
  @{ Name = 'CommandPalette'; Path = '/src/components/command/CommandPalette.vue' },
  @{ Name = 'ThinkingPanel';  Path = '/src/components/ai/ThinkingPanel.vue' }
)

foreach ($c in $comps) {
  $resp = Get-Url ($VITE + $c.Path)
  Assert-Test ($c.Name + ' reachable') ([bool]($resp.status -eq 'OK' -or $resp.status -eq 200)) ("status=" + $resp.status)
}

# ============================================================
# UI 4: API & composables
# ============================================================
Write-Host ''
Write-Host '== UI 4: api & composables =====================' -ForegroundColor Cyan

$libs = @(
  @{ Name = 'aiStats.ts';     Path = '/src/api/aiStats.ts' },
  @{ Name = 'http.ts';        Path = '/src/api/http.ts' },
  @{ Name = 'useSse.ts';      Path = '/src/composables/useSse.ts' },
  @{ Name = 'useHttpHealth';  Path = '/src/composables/useHttpHealth.ts' },
  @{ Name = 'theme.ts store'; Path = '/src/stores/theme.ts' },
  @{ Name = 'router';         Path = '/src/router/index.ts' }
)

foreach ($l in $libs) {
  $resp = Get-Url ($VITE + $l.Path)
  Assert-Test ($l.Name + ' reachable') ([bool]($resp.status -eq 'OK' -or $resp.status -eq 200)) ("status=" + $resp.status)
}

# ============================================================
# UI 5: route content sanity
# ============================================================
Write-Host ''
Write-Host '== UI 5: AiStatsView template sanity ==========' -ForegroundColor Cyan

$aiv = (Get-Url ($VITE + '/src/views/AiStatsView.vue')).body
Assert-Test 'AiStatsView has hero section'        ([bool]($aiv -match 'hero')) ''
Assert-Test 'AiStatsView uses readToken'           ([bool]($aiv -match 'readToken')) ''
Assert-Test 'AiStatsView has scene chart ref'      ([bool]($aiv -match 'sceneRef')) ''
Assert-Test 'AiStatsView has trend chart ref'      ([bool]($aiv -match 'trendRef')) ''
Assert-Test 'AiStatsView calls getAiStatsOverview' ([bool]($aiv -match 'getAiStatsOverview')) ''
Assert-Test 'AiStatsView calls listSlowAiCalls'    ([bool]($aiv -match 'listSlowAiCalls')) ''
Assert-Test 'AiStatsView calls listAiCallLogs'     ([bool]($aiv -match 'listAiCallLogs')) ''
Assert-Test 'AiStatsView uses SkeletonList'        ([bool]($aiv -match 'SkeletonList')) ''
Assert-Test 'AiStatsView uses ErrorPage'           ([bool]($aiv -match 'ErrorPage')) ''
Assert-Test 'AiStatsView has theme.isDark watch'   ([bool]($aiv -match 'theme.isDark')) ''
Assert-Test 'AiStatsView uses CSS tokens'          ([bool]($aiv -match '--accent|--bg-elev|--text-')) ''

$aiv_tokenScan = $aiv -replace "readToken\((['""])([^'""]+)\1\s*,\s*(['""])#[0-9a-fA-F]{6}\3\)", "readToken('$2', 'TOKEN_FALLBACK')"
$aiv_tokenLeak = ($aiv_tokenScan -match '#[0-9a-fA-F]{6}\b' -or $aiv_tokenScan -match 'rgb\(\s*\d')
Assert-Test 'AiStatsView no hardcoded color tokens' (-not $aiv_tokenLeak) ''

# ============================================================
# UI 6: sidebar / router
# ============================================================
Write-Host ''
Write-Host '== UI 6: sidebar + router ======================' -ForegroundColor Cyan

$sb = (Get-Url ($VITE + '/src/components/layout/AppSidebar.vue')).body
Assert-Test 'sidebar imports Activity icon' ([bool]($sb -match '\bActivity\b')) ''
Assert-Test 'sidebar iconMap has Activity'  ([bool]($sb -match 'Activity[\s,}]')) ''

$router = (Get-Url ($VITE + '/src/router/index.ts')).body
Assert-Test 'router has /ai-stats path' ([bool]($router -match "/ai-stats")) ''
Assert-Test 'router has 404 catch-all'  ([bool]($router -match 'pathMatch')) ''
Assert-Test 'router meta has AI title'  ([bool]($router -match 'AI ')) ''

# ============================================================
# UI 7: NetworkBanner mounted
# ============================================================
Write-Host ''
Write-Host '== UI 7: NetworkBanner integrated ==============' -ForegroundColor Cyan

$app = (Get-Url ($VITE + '/src/App.vue')).body
Assert-Test 'App.vue imports NetworkBanner' ([bool]($app -match 'NetworkBanner')) ''
Assert-Test 'App.vue uses useSse reconnect' ([bool]($app -match 'reconnect')) ''

$nb = (Get-Url ($VITE + '/src/components/layout/NetworkBanner.vue')).body
Assert-Test 'NetworkBanner uses useHttpHealth'  ([bool]($nb -match 'useHttpHealth')) ''
Assert-Test 'NetworkBanner has reconnect emit'   ([bool]($nb -match "emit.{0,3}reconnect")) ''
Assert-Test 'NetworkBanner has 30s grace timer'  ([bool]($nb -match '30_000|DISCONNECT_GRACE')) ''

# ============================================================
# UI 8: 5 ListView use SkeletonList
# ============================================================
Write-Host ''
Write-Host '== UI 8: ListViews use SkeletonList ============' -ForegroundColor Cyan

$listViews = @('Events','Incidents','Rules','Objects','Channels')
foreach ($v in $listViews) {
  $body = (Get-Url ($VITE + '/src/views/' + $v + 'View.vue')).body
  Assert-Test ($v + 'View imports SkeletonList') ([bool]($body -match 'SkeletonList')) ''
}

# ============================================================
# UI 9: AiSummaryCard PENDING typewriter
# ============================================================
Write-Host ''
Write-Host '== UI 9: AiSummaryCard PENDING animation =======' -ForegroundColor Cyan

$ac = (Get-Url ($VITE + '/src/components/alert/AiSummaryCard.vue')).body
$acSourcePath = Join-Path $PSScriptRoot '..\frontend\src\components\alert\AiSummaryCard.vue'
$acSource = if (Test-Path $acSourcePath) { Get-Content -Raw -Encoding UTF8 $acSourcePath } else { '' }
Assert-Test 'AiSummaryCard has thinking-line'  ([bool]($ac -match 'thinking-line')) ''
Assert-Test 'AiSummaryCard has THINKING_MESSAGES' ([bool]($ac -match 'THINKING_MESSAGES')) ''
Assert-Test 'AiSummaryCard has caret animation' ([bool]($ac -match 'caret')) ''
Assert-Test 'AiSummaryCard PENDING uses tokens' ([bool](($ac -match '--accent') -or ($acSource -match '--accent'))) ''

# ============================================================
# Summary
# ============================================================
Write-Host ''
Write-Host '========================================' -ForegroundColor Yellow
$total = $global:passCount + $global:failCount
Write-Host ("UI tests: " + $total + " | PASS: " + $global:passCount + " | FAIL: " + $global:failCount) -ForegroundColor Yellow
Write-Host '========================================' -ForegroundColor Yellow

if ($global:failCount -gt 0) {
  Write-Host ''
  Write-Host 'FAILED cases:' -ForegroundColor Red
  $global:results | Where-Object { $_.Status -eq 'FAIL' } | ForEach-Object {
    Write-Host ('  - ' + $_.Name + ' :: ' + $_.Detail) -ForegroundColor Red
  }
}
