# TaskFlow

A distributed task processing system built on raw Java TCP sockets and JSON messaging. A central coordinator server distributes work across any number of connected peer nodes, which process tasks in parallel and report results back.

## Architecture

```
┌─────────────────────────────────────┐
│        TaskCoordinatorServer         │
│                                     │
│  ┌─────────────┐  ┌───────────────┐ │
│  │ JobDispatcher│  │task-scheduler │ │
│  │ (sends tasks)│  │(handles results│ │
│  └──────┬──────┘  └───────┬───────┘ │
│         │                 │         │
│  ┌──────▼─────────────────▼───────┐ │
│  │   PeerHandler (one per peer)   │ │
│  │   - sends PING heartbeats      │ │
│  │   - forwards messages to mailbox│ │
│  └────────────────────────────────┘ │
└──────────────┬──────────────────────┘
               │ TCP (port 6789)
    ┌──────────┴──────────┐
    │                     │
┌───▼────┐          ┌─────▼───┐
│PeerNode│          │PeerNode │   ...
│        │          │         │
│PING→PONG          │PING→PONG│
│TASK_ASSIGN        │TASK_ASSIGN
│→ convert          │→ convert│
│TASK_RESULT        │TASK_RESULT
└────────┘          └─────────┘
```

### Message Flow

1. **Server** scans the input folder for files to process
2. **Server** waits until at least one peer connects
3. **Server → Peer**: `TASK_ASSIGN` — contains input path, output path, target format
4. **Peer** processes the file (e.g. converts PDF → PNG) and writes output
5. **Peer → Server**: `TASK_RESULT` — reports `OK` or `ERROR`
6. **Server** logs progress and picks the next least-loaded peer for the next task
7. **Server → Peer**: `PING` heartbeat every 3 seconds; peer replies with `PONG`
8. Peers that miss heartbeats for 10 seconds are evicted from the registry

## Supported Tasks

Currently implemented: **image format conversion**, including PDF-to-image rendering.

| Input formats | Output formats |
|---|---|
| PDF, PNG, JPG/JPEG, BMP, GIF | PNG, JPG, BMP, GIF |

PDF pages are rendered at 150 DPI. Multi-page PDFs produce one output file per page (e.g. `test-page-0.png`, `test-page-1.png`, …).

## Project Structure

```
src/main/java/
├── server/
│   ├── TaskCoordinatorServer.java   # Main server — accepts peers, runs scheduler
│   ├── JobDispatcher.java           # Scans input folder, distributes tasks to peers
│   └── handler/
│       └── PeerHandler.java         # Per-peer thread — heartbeat + message routing
│   └── monitor/
│       └── PeerLivenessMonitor.java # Evicts unresponsive peers
│   └── registry/
│       ├── PeerRegistry.java        # Interface for peer tracking
│       ├── InMemoryPeerRegistry.java
│       └── PeerInfo.java            # Tracks socket, heartbeat time, active task count
│   └── model/
│       └── MessageEnvelope.java     # Wraps a message with its sender's node ID
├── peer/
│   └── PeerNode.java                # Connects to server, dispatches incoming messages
├── messaging/
│   ├── MessageDispatcher.java       # Routes messages to the right handler by type
│   ├── MessageFactory.java          # Deserializes JSON to typed Message objects
│   ├── MessageHandler.java          # Handler interface
│   └── handlers/
│       ├── PingHandler.java         # Responds to PING with PONG
│       ├── PongHandler.java         # Logs received PONGs
│       └── ConversionHandler.java   # Converts images/PDFs using ImageIO + PDFBox
└── protocol/
    ├── Message.java                 # Abstract base — type, nodeId, time
    ├── MessageType.java             # Constants: PING, PONG, TASK_ASSIGN, TASK_RESULT
    ├── PingMessage.java
    ├── PongMessage.java
    ├── TaskAssignMessage.java       # taskId, inputPath, outputPath, targetFormat
    └── TaskResultMessage.java       # taskId, status, outputPath, error
```

## Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `apache-maven-3.9.14/` directory)

## Build

Run from the `TaskFlow/` directory:

```cmd
mvn package -q
```

This produces `target/distributed-task-system-1.0-SNAPSHOT-jar-with-dependencies.jar` — a fat jar containing all dependencies (Gson, PDFBox).

## Running

### Start the server

```cmd
.\run-server.cmd "<input-folder>" [target-format]
```

- `<input-folder>` — folder containing files to convert (scanned one level deep)
- `target-format` — output format, default `png`

**Example** — convert all PDFs/images in a folder to PNG:
```cmd
.\run-server.cmd "src\main\java" png
```

Output files are written to `<input-folder>\converted\`.

### Start a peer (in a separate terminal)

```cmd
.\run-peer.cmd [host] [port]
```

Defaults: `localhost 6789`

```cmd
.\run-peer.cmd
```

Start multiple peers in multiple terminals to process files in parallel. The server automatically load-balances across all connected peers using least-active-tasks assignment.

## Dependencies

| Library | Version | Use |
|---|---|---|
| [Gson](https://github.com/google/gson) | 2.10.1 | JSON serialization of messages |
| [Apache PDFBox](https://pdfbox.apache.org/) | 3.0.4 | PDF rendering to BufferedImage |
