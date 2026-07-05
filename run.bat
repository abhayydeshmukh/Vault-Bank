@echo off
title Vault Bank Startup Script
cls

echo ==================================================
echo         VAULT BANK - INTEGRATED RUNNER
echo ==================================================
echo.

echo [1/3] Spinning up Docker backing services (MySQL + Kafka)...
docker compose up -d
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Failed to start Docker services. Please ensure Docker Desktop is running!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Waiting 10 seconds for database and Kafka queues to initialize...
timeout /t 10 /nobreak > NUL

echo.
echo [3/3] Launching Spring Boot server (hosting Backend + Web UI Dashboard)...
echo.
echo Open your browser at: http://localhost:8081
echo.
call mvn spring-boot:run
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Maven failed to execute spring-boot:run.
    pause
)
