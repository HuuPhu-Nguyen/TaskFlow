package server.registry;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PeerInfo {

    private final String nodeId;
    private final Socket socket;

    private final AtomicLong lastHeartbeatReceivedAtMillis;
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    private volatile PeerSender sender;

    private long latency;           // Last measured RTT
    private long avgTaskDuration;   // Average turnaround time
    private long avgCpuTime;        // Placeholder for future use
    private long failedTasks;

    public void attachSender(PeerSender sender) {
        this.sender = sender;
    }
    public void send(protocol.Message message) {
        if (sender != null) {
            sender.send(message);
        } else {
            System.err.println("No sender attached for " + nodeId);
        }
    }

    public PeerInfo(String nodeId, Socket socket) {
        this.nodeId = nodeId;
        this.socket = socket;
        this.lastHeartbeatReceivedAtMillis = new AtomicLong(System.currentTimeMillis());
    }

    public String getNodeId() {
        return nodeId;
    }

    public Socket getSocket() {return socket;}

    public long getLastHeartbeatReceivedAtMillis() {return lastHeartbeatReceivedAtMillis.get();}

    public void updateHeartbeatReceivedNow() {lastHeartbeatReceivedAtMillis.set(System.currentTimeMillis());}

    public int getActiveTasks() {return activeTasks.get();}

    public int incrementTasks() {return activeTasks.incrementAndGet();}

    public int decrementTasks() {return activeTasks.decrementAndGet();}

    public void updateLatency(long rtt) { this.latency = rtt; }

    public void updateTaskMetrics(long duration) {
        // Simple moving average for turnaround time
        if (this.avgTaskDuration == 0) this.avgTaskDuration = duration;
        else this.avgTaskDuration = (this.avgTaskDuration + duration) / 2;
    }

    public long getLatency() { return latency; }
    public long getAvgTaskDuration() { return avgTaskDuration; }
    public void incrementFailedTasks() { failedTasks++; }
    public void decrementFailedTasks() { failedTasks--; }

    public double getSelectionScore() {
        // We weight active tasks heavily to ensure load balancing
        // Then we add performance metrics as tie-breakers
        double score = activeTasks.get() * 5000.0;
        score += latency;
        score += avgTaskDuration;

        // Penalize peers that have failed tasks recently
        score += (failedTasks * 2000.0);

        return score;
    }
}