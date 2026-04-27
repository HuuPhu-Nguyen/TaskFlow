package server.concreteJobs.conversion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

import protocol.FilePayload;
import protocol.JobSubmitMessage;
import protocol.TaskAssignMessage;
import server.job.EmbarrassinglyParallelJob;
import server.job.TaskUnit;

/**
 * Job class for video transcoding operations.
 * Handles splitting jobs into tasks and aggregating results.
 */
public class VideoTranscodingJob extends EmbarrassinglyParallelJob<FilePayload, FilePayload> {

    private final String targetFormat;
    private final Gson gson = new Gson();
    private final Map<String, FilePayload> results = new ConcurrentHashMap<>();

    public VideoTranscodingJob(String jobId, String requesterId, String targetFormat) {
        super(jobId, requesterId, "VIDEO_TRANSCODING");
        this.targetFormat = targetFormat;
    }

    @Override
    public void initializeTasks(JobSubmitMessage message) {
        List<Object> payloads = message.getTaskPayloads();
        if (payloads == null) return;

        for (int i = 0; i < payloads.size(); i++) {
            String taskId = "task-" + jobId + "-" + i;
            
            // Convert the generic Object back to FilePayload
            Object rawData = payloads.get(i);
            FilePayload filePayload = gson.fromJson(gson.toJson(rawData), FilePayload.class);

            // Wrap it in task unit
            tasks.put(taskId, new ConversionTaskUnit(taskId, jobId, filePayload, targetFormat));
        }
    }

    @Override
    protected void onTaskSuccess(TaskUnit<FilePayload> task, FilePayload resultData) {
        results.put(task.getTaskId(), resultData);
        System.out.println("  Video task stored: " + task.getTaskId());
    }

    @Override
    public List<Object> aggregateAndSendResult() {
        System.out.println("Video Job [" + jobId + "] is finished. Packaging " + results.size() + " files.");
        List<Object> finalPayloads = new ArrayList<>();
        finalPayloads.addAll(results.values());
        return finalPayloads;
    }

    @Override
    protected FilePayload parseResult(Object rawData) {
        return gson.fromJson(gson.toJson(rawData), FilePayload.class);
    }

    @Override
    public TaskAssignMessage createTaskAssignMessage(TaskUnit<?> task) {
        return new TaskAssignMessage(
                "COORDINATOR",
                java.time.Instant.now().toString(),
                task.getTaskId(),
                jobId,
                "VIDEO_TRANSCODING",
                task.getPayload(),
                this.targetFormat);
    }
}