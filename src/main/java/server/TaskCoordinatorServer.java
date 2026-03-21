package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskCoordinatorServer {

    private static final int PORT = 6789;
    private static final int THREAD_POOL_SIZE = 10;

    public static void main(String[] args) {

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            pool.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait up to 5 seconds for existing tasks to terminate
                if (!pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    System.out.println("Forcing shutdown now...");
                    pool.shutdownNow(); // Cancel currently executing tasks
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Server halted.");
        }));

        System.out.println("Server starting on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New peer connected: " + socket.getRemoteSocketAddress());

                pool.submit(new PeerHandler(socket));
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}