Write-Host "=== BƯỚC 6.1: KIỂM TRA TRACE ID PHÂN TÁN VÀ ACTUATOR METRICS ==="

Start-Sleep -Seconds 5

Write-Host "1. Kiểm tra Actuator Health của user-service:"
$h1 = Invoke-WebRequest -Uri "http://localhost:8081/actuator/health" -UseBasicParsing
Write-Host "User-Service Health: $($h1.Content)"

Write-Host "2. Kiểm tra Actuator Health của notification-service:"
$h2 = Invoke-WebRequest -Uri "http://localhost:8082/actuator/health" -UseBasicParsing
Write-Host "Notification-Service Health: $($h2.Content)"

Write-Host "3. Gửi request tạo User với X-Trace-Id cụ thể:"
$traceId = "trace-e2e-" + (Get-Random)
$regBody = @{
    email = "trace_demo_" + (Get-Random) + "@example.com"
    password = "Password123@"
    fullName = "Distributed Trace User"
    phone = "0987111222"
} | ConvertTo-Json

$resp = Invoke-WebRequest -Uri "http://localhost:8081/api/v1/users" -Method Post -Headers @{ "X-Trace-Id" = $traceId } -Body $regBody -ContentType "application/json" -UseBasicParsing
Write-Host "User-Service Response Code: $($resp.StatusCode)"
Write-Host "Returned X-Trace-Id Header: $($resp.Headers['X-Trace-Id'])"

Write-Host "Chờ 3 giây để Outbox quét và phát sang Notification Consumer..."
Start-Sleep -Seconds 3

Write-Host "4. Kiểm tra Log User Service với Trace ID [$traceId]:"
$userLogs = docker logs microservice_user_service 2>&1 | Select-String -Pattern $traceId
if ($userLogs) {
    $userLogs | Select-Object -Last 3 | ForEach-Object { Write-Host "   USER LOG: $_" }
} else {
    Write-Host "   (Không tìm thấy trong log user-service)"
}

Write-Host "5. Kiểm tra Log Notification Service với Trace ID [$traceId]:"
$notiLogs = docker logs microservice_notification_service 2>&1 | Select-String -Pattern $traceId
if ($notiLogs) {
    $notiLogs | Select-Object -Last 3 | ForEach-Object { Write-Host "   NOTI LOG: $_" }
} else {
    Write-Host "   (Không tìm thấy trong log notification-service)"
}

Write-Host "6. Kiểm tra Endpoint Actuator Metrics:"
$m1 = (Invoke-WebRequest -Uri "http://localhost:8081/actuator/metrics" -UseBasicParsing).StatusCode
Write-Host "User-Service /actuator/metrics Status: $m1"
$m2 = (Invoke-WebRequest -Uri "http://localhost:8082/actuator/metrics" -UseBasicParsing).StatusCode
Write-Host "Notification-Service /actuator/metrics Status: $m2"

Write-Host "=== KẾT THÚC KIỂM TRA BƯỚC 6.1 ==="
