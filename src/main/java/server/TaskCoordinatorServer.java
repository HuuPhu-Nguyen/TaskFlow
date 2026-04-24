package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import server.handler.PeerHandler;
import server.model.MessageEnvelope;
import server.monitor.PeerLivenessMonitor;
import server.registry.InMemoryPeerRegistry;
import server.registry.PeerRegistry;
import server.scheduler.TaskScheduler; // Import your new class

public class TaskCoordinatorServer {

    private static final int PORT = 6789;
    private static final int IO_POOL_SIZE = 100;
    private static final long HEARTBEAT_TIMEOUT_MILLIS = 90_000;

    public static void main(String[] args) {
        BlockingQueue<MessageEnvelope> inboundMailbox = new LinkedBlockingQueue<>();
        PeerRegistry registry = new InMemoryPeerRegistry();

        //Initialize the TaskScheduler
        TaskScheduler schedulerLogic = new TaskScheduler(inboundMailbox, registry);
        Thread schedulerThread = new Thread(schedulerLogic, "task-scheduler");

        //Monitoring and Networking
        ExecutorService ioPool = Executors.newFixedThreadPool(IO_POOL_SIZE);
        PeerLivenessMonitor monitor = new PeerLivenessMonitor(registry, HEARTBEAT_TIMEOUT_MILLIS);

        //Status Printer (Modified to include new performance metrics)
        Thread statusPrinter = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3000);
                    System.out.println("\n========== PEER STATUS ==========");
                    System.out.printf("%-20s %-10s %-10s %-10s %-10s%n",
                            "Node ID", "Status", "Tasks", "Latency", "Avg Dur");

                    long now = System.currentTimeMillis();
                    for (var peer : registry.getAllPeers()) {
                        long delta = now - peer.getLastHeartbeatReceivedAtMillis();
                        String status = (delta < HEARTBEAT_TIMEOUT_MILLIS) ? "ALIVE" : "STALE";

                        System.out.printf("%-20s %-10s %-10d %-10d %-10d%n",
                                peer.getNodeId(),
                                status,
                                peer.getActiveTasks(),
                                peer.getLatency(),
                                peer.getAvgTaskDuration());
                    }
                    System.out.println("=================================\n");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "status-printer");

        // Start everything
        monitor.start();
        schedulerThread.start();
        statusPrinter.start();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down coordinator...");
            schedulerThread.interrupt();
            monitor.shutdown();
            ioPool.shutdownNow();
        }));

        //Server Loop
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("TaskCoordinatorServer listening on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                // PeerHandler will drop messages into 'inboundMailbox', which schedulerThread pulls from
                ioPool.submit(new PeerHandler(socket, registry, inboundMailbox));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}