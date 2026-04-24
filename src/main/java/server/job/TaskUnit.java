package server.job;

public abstract class TaskUnit<T> {
    protected final String taskId;
    protected final String jobId;
    protected final T payload;

    protected TaskStatus status = TaskStatus.PENDING;
    protected String assignedPeerId;

    private long startTime;

    private int retryCount;

    public enum TaskStatus {
        PENDING, ASSIGNED, COMPLETED, FAILED
    }

    public TaskUnit(String taskId, String jobId, T payload) {
        this.taskId = taskId;
        this.jobId = jobId;
        this.payload = payload;
        this.retryCount = 0;
        this.status= TaskStatus.PENDING;
    }

    // IDEMPOTENT Logic: Only returns true if this call actually changed status to COMPLETED
    public synchronized boolean markCompleted() {
        if (this.status == TaskStatus.COMPLETED) {
            return false; // Already finished, ignore duplicate
        }
        this.status = TaskStatus.COMPLETED;
        return true;
    }

    public synchronized void markAssigned(String peerId) {
        this.assignedPeerId = peerId;
        this.status = TaskStatus.ASSIGNED;
    }

    public synchronized void resetToPending() {
        this.assignedPeerId = null;
        this.status = TaskStatus.PENDING;
    }


    public TaskStatus getStatus() {
        return status;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setStartTime(long time) { this.startTime = time; }
    public long getStartTime() { return this.startTime; }

    public T getPayload() {
        return payload;
    }

    public String getAssignedPeerId() {
        return assignedPeerId;
    }
    public void incrementRetryCount() {
        this.retryCount++;
        if (this.retryCount == 20) {
            this.status = TaskStatus.FAILED;
        }
    }
    public int getRetryCount() {
        return retryCount;
    }
    public String getJobId(){
        return jobId;
    }
}