# ==============================================================================
# Enterprise Knowledge Intelligence Platform — DBE Live Working Demo Script
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Enterprise Knowledge Intelligence Platform — DBE Demo Runner   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Check Docker Daemon
Write-Host "[1/4] Verifying Docker daemon connection..." -ForegroundColor Yellow
$dockerCheck = docker ps 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Docker Desktop is not running." -ForegroundColor Red
    Write-Host "Please start 'Docker Desktop' from your Start Menu, wait for it to initialize, and rerun this script." -ForegroundColor Yellow
    exit 1
}
Write-Host "Docker daemon is active and responsive." -ForegroundColor Green
Write-Host ""

# 2. Spin up the isolated Demo database stack
Write-Host "[2/4] Starting isolated DBE Demo database stack (PostgreSQL, MongoDB, Qdrant)..." -ForegroundColor Yellow
$demoCompose = Join-Path $PSScriptRoot "database\docker-compose.demo.yml"
docker compose -f $demoCompose up -d --wait

if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to start demo containers." -ForegroundColor Red
    exit 1
}

Write-Host "Demo stack running on isolated ports:" -ForegroundColor Green
Write-Host "  - PostgreSQL : localhost:5436 (315 synthetic enterprise documents)"
Write-Host "  - MongoDB    : localhost:27019 (Full text, chunks, references, metadata)"
Write-Host "  - Qdrant     : localhost:6345 (HTTP) / 6346 (gRPC) (705 384-D vectors)"
Write-Host ""

# 3. Execute the live Demo Semantic Search & RBAC Integration Test Suite
Write-Host "[3/4] Running live Java ONNX Semantic Search & RBAC Integration Test..." -ForegroundColor Yellow
$backendDir = Join-Path $PSScriptRoot "backend"
Push-Location $backendDir

& .\mvnw.cmd test "-Dtest=DemoSemanticSearchIntegrationTest"

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "[PASS] Demo Integration Test passed successfully!" -ForegroundColor Green
    Write-Host "  - On-the-fly in-process Java ONNX embedding verified" -ForegroundColor Green
    Write-Host "  - Dense Qdrant vector retrieval verified" -ForegroundColor Green
    Write-Host "  - PostgreSQL keyword search + Qdrant vector score fusion verified" -ForegroundColor Green
    Write-Host "  - Document-level RBAC security enforcement verified (unauthorized docs dropped)" -ForegroundColor Green
} else {
    Write-Host "Test execution encountered errors." -ForegroundColor Red
}

Pop-Location
Write-Host ""

# 4. Instructions for interactive Spring Boot live server
Write-Host "[4/4] Next Steps — Running the live Spring Boot server for curl / Postman:" -ForegroundColor Yellow
Write-Host "To run the interactive backend server against this demo stack, run:"
Write-Host ""
Write-Host "  cd subjects/DBE-DSD/backend" -ForegroundColor Cyan
Write-Host "  `$env:JWT_SECRET='dGVzdC1zZWNyZXQta2V5LXNob3VsZC1iZS1hdC1sZWFzdC0zMi1ieXRlcw=='" -ForegroundColor Cyan
Write-Host "  `$env:DB_PORT='5436'" -ForegroundColor Cyan
Write-Host "  `$env:DB_USERNAME='eip_demo'" -ForegroundColor Cyan
Write-Host "  `$env:DB_PASSWORD='demo_pass_123'" -ForegroundColor Cyan
Write-Host "  `$env:MONGO_PORT='27019'" -ForegroundColor Cyan
Write-Host "  `$env:MONGO_USERNAME='eip_mongo_demo'" -ForegroundColor Cyan
Write-Host "  `$env:MONGO_PASSWORD='mongo_demo_pass_123'" -ForegroundColor Cyan
Write-Host "  `$env:QDRANT_PORT='6346'" -ForegroundColor Cyan
Write-Host "  `$env:EMBEDDING_ENABLED='true'" -ForegroundColor Cyan
Write-Host "  .\mvnw.cmd spring-boot:run" -ForegroundColor Cyan
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Demo environment ready!" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
