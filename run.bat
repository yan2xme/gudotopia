@echo off
cd /d %~dp0
if not exist out mkdir out
javac -d out -cp "lib\*" src\*.java && java -cp "out;lib\*" Main
