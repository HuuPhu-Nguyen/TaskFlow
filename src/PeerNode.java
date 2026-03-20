import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class PeerNode {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("incorrect number of arguments given: " + args.length);
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String resource = args[2];

        try (Socket socket = new Socket(host, port)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // String HTTPRequest = "GET " + resource + " HTTP/1.1\n" + "Host: " + host + "\n\n";
            String HTTPRequest = "helloworld";

            out.writeBytes(HTTPRequest);
            String responseLine;
            while ((responseLine = in.readLine()) != null) {
                System.out.println(responseLine);
            }
        } catch (IOException e) {
            System.err.println("Error connecting to " + host + ": " + e.getMessage());
        }
    }
}
