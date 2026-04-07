@echo off
setlocal enabledelayedexpansion

set HOST=%1
set PORT=%2

if "!HOST!"=="" set HOST=localhost
if "!PORT!"=="" set PORT=6789

set FAT_JAR=target\distributed-task-system-1.0-SNAPSHOT-jar-with-dependencies.jar

java -cp "!FAT_JAR!" peer.PeerNode !HOST! !PORT!
