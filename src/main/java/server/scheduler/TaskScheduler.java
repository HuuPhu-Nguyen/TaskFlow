package server.scheduler;

import protocol.*;
import server.db.DatabaseManager;
import server.job.*;
import server.model.MessageEnvelope;
import server.registry.*;
import java.util.*;
import java.util.concurrent.*;

public class TaskScheduler implements Runnable {
    private final BlockingQueue<MessageEnvelope> inboundMailbox;
    private final PeerRegistry registry;
    private final DatabaseManager db;
    private final Map<String, EmbarrassinglyParallelJob<?,?>> activeJobs = Collections.synchronizedMap(new LinkedHashMap<>());

    public TaskScheduler(BlockingQueue<MessageEnvelope> mailbox, PeerRegistry registry, DatabaseManager db) {
        this.inboundMailbox = mailbox;
        this.registry = registry;
        this.db = db;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                //Process new messages (results or new jobs)
                MessageEnvelope envelope = inboundMailbox.poll(500, TimeUnit.MILLISECONDS);
                if (envelope != null) {
                    handleMessage(envelope);
                }
                //Check for stale tasks (Watchdog)
                checkTimeouts();
                // Dispatch pending work
                dispatchPendingTasks();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        long timeoutLimit = 60_000; // 60 seconds
        for (EmbarrassinglyParallelJob<?,?> job : activeJobs.values()) {
            // We only care about tasks that were assigned but aren't done yet
            job.getTasks().values().stream()
                    .filter(t -> t.getStatus() == TaskUnit.TaskStatus.ASSIGNED)
                    .filter(t -> (now - t.getStartTime()) > timeoutLimit)
                    .forEach(task -> {
                        System.err.println("Task timeout detected: " + task.getTaskId());
                        //Penalize the peer in the registry
                        PeerInfo peer = registry.get(task.getAssignedPeerId());
                        if (peer != null) {
                            peer.decrementTasks(); // Reduce load count so we can send to others
                            peer.incrementFailedTasks();
                        }
                        //Reset the task so dispatchPendingTasks picks it up again
                        task.resetToPending();
                        task.incrementRetryCount();
                        if (db != null) {
                            if (task.getStatus() == TaskUnit.TaskStatus.FAILED) db.markTaskFailed(task.getTaskId());
                            else db.markTaskRetried(task.getTaskId(), task.getRetryCount());
                        }
                    });
        }
    }

    private void handleMessage(MessageEnvelope envelope) {
        Message msg = envelope.message();

        if (msg instanceof JobSubmitMessage submit) {
            try {
                EmbarrassinglyParallelJob<?,?> job = JobFactory.create(submit, envelope.fromNodeId());
                job.initializeTasks(submit);
                activeJobs.put(job.getJobId(), job);
                System.out.println("[Scheduler] Started " + job.getTaskType() + " Job: " + job.getJobId());

                if (db != null) {
                    db.insertJob(job.getJobId(), job.getTaskType(), job.getRequesterNodeId(), job.getTasks().size());
                    for (String taskId : job.getTasks().keySet()) {
                        db.insertTask(taskId, job.getJobId());
                    }
                }
            } catch (Exception e) {
                System.err.println("[Scheduler] Failed to create job: " + e.getMessage());
            }
        }
        else if (msg instanceof TaskResultMessage result) {
            EmbarrassinglyParallelJob<?, ?> job = activeJobs.get(result.getJobId());
            if (job != null) {
                TaskUnit<?> task = job.getTasks().get(result.getTaskId());
                //Scheduler handles the Timing/Metrics (Agnostic of Data Types)
                if(!result.isSuccessful()){
                    System.err.println("Peer reported failure: " + result.getErrorMessage());
                    if (task == null || task.getStatus() == TaskUnit.TaskStatus.COMPLETED) {
                        return;
                    }
                    //Penalize the peer in the registry
                    PeerInfo peer = registry.get(envelope.fromNodeId());
                    if (peer == null && task.getAssignedPeerId() != null) {
                        peer = registry.get(task.getAssignedPeerId());
                    }
                    if (peer != null) {
                        peer.decrementTasks(); // Reduce load count so we can send to others
                        peer.incrementFailedTasks();
                    }
                    //Reset the task so dispatchPendingTasks picks it up again
                    task.resetToPending();
                    task.incrementRetryCount();
                    if (db != null) {
                        if (task.getStatus() == TaskUnit.TaskStatus.FAILED) db.markTaskFailed(task.getTaskId());
                        else db.markTaskRetried(task.getTaskId(), task.getRetryCount());
                    }
                    return;
                }
                if (task != null && task.getStartTime() > 0) {
                    long now = System.currentTimeMillis();
                    long duration = now - task.getStartTime();
                    PeerInfo peer = registry.get(envelope.fromNodeId());
                    if (peer != null) {
                        peer.updateTaskMetrics(duration);
                        peer.decrementTasks();
                    }
                    if (db != null) db.markTaskCompleted(task.getTaskId(), now, duration);
                }
                if (job.recordResult(result.getTaskId(), result.getResultPayload())) {
                    if (job.isJobComplete()) {
                        //Get the list of JSON-encoded results from the job
                        List<Object> finalData = job.aggregateAndSendResult();

                        // 2. Construct the high-level protocol message
                        // We use the requesterId stored in the job to find the original sender
                        JobResultMessage response = new JobResultMessage(
                                "COORDINATOR",
                                java.time.Instant.now().toString(),
                                job.getJobId(),
                                job.getTaskType(),
                                true,
                                finalData
                        );

                        // 3. Find the peer and send the message
                        PeerInfo requester = registry.get(job.getRequesterNodeId());
                        if (requester != null) {
                            requester.send(response);
                            System.out.println("[Scheduler] Job " + job.getJobId() + " sent to " + requester.getNodeId());
                        } else {
                            System.err.println("[Scheduler] Requester " + job.getRequesterNodeId() + " not found for Job " + job.getJobId());
                        }

                        // 4. Cleanup
                        activeJobs.remove(job.getJobId());
                        if (db != null) db.markJobCompleted(job.getJobId());
                    }
                }
            }
        }
    }

    private void dispatchPendingTasks() {
        //Get our candidates for work
        List<PeerInfo> candidates = getAvailablePeers();
        if (candidates.isEmpty()) return;

        //Process jobs in order
        for (EmbarrassinglyParallelJob<?,?> job : activeJobs.values()) {
            //Prioritize high-retry tasks
            List<? extends TaskUnit<?>> pending = job.getPendingTasks().stream()
                    .sorted(Comparator.comparingInt((TaskUnit<?> t) -> t.getRetryCount()).reversed())
                    .toList();

            for (TaskUnit<?> task : pending) {
                // Find the first peer in our sorted list who still has room.
                PeerInfo bestPeer = candidates.stream()
                        .filter(p -> p.getActiveTasks() < 3)
                        .findFirst()
                        .orElse(null);

                if (bestPeer != null) {
                    assign(job, task, bestPeer);
                } else {
                    return; // All peers have hit the limit of 3
                }
            }
        }
    }

    private void assign(EmbarrassinglyParallelJob<?,?> job, TaskUnit<?> task, PeerInfo peer) {
        task.markAssigned(peer.getNodeId());
        task.setStartTime(System.currentTimeMillis());
        peer.incrementTasks();
        TaskAssignMessage message = job.createTaskAssignMessage(task);
        peer.send(message);
        if (db != null) db.markTaskAssigned(task.getTaskId(), peer.getNodeId(), task.getStartTime());
    }

    private List<PeerInfo> getAvailablePeers() {
        return registry.getAllPeers().stream()
                // 1. Filter out dead or over-encumbered peers
                .filter(p -> !p.getSocket().isClosed())
                .filter(p -> p.getActiveTasks() < 3)

                // 2. Sort by our composite score (Lowest score = Best peer)
                .sorted(Comparator.comparingDouble(PeerInfo::getSelectionScore))
                .toList();
    }
}
