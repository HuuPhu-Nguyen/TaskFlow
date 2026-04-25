# TaskFlow

TaskFlow is a distributed task processing system built on raw Java TCP sockets and JSON messaging.  
A central coordinator distributes work across connected peers, which execute tasks in parallel and return results asynchronously.

The system demonstrates key distributed systems concepts such as scheduling, load balancing, fault tolerance, and asynchronous communication.

---

## Overview

TaskFlow follows a **coordinator–worker model**:

- A **Coordinator Server** manages jobs and connected peers
- **Peer nodes** execute tasks concurrently
- A **GUI client** submits jobs and receives results

Jobs are submitted dynamically by clients and processed in a fully asynchronous, message-driven pipeline.

---

## Core Components

### Coordinator Server

The coordinator is the central entry point of the system.

- Listens for peer connections on port `6789`
- Maintains a registry of connected peers
- Handles networking via `PeerHandler`
- Delegates all scheduling logic to a dedicated `TaskScheduler` thread

The system uses a mailbox-based design where incoming messages are queued and processed asynchronously.

---

### Task Scheduler

The `TaskScheduler` is the core of the system.

**Responsibilities:**
- Handles incoming messages (`JOB_SUBMIT`, `TASK_RESULT`)
- Creates jobs and splits them into tasks
- Dispatches tasks to available peers
- Tracks task progress and retries failed work
- Aggregates results and returns them to the requester

**Load Balancing**
- Maximum of **3 concurrent tasks per peer**
- Peers are selected based on a scoring function (load + performance)

**Fault Tolerance**
- Task timeout: **60 seconds**
- Automatic retries on failure
- Failed tasks are returned to the pending queue and retried by available peers

---

### Peer Node

A `PeerNode` connects to the coordinator and executes assigned tasks.

**Responsibilities:**
- Maintain TCP connection with the server
- Respond to heartbeat messages (`PING` / `PONG`)
- Receive `TASK_ASSIGN` messages
- Execute tasks using the execution engine
- Send results back via `TASK_RESULT`

---

### Execution Engine

Each peer runs a `PeerExecutionEngine`.

- Uses a thread pool sized to available CPU cores
- Executes tasks asynchronously
- Supports pluggable processors for different task types

New task types can be added without modifying the core system.

---

### Job Model

TaskFlow uses a generic abstraction for distributed jobs.

#### EmbarrassinglyParallelJob
- Splits work into independent tasks
- Tracks completion safely (idempotent updates)
- Aggregates final results

#### TaskUnit
Each task tracks:
- Status (`PENDING`, `ASSIGNED`, `COMPLETED`, `FAILED`)
- Assigned peer
- Retry count
- Execution timing

---

## Supported Jobs

Currently implemented job types:
IMAGE_CONVERSION
VIDEO_TRANSCODING


**Image conversion features:**
- Converts between PNG, JPG, BMP, GIF
- Supports PDF to image conversion
- Uses Apache PDFBox for PDF rendering
- Transfers files as Base64-encoded payloads

**Video transcoding features:**
- Converts between MP4, AVI, MKV, MOV, WEBM, FLV, WMV
- Uses JavaCV with bundled FFmpeg native libraries
- Uses broadly available FFmpeg encoders for portability across machines
- Transfers files as Base64-encoded payloads

Each file is processed independently, allowing full parallel execution across peers.

---

## Message Protocol

All communication is done using JSON messages over TCP.

### Message Types

- `JOB_SUBMIT` — submit a new job
- `TASK_ASSIGN` — assign a task to a peer
- `TASK_RESULT` — return result from peer
- `JOB_RESULT` — final aggregated result
- `PING` — heartbeat from server
- `PONG` — heartbeat response from peer

---

## Workflow

1. GUI uploads files and encodes them in Base64
2. A `JOB_SUBMIT` message is sent to the coordinator
3. The scheduler creates a job and splits it into tasks
4. Tasks are distributed to peers (`TASK_ASSIGN`)
5. Peers execute tasks and return results (`TASK_RESULT`)
6. The scheduler aggregates results
7. The coordinator sends a `JOB_RESULT` back to the client
8. The GUI allows the user to save output files

---

## GUI Client

The JavaFX GUI (`PeerApp`) acts as both:

- a **job client** (submits work)
- a **worker peer** (executes tasks)

**Features:**
- Upload files
- Select output format
- Submit distributed jobs
- Receive and save results
- Uses temporary session folders for input/output

---

## Key Design Features

### Asynchronous Message-Driven System
- Decoupled components via message passing
- No blocking request-response model

### Fault Tolerance
- Task retries
- Timeout detection
- Peer failure handling

### Load Balancing
- Dynamic scheduling
- Peer scoring and task limits

### Extensibility
- New job types via factory pattern
- New processors via `TaskProcessor` interface

---

## Dependencies

- Gson — JSON serialization
- Apache PDFBox — PDF rendering
- JavaFX — GUI
- JavaCV / FFmpeg — video transcoding

---

## Future Improvements

- Persistent job tracking (database)
- Distributed coordinator (no single point of failure)
- More task types
- Monitoring and metrics dashboard

---

## Notes

This project is designed to demonstrate practical distributed systems concepts, including:

- task orchestration
- concurrency control
- fault tolerance
- network-based computation  

## How to Run

### Will this run on any computer?

It should run on a normal Windows, macOS, or Linux desktop/laptop if all of these are true:

- Java 21 or newer is installed.
- Maven 3.9 or newer is installed.
- The machine can download Maven dependencies the first time it builds.
- The GUI machine has a desktop environment available. Headless servers can run the coordinator or command-line peer, but not the JavaFX GUI.
- Port `6789` is open between the coordinator and peer machines.

For multiple computers, start the coordinator on one machine. On every GUI or peer machine, connect to the coordinator machine's IP address instead of `localhost`.

### Prerequisites

Make sure you have the following installed:

- **Java 21 or higher**
- **Maven 3.9+**

Check installation:

```bash
java -version
mvn -version
```

---

### 1. Clone the Repository

```bash
git clone <your-repo-url>
cd TaskFlow
```

---

### 2. Build the Project

```bash
mvn clean package
```

---

### 3. Start the Coordinator Server

In one terminal:

```bash
mvn exec:java
```

The coordinator main class is configured in `pom.xml`, so no additional CLI property is needed.

---

### 4. Start the GUI

In another terminal:

```bash
mvn javafx:run
```

---
Inside the GUI:

1. Enter:
   - Host: `localhost` if the coordinator is on the same computer, otherwise the coordinator computer's IP address
   - Port: `6789`
2. Click **Connect**
3. Upload files
4. Choose output format
5. Click **Start Conversion**
6. Select a folder to save results

---

### Optional: Start a Command-Line Worker Peer

The GUI also works as a peer, so this is optional. Use this when you want another machine or terminal to contribute worker capacity without opening the GUI:

```bash
mvn exec:java -Dexec.mainClass=peer.PeerNode -Dexec.args="localhost 6789"
```

Replace `localhost` with the coordinator machine's IP address when running across computers.

### Notes

- The GUI also acts as a peer and can execute tasks.
- Always start the server before peers or GUI.
- If connection fails, verify port `6789` is available.
- If video conversion fails on one machine but not another, rebuild and restart every coordinator/peer process so all machines use the same compiled code.
