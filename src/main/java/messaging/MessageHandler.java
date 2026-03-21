package messaging;

import protocol.Message;
import java.io.PrintWriter;

public interface MessageHandler {
    void handle(Message message, PrintWriter out);
}