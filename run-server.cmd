@echo off
setlocal enabledelayedexpansion

set GSON_JAR=%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar

java -cp "target/classes;!GSON_JAR!" server.TaskCoordinatorServer
