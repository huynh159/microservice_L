# ==============================================================================
# AUTOMATED E2E (END-TO-END) TEST SCRIPT - STEP 5.2
# ==============================================================================
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8081"
$mailpitUrl = "http://localhost:8025"
$testEmail = "e2e_user_$([System.DateTimeOffset]::UtcNow.ToUnixTimeSeconds())@example.com"
$initialPassword = "Password123@"
$newPassword = "NewPassword456@"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "STARTING END-TO-END (E2E) TEST ON DOCKER COMPOSE STACK" -ForegroundColor Cyan
Write-Host "Test email: $testEmail" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# 1. REGISTER NEW USER
# ------------------------------------------------------------------------------
Write-Host "`n[1/8] Registering new user..." -NoNewline
$registerBody = @{
    email = $testEmail
    password = $initialPassword
    fullName = "Le Huynh E2E"
    phone = "0987654321"
} | ConvertTo-Json

$regResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/users" -Method Post -Body $registerBody -ContentType "application/json"
if ($regResp.email -eq $testEmail) {
    Write-Host " [PASS] (HTTP 201 Created, ID: $($regResp.id))" -ForegroundColor Green
} else {
    Write-Host " [FAIL]" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 2. CHECK WELCOME EMAIL IN MAILPIT
# ------------------------------------------------------------------------------
Write-Host "[2/8] Polling Mailpit for welcome email via Kafka..." -NoNewline
$emailFound = $false
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 1
    $mailResp = Invoke-RestMethod -Uri "$mailpitUrl/api/v1/messages"
    $targetEmail = $mailResp.messages | Where-Object { $_.To[0].Address -eq $testEmail }
    if ($targetEmail) {
        $emailFound = $true
        Write-Host " [PASS] (Received email: '$($targetEmail.Subject)' in Mailpit)" -ForegroundColor Green
        break
    }
}
if (-not $emailFound) {
    Write-Host " [FAIL] (Email not found in Mailpit after 15s)" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 3. LOGIN AND RECEIVE JWT TOKENS
# ------------------------------------------------------------------------------
Write-Host "[3/8] Logging in with initial credentials..." -NoNewline
$loginBody = @{
    email = $testEmail
    password = $initialPassword
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$accessToken = $loginResp.accessToken
$refreshToken = $loginResp.refreshToken

if ($accessToken -and $refreshToken) {
    Write-Host " [PASS] (Tokens issued: AccessToken and RefreshToken)" -ForegroundColor Green
} else {
    Write-Host " [FAIL] (Missing tokens in login response)" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 4. GET /api/v1/users/me WITH BEARER TOKEN
# ------------------------------------------------------------------------------
Write-Host "[4/8] Fetching profile (GET /api/v1/users/me)..." -NoNewline
$headers = @{
    Authorization = "Bearer $accessToken"
}
$profileResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/users/me" -Method Get -Headers $headers
if ($profileResp.email -eq $testEmail -and $profileResp.fullName -eq "Le Huynh E2E") {
    Write-Host " [PASS] (JWT verified, Roles: $($profileResp.roles))" -ForegroundColor Green
} else {
    Write-Host " [FAIL]" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 5. CHANGE PASSWORD (PUT /api/v1/users/me/password)
# ------------------------------------------------------------------------------
Write-Host "[5/8] Changing password..." -NoNewline
$changePassBody = @{
    oldPassword = $initialPassword
    newPassword = $newPassword
} | ConvertTo-Json

$changePassResp = Invoke-WebRequest -Uri "$baseUrl/api/v1/users/me/password" -Method Put -Headers $headers -Body $changePassBody -ContentType "application/json" -UseBasicParsing
if ($changePassResp.StatusCode -eq 204) {
    Write-Host " [PASS] (HTTP 204 No Content - Password changed)" -ForegroundColor Green
} else {
    Write-Host " [FAIL]" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 6. LOGIN WITH NEW PASSWORD
# ------------------------------------------------------------------------------
Write-Host "[6/8] Logging in with new password..." -NoNewline
$loginNewBody = @{
    email = $testEmail
    password = $newPassword
} | ConvertTo-Json

$loginNewResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method Post -Body $loginNewBody -ContentType "application/json"
$newAccessToken = $loginNewResp.accessToken
$newRefreshToken = $loginNewResp.refreshToken

if ($newAccessToken -and $newRefreshToken) {
    Write-Host " [PASS] (Login with new password succeeded)" -ForegroundColor Green
} else {
    Write-Host " [FAIL]" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 7. LOGOUT (REVOKE REFRESH TOKEN)
# ------------------------------------------------------------------------------
Write-Host "[7/8] Logging out..." -NoNewline
$logoutBody = @{
    refreshToken = $newRefreshToken
} | ConvertTo-Json

$logoutHeaders = @{
    Authorization = "Bearer $newAccessToken"
}
$logoutResp = Invoke-WebRequest -Uri "$baseUrl/api/v1/auth/logout" -Method Post -Headers $logoutHeaders -Body $logoutBody -ContentType "application/json" -UseBasicParsing
if ($logoutResp.StatusCode -eq 204) {
    Write-Host " [PASS] (HTTP 204 No Content - Token revoked)" -ForegroundColor Green
} else {
    Write-Host " [FAIL]" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------------------
# 8. ATTEMPT REFRESH WITH REVOKED TOKEN (EXPECT 401 UNAUTHORIZED)
# ------------------------------------------------------------------------------
Write-Host "[8/8] Verifying revoked token rejection..." -NoNewline
$refreshBody = @{
    refreshToken = $newRefreshToken
} | ConvertTo-Json

try {
    $refreshResp = Invoke-WebRequest -Uri "$baseUrl/api/v1/auth/refresh" -Method Post -Body $refreshBody -ContentType "application/json" -UseBasicParsing
    Write-Host " [FAIL] (Token was not rejected, status: $($refreshResp.StatusCode))" -ForegroundColor Red
    exit 1
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-Host " [PASS] (HTTP 401 Unauthorized - Revoked token correctly rejected)" -ForegroundColor Green
    } else {
        Write-Host " [FAIL] (Unexpected status code: $statusCode)" -ForegroundColor Red
        exit 1
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "ALL 8 E2E TESTS PASSED 100% GREEN!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
