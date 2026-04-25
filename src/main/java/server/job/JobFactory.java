package server.job;

import protocol.JobSubmitMessage;
import server.concreteJobs.conversion.ImageConversionJob;
import server.concreteJobs.conversion.VideoTranscodingJob;

/**
 * The JobFactory is the central point for creating concrete job objects.
 * It is placed here in server.job as it is part of the core job orchestration logic.
 */
public class JobFactory {

    public static EmbarrassinglyParallelJob<?,?> create(JobSubmitMessage msg, String requesterId) {
        String type = msg.getTaskType();

        // The factory "knows" about concrete implementations and maps the string type to the class
        if ("IMAGE_CONVERSION".equalsIgnoreCase(type)) {
            // msg.getParameters() holds the targetFormat (e.g., "PNG")
            return new ImageConversionJob(
                    msg.getJobId(),
                    requesterId,
                    msg.getParameter()
            );
        }

        if ("VIDEO_TRANSCODING".equalsIgnoreCase(type)) {
            return new VideoTranscodingJob(
                    msg.getJobId(),
                    requesterId,
                    msg.getParameter()
            );
        }

        // To add a new job type (e.g., "PDF_MERGE"), just add another 'if' block here
        throw new IllegalArgumentException("Unsupported job type: " + type);
    }
}