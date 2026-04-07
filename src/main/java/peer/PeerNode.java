package peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import com.google.gson.Gson;

import messaging.MessageDispatcher;
import messaging.MessageFactory;
import messaging.handlers.ConversionHandler;
import messaging.handlers.PingHandler;
import protocol.Message;
import protocol.MessageType;
import protocol.PingMessage;
import protocol.TaskAssignMessage;

public class PeerNode {

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Usage: java peer.PeerNode <host> <port>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        Gson gson = new Gson();

        MessageFactory factory = createFactory(gson);
        MessageDispatcher dispatcher = createDispatcher();

        System.out.println("Attempting to connect to Coordinator at " + host + ":" + port);

        try (Socket socket = new Socket(host, port)) {

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Connected! Waiting for messages...");

            String incomingJson;

            while ((incomingJson = in.readLine()) != null) {
                try {
                    if (incomingJson.trim().isEmpty()) continue;

                    Message msg = factory.fromJson(incomingJson);
                    dispatcher.dispatch(msg, out);

                } catch (Exception jsonEx) {
                    System.err.println("Error parsing incoming message: " + incomingJson);
                    jsonEx.printStackTrace(); //LATER USE SLF4j
                }
            }

            System.out.println("Server closed connection.");

        } catch (IOException e) {
            System.err.println("Connection lost or server not found: " + e.getMessage());
        }
    }

    private static MessageFactory createFactory(Gson gson) {
        MessageFactory factory = new MessageFactory();
        factory.register(MessageType.PING,
                json -> gson.fromJson(json, PingMessage.class));
        factory.register(MessageType.TASK_ASSIGN,
                json -> gson.fromJson(json, TaskAssignMessage.class));
        return factory;
    }

    private static MessageDispatcher createDispatcher() {
        MessageDispatcher dispatcher = new MessageDispatcher();
        dispatcher.register(MessageType.PING, new PingHandler());
        dispatcher.register(MessageType.TASK_ASSIGN, new ConversionHandler());
        return dispatcher;
    }
}