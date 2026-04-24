package server.job;

import protocol.JobSubmitMessage;
import protocol.TaskAssignMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class EmbarrassinglyParallelJob<T, R> {
    protected final String jobId;
    protected final String requesterNodeId;
    protected final String taskType;

    // Mapping TaskID to the internal tracking unit
    protected final Map<String, TaskUnit<T>> tasks = new ConcurrentHashMap<>();

    protected final AtomicInteger completedCount = new AtomicInteger(0);

    public EmbarrassinglyParallelJob(String jobId, String requesterNodeId, String taskType) {
        this.jobId = jobId;
        this.requesterNodeId = requesterNodeId;
        this.taskType = taskType;
    }

    public abstract void initializeTasks(JobSubmitMessage message);

    /**
     * IDEMPOTENT UPDATE:
     * Uses the nested TaskStatus via TaskUnit and handles duplicates.
     */
    public synchronized boolean recordResult(String taskId, Object rawResultData) {
        TaskUnit<T> task = tasks.get(taskId);

        // 1. Basic Validation
        if (task == null || task.getStatus() == TaskUnit.TaskStatus.COMPLETED) {
            return false; // Result ignored: either non-existent or already done
        }

        // 2. Mark it. Only the first thread through the door gets 'true'
        boolean wasFreshSuccess = task.markCompleted();

        if (wasFreshSuccess) {
            // 3. Transform the raw network string into your generic result R
            R resultData = parseResult(rawResultData);

            // 4. Trigger the concrete job's specific storage logic
            onTaskSuccess(task, resultData);

            // 5. Atomic state update
            completedCount.incrementAndGet();
            return true;
        }

        return false;
    }

    protected abstract void onTaskSuccess(TaskUnit<T> task, R resultData);

    public boolean isJobComplete() {
        return !tasks.isEmpty() && completedCount.get() == tasks.size();
    }

    public abstract List<Object> aggregateAndSendResult();

    protected abstract R parseResult(Object payloads);

    public List<TaskUnit<T>> getPendingTasks() {
        return tasks.values().stream()
                .filter(t -> t.getStatus() == TaskUnit.TaskStatus.PENDING)
                .toList();
    }

    public String getJobId() {
        return jobId;
    }

    public String getTaskType(){
        return taskType;
    }

    public Map<String, TaskUnit<T>> getTasks() {
        return tasks;
    }

    public String getRequesterNodeId() {
        return requesterNodeId;
    }

    public abstract TaskAssignMessage createTaskAssignMessage(TaskUnit<?> task);
}