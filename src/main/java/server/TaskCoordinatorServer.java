package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import protocol.Message;
import protocol.MessageType;
import protocol.TaskResultMessage;
import server.handler.PeerHandler;
import server.model.MessageEnvelope;
import server.monitor.PeerLivenessMonitor;
import server.registry.InMemoryPeerRegistry;
import server.registry.PeerInfo;
import server.registry.PeerRegistry;

public class TaskCoordinatorServer {

    private static final int PORT = 6789;
    private static final int IO_POOL_SIZE = 100;
    private static final long HEARTBEAT_TIMEOUT_MILLIS = 10_000;

    public static void main(String[] args) {

        // Optional: java server.TaskCoordinatorServer <inputFolder> [targetFormat]
        BlockingQueue<MessageEnvelope> inboundMailbox = new LinkedBlockingQueue<>();
        PeerRegistry registry = new InMemoryPeerRegistry();

        ExecutorService ioPool = Executors.newFixedThreadPool(IO_POOL_SIZE);
        PeerLivenessMonitor monitor = new PeerLivenessMonitor(registry, HEARTBEAT_TIMEOUT_MILLIS);

        final JobDispatcher jobDispatcher = (args.length >= 1)
                ? new JobDispatcher(registry, Paths.get(args[0]), args.length >= 2 ? args[1] : "png")
                : null;

        if (jobDispatcher != null) {
            System.out.printf("Image conversion job: %s → %s%n",
                    args[0], (args.length >= 2 ? args[1] : "png").toUpperCase());
        }

        Thread schedulerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    MessageEnvelope envelope = inboundMailbox.take();
                    Message message = envelope.message();
                    String fromNodeId = envelope.fromNodeId();

                    if (MessageType.TASK_RESULT.equals(message.getType())) {
                        TaskResultMessage result = (TaskResultMessage) message;
                        PeerInfo peer = registry.get(fromNodeId);
                        if (peer != null) peer.decrementTasks();
                        if (jobDispatcher != null) {
                            jobDispatcher.onResult(
                                    result.getTaskId(),
                                    result.getStatus(),
                                    result.getOutputPath(),
                                    result.getError());
                        }
                    } else {
                        System.out.println("Scheduler: unhandled " + message.getType() + " from " + fromNodeId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Scheduler error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "task-scheduler");

        Thread statusPrinter = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3000); // print every 3 seconds

                    System.out.println("\n========== PEER STATUS ==========");
                    System.out.printf("%-30s %-20s %-10s %-10s%n",
                            "Peer", "Last Seen (ms ago)", "Status", "Tasks");

                    long now = System.currentTimeMillis();

                    for (var peer : registry.getAllPeers()) {
                        long lastSeen = peer.getLastHeartbeatReceivedAtMillis();
                        long delta = now - lastSeen;

                        String status = (delta < 10000) ? "ALIVE" : "STALE";

                        System.out.printf("%-30s %-20d %-10s %-10d%n",
                                peer.getNodeId(),
                                delta,
                                status,
                                peer.getActiveTasks());
                    }

                    System.out.println("=================================\n");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Status printer error: " + e.getMessage());
                }
            }
        }, "status-printer");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down coordinator...");
            schedulerThread.interrupt();
            monitor.shutdown();
            ioPool.shutdownNow();
            System.out.println("Coordinator halted.");
        }));

        monitor.start();
        schedulerThread.start();
        statusPrinter.start();

        if (jobDispatcher != null) {
            Thread dispatchThread = new Thread(jobDispatcher, "job-dispatcher");
            dispatchThread.setDaemon(true);
            dispatchThread.start();
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("TaskCoordinatorServer listening on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted peer: " + socket.getRemoteSocketAddress());
                ioPool.submit(new PeerHandler(socket, registry, inboundMailbox));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}