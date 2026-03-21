package server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class TaskCoordinatorServer {
    private static final int PORT = 6789;
    private static final String DEFAULT_NODE_ID = "192.168.1.15";
    private static final Gson GSON = new Gson();

    public static void main(String[] argv) throws Exception {
        String nodeId = argv.length > 0 ? argv[0] : DEFAULT_NODE_ID;
        ServerSocket welcomeSocket = new ServerSocket(PORT);

        System.out.println("Waiting for incoming connection Request...");
        System.out.println("Server nodeId: " + nodeId);

        while (true) {
            Socket connectionSocket = welcomeSocket.accept();

            try (
                BufferedReader inFromClient = new BufferedReader(
                    new InputStreamReader(connectionSocket.getInputStream()));
                DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream())
            ) {
                String pingMessage = createPingMessage(nodeId);
                outToClient.writeBytes(pingMessage + "\n");
                outToClient.flush();
                System.out.println("SENT TO CLIENT: " + pingMessage);

                String clientMessage = inFromClient.readLine();
                if (clientMessage == null) {
                    System.out.println("Client disconnected before replying.");
                    continue;
                }

                System.out.println("RECEIVED FROM CLIENT: " + clientMessage);
                processPongMessage(clientMessage);
            } finally {
                connectionSocket.close();
            }
        }
    }

    private static String createPingMessage(String nodeId) {
        JsonObject ping = new JsonObject();
        ping.addProperty("type", "PING");
        ping.addProperty("nodeId", nodeId);
        return GSON.toJson(ping);
    }

    private static void processPongMessage(String clientMessage) {
        try {
            JsonObject pong = GSON.fromJson(clientMessage, JsonObject.class);
            if (pong == null) {
                System.out.println("Received empty JSON payload.");
                return;
            }

            String type = getStringField(pong, "type");
            if (!"PONG".equalsIgnoreCase(type)) {
                System.out.println("Unexpected message type: " + type);
                return;
            }

            String time = getStringField(pong, "time");
            System.out.println("Client replied with PONG at " + time);
        } catch (JsonParseException | IllegalStateException e) {
            System.out.println("Received invalid JSON from client.");
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid PONG message: " + e.getMessage());
        }
    }

    private static String getStringField(JsonObject json, String fieldName) {
        if (!json.has(fieldName) || json.get(fieldName).isJsonNull()) {
            throw new IllegalArgumentException("Missing field: " + fieldName);
        }

        return json.get(fieldName).getAsString();
    }
}
