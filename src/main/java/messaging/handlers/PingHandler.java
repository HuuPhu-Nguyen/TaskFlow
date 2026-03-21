package messaging.handlers;

import com.google.gson.Gson;
import messaging.MessageHandler;
import protocol.Message;
import protocol.PingMessage;
import protocol.PongMessage;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PingHandler implements MessageHandler {

    private Gson gson = new Gson();
    private DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void handle(Message message, PrintWriter out) {

        PingMessage ping = (PingMessage) message;

        PongMessage response = new PongMessage(
                ping.getNodeId(),
                LocalDateTime.now().format(formatter)
        );

        out.println(gson.toJson(response));

        System.out.println("Handled PING → sent PONG");
    }
}