package server.registry;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PeerInfo {

    private final String nodeId;
    private final Socket socket;
    private final AtomicLong lastHeartbeatReceivedAtMillis;
    private final AtomicInteger activeTasks = new AtomicInteger(0);

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
}