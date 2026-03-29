package server.monitor;

import server.registry.PeerInfo;
import server.registry.PeerRegistry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PeerLivenessMonitor {

    private final PeerRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final long timeoutMillis;

    public PeerLivenessMonitor(PeerRegistry registry, long timeoutMillis) {
        this.registry = registry;
        this.timeoutMillis = timeoutMillis;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkPeers, 5, 5, TimeUnit.SECONDS);
    }

    private void checkPeers() {
        long now = System.currentTimeMillis();
        for (PeerInfo peer : registry.getAllPeers()) {
            long lastSeen = peer.getLastHeartbeatReceivedAtMillis();
            if (now - lastSeen > timeoutMillis) {
                System.out.println("Peer timed out: " + peer.getNodeId());
                registry.remove(peer.getNodeId());
                try {
                    peer.getSocket().close();
                } catch (Exception ignored) {}
            }
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}