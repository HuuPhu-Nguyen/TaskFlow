package server.concreteJobs.conversion;

import protocol.FilePayload;
import server.job.TaskUnit;

/**
 * Task unit for video transcoding jobs.
 */
public class VideoTaskUnit extends TaskUnit<FilePayload> {
    private final String targetFormat;

    public VideoTaskUnit(String taskId, String jobId, FilePayload payload, String targetFormat) {
        super(taskId, jobId, payload);
        this.targetFormat = targetFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }
}