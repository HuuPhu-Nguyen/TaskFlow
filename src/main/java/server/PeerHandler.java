package server;

import com.google.gson.Gson;
import messaging.MessageDispatcher;
import messaging.MessageFactory;
import messaging.handlers.PongHandler;
import protocol.Message;
import protocol.MessageType;
import protocol.PongMessage;

import protocol.PingMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.io.*;
import java.net.Socket;

public class PeerHandler implements Runnable {

    private final Socket socket;

    public PeerHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        Gson gson = new Gson();
        MessageFactory factory = createFactory(gson);
        MessageDispatcher dispatcher = createDispatcher();
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("Handling peer: " + socket.getRemoteSocketAddress());
            while (true) {

                // 🔥 Send PING
                PingMessage ping = new PingMessage(
                        socket.getLocalAddress().getHostAddress(),
                        LocalDateTime.now().format(formatter)
                );

                out.println(gson.toJson(ping));
                System.out.println("Sent PING to " + socket.getRemoteSocketAddress());

                // 🔥 Read response
                String incomingJson = in.readLine();
                if (incomingJson == null) break;

                try {
                    Message msg = factory.fromJson(incomingJson);
                    dispatcher.dispatch(msg, out);

                } catch (Exception e) {
                    System.err.println("Error processing message: " + incomingJson);
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Connection lost: " + socket.getRemoteSocketAddress());
        }
    }

    private MessageFactory createFactory(Gson gson) {
        MessageFactory factory = new MessageFactory();

        factory.register(MessageType.PONG,
                json -> gson.fromJson(json, PongMessage.class));

        return factory;
    }

    private MessageDispatcher createDispatcher() {
        MessageDispatcher dispatcher = new MessageDispatcher();

        dispatcher.register(MessageType.PONG, new PongHandler());

        return dispatcher;
    }
}