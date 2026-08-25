@echo off
cd /d "%~dp0"
echo ============================================
echo   Smart Smoke Detector - Frontend (Vite)
echo   Open http://127.0.0.1:5173 in your browser
echo   (Backend must be running at :8080 first)
echo   Press Ctrl+C to stop
echo ============================================
if not exist node_modules (
  echo Installing dependencies...
  call npm install
)
call npm run dev
pause
