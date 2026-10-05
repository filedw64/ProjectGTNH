package moze_intel.projecte.network;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Dispatch packet-triggered EMC operations to the server tick thread. */
public final class ServerEMCUpdates {
    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private ServerEMCUpdates() {}
    public static void enqueue(Runnable task) { QUEUE.add(task); }
    public static void drain() {
        Runnable task;
        int budget = 256;
        while (budget-- > 0 && (task = QUEUE.poll()) != null) task.run();
    }
}
