package protocol;

public class TaskResultMessage extends Message {

    private String taskId;
    private String status;
    private String outputPath;
    private String error;

    public TaskResultMessage(String taskId, String status, String outputPath, String error) {
        this.type = MessageType.TASK_RESULT;
        this.taskId = taskId;
        this.status = status;
        this.outputPath = outputPath;
        this.error = error;
    }

    public TaskResultMessage() {
        this.type = MessageType.TASK_RESULT;
    }

    public String getTaskId()    { return taskId; }
    public String getStatus()    { return status; }
    public String getOutputPath(){ return outputPath; }
    public String getError()     { return error; }
}
