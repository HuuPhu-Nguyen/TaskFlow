package gui;

import com.google.gson.Gson;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import messaging.MessageDispatcher;
import messaging.MessageFactory;
import peer.PeerNode;
import peer.engine.PeerExecutionEngine;
import peer.processors.ImageConversionProcessor;
import protocol.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class PeerApp extends Application {
    private Stage window;
    private PeerExecutionEngine engine;
    private final Gson gson = new Gson();
    private final java.util.Set<String> myActiveJobIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Shared output stream for thread-safe communication
    private PeerNode backendNode;
    private PrintWriter socketOut;

    private String currentInPath;
    private String currentOutPath;
    private String sessionId;
    private TilePane gallery;

    @Override
    public void init() {
        try {
            // Generate unique folders for this GUI instance
            this.sessionId = "PEER_" + (System.currentTimeMillis() % 100000);
            this.currentInPath = "java/in_" + sessionId;
            this.currentOutPath = "java/out_" + sessionId;

            FileUtils.prepareDirectories(currentInPath, currentOutPath);

            // Initialize Engine with the same unique ID
            engine = new PeerExecutionEngine(sessionId);
            engine.registerProcessor("IMAGE_CONVERSION", new ImageConversionProcessor());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.window = primaryStage;
        showConnectionScreen();
    }

    private void showConnectionScreen() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        TextField hostField = new TextField("localhost");
        TextField portField = new TextField("6789");
        Button connectBtn = new Button("Connect to Coordinator");

        connectBtn.setOnAction(e -> {
            connectBtn.setDisable(true);
            startNetworkThread(
                    hostField.getText(),
                    Integer.parseInt(portField.getText()),
                    () -> Platform.runLater(this::showMainGallery),
                    error -> Platform.runLater(() -> {
                        connectBtn.setDisable(false);
                        new Alert(Alert.AlertType.ERROR,
                                "Could not connect to coordinator: " + error).show();
                    }));
        });

        root.getChildren().addAll(new Label("Host:"), hostField, new Label("Port:"), portField, connectBtn);
        window.setScene(new Scene(root, 300, 250));
        window.setTitle("Connect Peer");
        window.show();
    }

    private void showMainGallery() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        HBox topBar = new HBox(10);
        ComboBox<String> formatBox = new ComboBox<>();
        formatBox.getItems().addAll("PNG", "JPG", "BMP", "GIF");
        formatBox.setValue("PNG");

        Button uploadBtn = new Button("Upload Files");
        Button startBtn = new Button("Start Conversion");
        topBar.getChildren().addAll(new Label("Target:"), formatBox, uploadBtn, startBtn);

        gallery = new TilePane();
        gallery.setHgap(10);
        gallery.setVgap(10);
        gallery.setPadding(new Insets(15));
        ScrollPane scroll = new ScrollPane(gallery);
        scroll.setFitToWidth(true);

        root.setTop(topBar);
        root.setCenter(scroll);

        // UPLOAD: Copy selected files to the local session 'in' folder
        startBtn.setOnAction(e -> {
            try {
                if (socketOut == null) {
                    new Alert(Alert.AlertType.ERROR,
                            "Not connected to the coordinator yet.").show();
                    return;
                }

                File folder = new File(currentInPath);
                File[] files = folder.listFiles();
                if (files == null || files.length == 0) return;

                List<FilePayload> payloads = new ArrayList<>();
                for (File f : files) {
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    payloads.add(new FilePayload(f.getName(), Base64.getEncoder().encodeToString(bytes)));
                }

                String targetFormat = formatBox.getValue().toLowerCase();
                String jobId = backendNode.submitImageJob(payloads, targetFormat, socketOut);

                if (jobId != null) {
                    myActiveJobIds.add(jobId);
                    System.out.println("GUI: Job submitted via PeerNode. ID: " + jobId);

                    // --- THE WIPE LOGIC ---

                    // 1. Clear the Gallery (UI)
                    gallery.getChildren().clear();

                    // 2. Clear the local 'in' folder (Filesystem)
                    for (File f : files) {
                        if (!f.delete()) {
                            System.err.println("Could not delete temporary file: " + f.getName());
                        }
                    }

                    // Optional: Give the user feedback that conversion has started
                    new Alert(Alert.AlertType.CONFIRMATION, "Conversion Started! The gallery will be cleared.").show();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        uploadBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Images or PDFs");
            List<File> selectedFiles = fileChooser.showOpenMultipleDialog(window);

            if (selectedFiles != null) {
                for (File file : selectedFiles) {
                    try {
                        // Copy the file to the local session folder so the Start button can find it
                        Files.copy(file.toPath(), Paths.get(currentInPath, file.getName()), StandardCopyOption.REPLACE_EXISTING);

                        // Create a visual card in the gallery
                        VBox fileCard = new VBox(5);
                        fileCard.setStyle("-fx-border-color: #ccc; -fx-padding: 5; -fx-background-color: #eee;");
                        fileCard.getChildren().add(new Label(file.getName()));
                        gallery.getChildren().add(fileCard);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        window.setScene(new Scene(root, 800, 600));
        window.show();
    }

    /**
     * Synchronized method to ensure JSON messages don't interleave on the socket.
     */
    private void sendSafe(Object message) {
        if (socketOut != null) {
            synchronized (socketOut) {
                socketOut.println(gson.toJson(message));
            }
        }
    }

    private void startNetworkThread(String host, int port, Runnable onConnected, java.util.function.Consumer<String> onFailed) {
        backendNode = new PeerNode();
        Thread netThread = new Thread(() -> {
            try (Socket socket = new Socket(host, port)) {
                socketOut = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                MessageFactory factory = createFactory();
                MessageDispatcher dispatcher = createDispatcher(engine, socketOut);
                onConnected.run();

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    Message msg = factory.fromJson(line);
                    dispatcher.dispatch(msg, socketOut);
                }
            } catch (IOException e) {
                socketOut = null;
                onFailed.accept(e.getMessage());
                Platform.runLater(() -> System.err.println("Connection lost: " + e.getMessage()));
            }
        });
        netThread.setDaemon(true);
        netThread.start();
    }

    private MessageFactory createFactory() {
        MessageFactory factory = new MessageFactory();
        factory.register(MessageType.PING, json -> gson.fromJson(json, PingMessage.class));
        factory.register(MessageType.TASK_ASSIGN, json -> gson.fromJson(json, TaskAssignMessage.class));
        factory.register(MessageType.JOB_RESULT, json -> gson.fromJson(json, JobResultMessage.class));
        return factory;
    }

    private MessageDispatcher createDispatcher(PeerExecutionEngine engine, PrintWriter out) {
        MessageDispatcher dispatcher = new MessageDispatcher();
        dispatcher.register(MessageType.PING, new messaging.handlers.PingHandler());
        dispatcher.register(MessageType.TASK_ASSIGN, (message, writer) -> {
            // Offload to worker pool
            engine.submitTask((TaskAssignMessage) message, out);
        });
        dispatcher.register(MessageType.JOB_RESULT, (message, writer) -> {
            JobResultMessage result = (JobResultMessage) message;

            // 1. Check if this GUI instance actually started this job
            if (myActiveJobIds.contains(result.getJobId())) {
                myActiveJobIds.remove(result.getJobId()); // Cleanup

                // 2. IMPORTANT: Switch from Network Thread to UI Thread
                Platform.runLater(() -> {
                    showDownloadWindow(result);
                });
            }
        });
        return dispatcher;
    }

    private void showDownloadWindow(JobResultMessage result) {
        Stage downloadStage = new Stage();
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));

        layout.getChildren().add(new Label("Job Complete! Select a location to save your files."));

        Button saveBtn = new Button("Choose Folder & Save");
        saveBtn.setStyle("-fx-base: #2ecc71; -fx-text-fill: white;");

        saveBtn.setOnAction(e -> {
            // 1. Let the user pick where to save
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Save Location");
            File selectedDirectory = directoryChooser.showDialog(downloadStage);

            if (selectedDirectory != null) {
                // 2. Perform the actual file writing
                saveFilesToDisk(result, selectedDirectory.getAbsolutePath());
                downloadStage.close();

                // Optional: Show a success alert
                new Alert(Alert.AlertType.INFORMATION, "Files saved successfully!").show();
            }
        });

        layout.getChildren().addAll(saveBtn);
        downloadStage.setScene(new Scene(layout, 300, 200));
        downloadStage.setTitle("Download Converted Files");
        downloadStage.show();
    }

    private void saveFilesToDisk(JobResultMessage result, String folderPath) {
        for (Object raw : result.getResultsByTaskId()) {
            // Use the Gson trick to turn the generic Object back into a FilePayload
            FilePayload fp = gson.fromJson(gson.toJson(raw), FilePayload.class);

            try {
                // 1. Decode the Base64 string back into raw bytes
                byte[] data = java.util.Base64.getDecoder().decode(fp.base64Data());

                // 2. Combine the chosen folder with the original filename
                java.nio.file.Path path = java.nio.file.Paths.get(folderPath, fp.fileName());

                // 3. Write the actual file to the hard drive
                java.nio.file.Files.write(path, data);

                System.out.println("[GUI] File saved to: " + path.toAbsolutePath());
            } catch (java.io.IOException ex) {
                System.err.println("[GUI] Failed to save " + fp.fileName() + ": " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

}
