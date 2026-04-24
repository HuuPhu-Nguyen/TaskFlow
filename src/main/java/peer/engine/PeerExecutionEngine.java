package peer.engine;

import com.google.gson.Gson;
import protocol.TaskResultMessage;
import protocol.TaskAssignMessage;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

public class PeerExecutionEngine {
    private final ExecutorService workerPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final Gson gson = new Gson();
    private final String nodeId;
    private final Map<String, TaskProcessor<?>> processors = new ConcurrentHashMap<>();

    public PeerExecutionEngine(String nodeId) {
        this.nodeId = nodeId;
    }

    public void registerProcessor(String type, TaskProcessor<?> processor) {
        processors.put(type, processor);
    }

    public void submitTask(TaskAssignMessage task, PrintWriter out) {
        workerPool.submit(() -> {
            try {
                //Resolve the Processor
                String taskType = task.getTaskType();
                TaskProcessor<?> processor = processors.get(taskType);
                if (processor == null) throw new RuntimeException("No processor: " + taskType);

                // Execute, Serialize, and Send (All in one block)
                // use a helper cast to handle the generic capture inline
                processAndRespond(processor, task, out);

            } catch (Exception e) {
                // Error Path: Notify Coordinator
                TaskResultMessage error = new TaskResultMessage(
                        nodeId, java.time.Instant.now().toString(),
                        task.getTaskId(), task.getJobId(),
                        null, false, e.getMessage()
                );
                synchronized (out) {
                    out.println(gson.toJson(error));
                }
            }
        });
    }

    // Inline helper to satisfy the compiler's need for a named type R
    private <R> void processAndRespond(TaskProcessor<R> processor, TaskAssignMessage task, PrintWriter out) throws Exception {
        // Execute
        R result = processor.process(task);

        // Build and Send Response
        TaskResultMessage response = new TaskResultMessage(
                nodeId, java.time.Instant.now().toString(),
                task.getTaskId(), task.getJobId(),
                result,
                true, null
        );

        synchronized (out) { //Thread-safe write
            out.println(gson.toJson(response));
        }
    }
}