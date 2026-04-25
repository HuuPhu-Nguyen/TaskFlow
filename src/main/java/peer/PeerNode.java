package peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import messaging.MessageDispatcher;
import messaging.MessageFactory;
import messaging.handlers.PingHandler;
import peer.engine.PeerExecutionEngine;
import peer.processors.ImageConversionProcessor;
import peer.processors.VideoTranscodingProcessor;
import protocol.*;

public class PeerNode {

    private final Set<String> myActiveJobIds = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java peer.PeerNode <host> <port>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Gson gson = new Gson();

        //Initialize the Execution Engine
        PeerExecutionEngine engine = new PeerExecutionEngine("PEER");

        //Register specialized processors
        engine.registerProcessor("IMAGE_CONVERSION", new ImageConversionProcessor());
        engine.registerProcessor("VIDEO_TRANSCODING", new VideoTranscodingProcessor());

        MessageFactory factory = createFactory(gson);

        System.out.println("Connecting to Coordinator at " + host + ":" + port);

        try (Socket socket = new Socket(host, port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            //Create dispatcher with the engine and the shared output stream
            MessageDispatcher dispatcher = createDispatcher(engine, out);

            System.out.println("Connected! Node ID recognized by Server as: " + socket.getLocalSocketAddress());

            String incomingJson;
            while ((incomingJson = in.readLine()) != null) {
                try {
                    if (incomingJson.trim().isEmpty()) continue;

                    // Convert raw JSON to protocol Message
                    Message msg = factory.fromJson(incomingJson);

                    // Dispatch to either the PingHandler or the Execution Engine
                    dispatcher.dispatch(msg, out);

                } catch (Exception e) {
                    System.err.println("Error processing message: " + e.getMessage());
                }
            }
            System.out.println("Server closed connection.");

        } catch (IOException e) {
            System.err.println("Connection lost: " + e.getMessage());
        }
    }

    public String submitImageJob(List<FilePayload> payloads, String format, PrintWriter out) {
        //Generate the unique ID
        String jobId = "JOB_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

        myActiveJobIds.add(jobId);

        //Construct the Message Object
        JobSubmitMessage msg = new JobSubmitMessage(
                "CLIENT",
                java.time.Instant.now().toString(),
                jobId,
                "IMAGE_CONVERSION",
                new ArrayList<>(payloads),
                format
        );
        // Serialize to JSON and Send via the PrintWriter
        // Synchronize to prevent multiple threads from overlapping text lines
        synchronized (out) {
            String jsonMessage = new Gson().toJson(msg);
            out.println(jsonMessage);
            // No need for out.flush() here because auto-flush is true
        }
        return jobId;
    }

    public String submitVideoJob(List<FilePayload> payloads, String format, PrintWriter out) {
        //Generate the unique ID
        String jobId = "JOB_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

        myActiveJobIds.add(jobId);

        //Construct the Message Object
        JobSubmitMessage msg = new JobSubmitMessage(
                "CLIENT",
                java.time.Instant.now().toString(),
                jobId,
                "VIDEO_TRANSCODING",
                new ArrayList<>(payloads),
                format
        );
        // Serialize to JSON and Send via the PrintWriter
        // Synchronize to prevent multiple threads from overlapping text lines
        synchronized (out) {
            String jsonMessage = new Gson().toJson(msg);
            out.println(jsonMessage);
            // No need for out.flush() here because auto-flush is true
        }
        return jobId;
    }

    private static MessageFactory createFactory(Gson gson) {
        MessageFactory factory = new MessageFactory();
        factory.register(MessageType.PING, json -> gson.fromJson(json, PingMessage.class));
        factory.register(MessageType.TASK_ASSIGN, json -> gson.fromJson(json, TaskAssignMessage.class));
        factory.register(MessageType.JOB_RESULT, json -> gson.fromJson(json, JobResultMessage.class));
        return factory;
    }

    private static MessageDispatcher createDispatcher(PeerExecutionEngine engine, PrintWriter out) {
        MessageDispatcher dispatcher = new MessageDispatcher();
        // Static handler for immediate responses (PING)
        dispatcher.register(MessageType.PING, new PingHandler());
        // TASK_ASSIGN -> Background Engine
        // prevents the networking thread from blocking during image conversion
        dispatcher.register(MessageType.TASK_ASSIGN, (message, writer) -> {
            TaskAssignMessage task = (TaskAssignMessage) message;
            engine.submitTask(task, out);
        });

        //Handling Job Results (If this peer submitted a folder)
        //dispatcher.register(MessageType.JOB_RESULT, new messaging.handlers.JobResultHandler());

        return dispatcher;
    }
}