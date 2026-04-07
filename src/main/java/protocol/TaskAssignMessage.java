package protocol;

public class TaskAssignMessage extends Message {

    private String taskId;
    private String inputPath;
    private String outputPath;
    private String targetFormat;

    public TaskAssignMessage(String taskId, String inputPath, String outputPath, String targetFormat) {
        this.type = MessageType.TASK_ASSIGN;
        this.taskId = taskId;
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.targetFormat = targetFormat;
    }

    public TaskAssignMessage() {
        this.type = MessageType.TASK_ASSIGN;
    }

    public String getTaskId()      { return taskId; }
    public String getInputPath()   { return inputPath; }
    public String getOutputPath()  { return outputPath; }
    public String getTargetFormat(){ return targetFormat; }
}
