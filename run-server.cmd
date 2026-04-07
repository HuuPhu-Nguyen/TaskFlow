@echo off
setlocal enabledelayedexpansion

set INPUT_FOLDER=%1
set TARGET_FORMAT=%2

if "!TARGET_FORMAT!"=="" set TARGET_FORMAT=png

set FAT_JAR=target\distributed-task-system-1.0-SNAPSHOT-jar-with-dependencies.jar

if "!INPUT_FOLDER!"=="" (
    echo Starting coordinator (no job)
    java -cp "!FAT_JAR!" server.TaskCoordinatorServer
) else (
    echo Starting coordinator with job: !INPUT_FOLDER! -^> !TARGET_FORMAT!
    java -cp "!FAT_JAR!" server.TaskCoordinatorServer "!INPUT_FOLDER!" "!TARGET_FORMAT!"
)
