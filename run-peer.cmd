@echo off
setlocal enabledelayedexpansion

set HOST=%1
set PORT=%2

if "!HOST!"=="" set HOST=localhost
if "!PORT!"=="" set PORT=6789

set GSON_JAR=%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar

java -cp "target/classes;!GSON_JAR!" peer.PeerNode !HOST! !PORT!
