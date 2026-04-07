package server;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;

import protocol.TaskAssignMessage;
import server.registry.PeerInfo;
import server.registry.PeerRegistry;

public class JobDispatcher implements Runnable {

    private static final Set<String> READABLE_FORMATS = Set.of("jpg", "jpeg", "png", "bmp", "gif", "wbmp", "pdf");

    private final PeerRegistry registry;
    private final Path inputFolder;
    private final String targetFormat;
    private final Gson gson = new Gson();

    private final AtomicInteger taskIdCounter = new AtomicInteger(0);
    private final AtomicInteger completed     = new AtomicInteger(0);
    private final AtomicInteger failed        = new AtomicInteger(0);

    public JobDispatcher(PeerRegistry registry, Path inputFolder, String targetFormat) {
        this.registry     = registry;
        this.inputFolder  = inputFolder;
        this.targetFormat = targetFormat;
    }

    @Override
    public void run() {
        System.out.println("JobDispatcher: waiting for at least one peer...");
        while (!Thread.currentThread().isInterrupted()) {
            if (!registry.getAllPeers().isEmpty()) break;
            try { Thread.sleep(500); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        List<Path> images = collectImages();
        if (images.isEmpty()) {
            System.out.println("JobDispatcher: no convertible images found in " + inputFolder);
            return;
        }

        Path outputDir = inputFolder.resolve("converted");
        System.out.printf("JobDispatcher: dispatching %d image(s) → %s into %s%n",
                images.size(), targetFormat.toUpperCase(), outputDir);

        for (Path src : images) {
            PeerInfo peer = pickLeastLoaded();
            if (peer == null) {
                System.err.println("JobDispatcher: no available peers — aborting remaining tasks");
                return;
            }

            String taskId    = "task-" + taskIdCounter.incrementAndGet();
            String outName   = stripExtension(src.getFileName().toString()) + "." + targetFormat;
            String outputPath = outputDir.resolve(outName).toAbsolutePath().toString();

            TaskAssignMessage task = new TaskAssignMessage(
                    taskId, src.toAbsolutePath().toString(), outputPath, targetFormat);

            try {
                PrintWriter out = new PrintWriter(peer.getSocket().getOutputStream(), true);
                out.println(gson.toJson(task));
                peer.incrementTasks();
                System.out.printf("  [%s] %s → %s%n", peer.getNodeId(), src.getFileName(), outName);
            } catch (IOException e) {
                System.err.printf("JobDispatcher: send failed to %s (%s)%n", peer.getNodeId(), e.getMessage());
            }
        }

        System.out.println("JobDispatcher: all tasks dispatched.");
    }

    /** Called by the scheduler thread when a TASK_RESULT arrives. */
    public void onResult(String taskId, String status, String outputPath, String error) {
        if ("OK".equals(status)) {
            completed.incrementAndGet();
            System.out.printf("  ✓ [%s] → %s%n", taskId, outputPath);
        } else {
            failed.incrementAndGet();
            System.err.printf("  ✗ [%s] error: %s%n", taskId, error);
        }
        System.out.printf("    Progress: %d done, %d failed of %d dispatched%n",
                completed.get(), failed.get(), taskIdCounter.get());
    }

    private List<Path> collectImages() {
        List<Path> result = new ArrayList<>();
        try (var stream = Files.walk(inputFolder, 1)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> isConvertible(p))
                  .forEach(result::add);
        } catch (IOException e) {
            System.err.println("JobDispatcher: cannot read folder: " + e.getMessage());
        }
        return result;
    }

    private boolean isConvertible(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1);
        // Skip files that are already in the target format
        return READABLE_FORMATS.contains(ext) && !ext.equals(targetFormat.toLowerCase())
                && !ext.equals(normalizeJpeg(targetFormat));
    }

    private PeerInfo pickLeastLoaded() {
        return registry.getAllPeers().stream()
                .filter(p -> !p.getSocket().isClosed())
                .min(Comparator.comparingInt(PeerInfo::getActiveTasks))
                .orElse(null);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** Treat "jpeg" and "jpg" as the same target to avoid re-converting jpg→jpeg. */
    private static String normalizeJpeg(String fmt) {
        return fmt.equalsIgnoreCase("jpeg") ? "jpg" : fmt.toLowerCase();
    }
}
