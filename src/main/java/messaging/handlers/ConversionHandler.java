package messaging.handlers;

import com.google.gson.Gson;
import messaging.MessageHandler;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import protocol.Message;
import protocol.TaskAssignMessage;
import protocol.TaskResultMessage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConversionHandler implements MessageHandler {

    private static final float PDF_RENDER_DPI = 150f;
    private final Gson gson = new Gson();

    @Override
    public void handle(Message message, PrintWriter out) {
        TaskAssignMessage task = (TaskAssignMessage) message;
        System.out.printf("Converting [%s]: %s -> %s%n",
                task.getTaskId(), task.getInputPath(), task.getTargetFormat().toUpperCase());

        try {
            Files.createDirectories(Paths.get(task.getOutputPath()).getParent());

            if (task.getInputPath().toLowerCase().endsWith(".pdf")) {
                convertPdf(task, out);
            } else {
                convertImage(task, out);
            }
        } catch (Exception e) {
            System.err.println("Conversion failed [" + task.getTaskId() + "]: " + e.getMessage());
            out.println(gson.toJson(new TaskResultMessage(
                    task.getTaskId(), "ERROR", null, e.getMessage())));
        }
    }

    private void convertPdf(TaskAssignMessage task, PrintWriter out) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(task.getInputPath()))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            String firstOutputPath = null;

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, PDF_RENDER_DPI, ImageType.RGB);
                String outPath = pageCount == 1
                        ? task.getOutputPath()
                        : insertPageSuffix(task.getOutputPath(), i);
                if (i == 0) firstOutputPath = outPath;
                ImageIO.write(image, task.getTargetFormat().toLowerCase(), new File(outPath));
                System.out.printf("  Page %d/%d -> %s%n", i + 1, pageCount, outPath);
            }

            System.out.println("Done: " + pageCount + " page(s)");
            out.println(gson.toJson(new TaskResultMessage(
                    task.getTaskId(), "OK", firstOutputPath, null)));
        }
    }

    private void convertImage(TaskAssignMessage task, PrintWriter out) throws Exception {
        BufferedImage image = ImageIO.read(new File(task.getInputPath()));
        if (image == null) {
            throw new IllegalArgumentException("Unreadable image: " + task.getInputPath());
        }

        String fmt = task.getTargetFormat().toLowerCase();
        if (fmt.equals("jpg") || fmt.equals("jpeg")) {
            BufferedImage rgb = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(image, 0, 0, Color.WHITE, null);
            image = rgb;
        }

        ImageIO.write(image, fmt, new File(task.getOutputPath()));
        System.out.println("Done: " + task.getOutputPath());
        out.println(gson.toJson(new TaskResultMessage(
                task.getTaskId(), "OK", task.getOutputPath(), null)));
    }

    /** "test.png" + page 2 -> "test-page-2.png" */
    private static String insertPageSuffix(String outputPath, int pageIndex) {
        int dot = outputPath.lastIndexOf('.');
        if (dot < 0) return outputPath + "-page-" + pageIndex;
        return outputPath.substring(0, dot) + "-page-" + pageIndex + outputPath.substring(dot);
    }
}
