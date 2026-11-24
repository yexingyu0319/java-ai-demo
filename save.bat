@echo off
chcp 65001 >nul
setlocal

rem 切到本脚本所在目录，避免写死绝对路径
cd /d "%~dp0"

rem 优先用 PATH 里的 git，找不到就用默认安装位置
where git >nul 2>nul
if %errorlevel%==0 (
    set GIT=git
) else (
    set GIT="D:\dev\Git\cmd\git.exe"
)

echo ============================================
echo   一键存档：%~dp0
echo ============================================
echo.

%GIT% status --short
echo.

set /p MSG=请输入本次改动说明（直接回车 = 使用默认说明）:
if "%MSG%"=="" set MSG=daily update

echo.
echo [1/3] 暂存所有改动...
%GIT% add -A

echo [2/3] 提交...
%GIT% commit -m "%MSG%"

echo [3/3] 推送到 GitHub...
%GIT% push

echo.
echo ============================================
echo   完成。仓库：https://github.com/yexingyu0319/java-ai-demo
echo ============================================
pause
