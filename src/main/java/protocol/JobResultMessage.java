package protocol;

import java.util.List;

public class JobResultMessage extends Message {

    private String jobId;
    private String taskType;
    private boolean successful;
    private List<Object> resultsByTaskId;

    public JobResultMessage(String nodeId, String time,
                            String jobId, String taskType,
                            boolean successful,
                            List<Object> resultsByTaskId) {
        this.type = MessageType.JOB_RESULT;
        this.nodeId = nodeId;
        this.time = time;
        this.jobId = jobId;
        this.taskType = taskType;
        this.successful = successful;
        this.resultsByTaskId = resultsByTaskId;
    }

    public JobResultMessage() {
        this.type = MessageType.JOB_RESULT;
    }

    public String getJobId() {
        return jobId;
    }

    public String getTaskType() {
        return taskType;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public List<Object> getResultsByTaskId() {return resultsByTaskId;}
}