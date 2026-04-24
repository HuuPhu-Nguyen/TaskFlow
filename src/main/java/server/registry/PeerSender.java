package server.registry;
import protocol.Message;

@FunctionalInterface
public interface PeerSender {
    void send(Message message);
}