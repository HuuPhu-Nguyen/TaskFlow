package server.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;

import com.google.gson.Gson;

import messaging.MessageFactory;
import protocol.Message;
import protocol.MessageType;
import protocol.PingMessage;
import protocol.PongMessage;
import protocol.TaskResultMessage;
import server.model.MessageEnvelope;
import server.registry.PeerInfo;
import server.registry.PeerRegistry;

public class PeerHandler implements Runnable {

    private static final long HEARTBEAT_INTERVAL_MILLIS = 3_000;
    private static final int SOCKET_POLL_TIMEOUT_MILLIS = 1_000;

    private final Socket socket;
    private final PeerRegistry registry;
    private final BlockingQueue<MessageEnvelope> mailbox;

    public PeerHandler(Socket socket,
                       PeerRegistry registry,
                       BlockingQueue<MessageEnvelope> mailbox) {
        this.socket = socket;
        this.registry = registry;
        this.mailbox = mailbox;
    }

    @Override
    public void run() {
        Gson gson = new Gson();
        MessageFactory factory = createFactory(gson);

        String nodeId = socket.getRemoteSocketAddress().toString();
        registry.register(nodeId, new PeerInfo(nodeId, socket));

        try {
            socket.setSoTimeout(SOCKET_POLL_TIMEOUT_MILLIS);
        } catch (IOException e) {
            System.err.println("Failed to configure socket timeout for " + nodeId);
            cleanup(nodeId);
            return;
        }

        try (
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("Handling peer: " + nodeId);

            long nextHeartbeatAt = 0L;

            while (!socket.isClosed()) {
                long now = System.currentTimeMillis();

                if (now >= nextHeartbeatAt) {
                    PingMessage ping = new PingMessage(
                            nodeId,
                            Instant.now().toString()
                    );

                    out.println(gson.toJson(ping));
                    nextHeartbeatAt = now + HEARTBEAT_INTERVAL_MILLIS;
                }

                try {
                    String incomingJson = in.readLine();

                    if (incomingJson == null) {
                        System.out.println("Peer closed connection: " + nodeId);
                        break;
                    }

                    if (incomingJson.trim().isEmpty()) {
                        continue;
                    }

                    Message message = factory.fromJson(incomingJson);

                    if (message instanceof PongMessage) {
                        registry.updateHeartbeat(nodeId);
                        continue;
                    }

                    mailbox.put(new MessageEnvelope(message, nodeId));

                } catch (SocketTimeoutException ignored) {
                    // Normal polling timeout
                } catch (SocketException e) {
                    System.out.println("Peer connection reset: " + nodeId);
                    break;
                } catch (IOException e) {
                    System.out.println("I/O error from peer " + nodeId + ": " + e.getMessage());
                    break;
                } catch (Exception e) {
                    System.err.println("Error processing non-fatal message from " + nodeId);
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            System.out.println("Outer connection failure for " + nodeId + ": " + e.getMessage());
        } finally {
            cleanup(nodeId);
        }
    }

    private void cleanup(String nodeId) {
        registry.remove(nodeId);

        try {
            socket.close();
        } catch (IOException ignored) {
        }

        System.out.println("Disconnected: " + nodeId);
    }

    private MessageFactory createFactory(Gson gson) {
        MessageFactory factory = new MessageFactory();

        factory.register(MessageType.PONG,
                json -> gson.fromJson(json, PongMessage.class));
        factory.register(MessageType.TASK_RESULT,
                json -> gson.fromJson(json, TaskResultMessage.class));

        return factory;
    }
}