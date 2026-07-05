# Vault Bank - PowerShell Integrated Runner
Clear-Host

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "         VAULT BANK - INTEGRATED RUNNER" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/3] Spinning up Docker backing services (MySQL + Kafka)..." -ForegroundColor Cyan
docker compose up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[ERROR] Failed to start Docker services. Ensure Docker Desktop is running!" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "[2/3] Waiting 10 seconds for MySQL database & Kafka brokers to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host ""
Write-Host "[3/3] Launching Spring Boot server (hosting Backend + Web UI Dashboard)..." -ForegroundColor Cyan
Write-Host "--------------------------------------------------" -ForegroundColor Gray
Write-Host "👉 Open your web browser at: http://localhost:8081" -ForegroundColor Green
Write-Host "--------------------------------------------------" -ForegroundColor Gray
Write-Host ""

mvn spring-boot:run
