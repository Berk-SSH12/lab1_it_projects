import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

class Process {
    private final String queueName;
    private final int id;

    public Process(String queueName, int id) {
        this.queueName = queueName;
        this.id = id;
    }

    public void run() {
        System.out.println(queueName + " " + id);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class GenerateProcess extends Thread {
    private final String queueName;
    private final BlockingQueue<Process> queue;
    private final int processInterval;
    private final AtomicBoolean stop;

    public GenerateProcess(String queueName,
                           BlockingQueue<Process> queue,
                           int processInterval,
                           AtomicBoolean stop) {
        this.queueName = queueName;
        this.queue = queue;
        this.processInterval = processInterval;
        this.stop = stop;
    }

    @Override
    public void run() {
        for (int i = 0; !stop.get(); i++) {
            queue.add(new Process(queueName, i));
            try {
                Thread.sleep(processInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class Processor extends Thread {
    public final AtomicInteger queue1MaxSizeReached = new AtomicInteger(0);
    public final AtomicInteger queue2MaxSizeReached = new AtomicInteger(0);
    private final BlockingQueue<Process> firstQueue;
    private final BlockingQueue<Process> secondQueue;
    private final AtomicBoolean stop;

    public Processor(BlockingQueue<Process> firstQueue,
                     BlockingQueue<Process> secondQueue,
                     AtomicBoolean stop) {
        this.firstQueue = firstQueue;
        this.secondQueue = secondQueue;
        this.stop = stop;
    }

    @Override
    public void run() {
        while (!stop.get()) {
            Process process = null;
            queue1MaxSizeReached.set(Math.max(queue1MaxSizeReached.get(), firstQueue.size()));
            queue2MaxSizeReached.set(Math.max(queue2MaxSizeReached.get(), secondQueue.size()));

            if (firstQueue.isEmpty()) {
                process = secondQueue.poll();
            } else {
                process = firstQueue.poll();
            }

            if (process != null) {
                process.run();
            }
        }
    }
}

class Computer implements Runnable {
    public final AtomicBoolean stop = new AtomicBoolean(false);
    public final BlockingQueue<Process> queue1;
    public final BlockingQueue<Process> queue2;
    private final Processor processor;

    public Computer() {
        queue1 = new LinkedBlockingQueue<>();
        queue2 = new LinkedBlockingQueue<>();
        processor = new Processor(queue1, queue2, stop);
    }

    @Override
    public void run() {
        processor.start();
    }

    public void join() throws InterruptedException {
        processor.join();
    }

    public void stopAfter(int milliseconds) {
        try (var scheduler = Executors.newSingleThreadScheduledExecutor()) {
            scheduler.schedule(() -> stop.set(true), milliseconds, TimeUnit.MILLISECONDS);
            scheduler.shutdown();
        }
    }

    public int getQueue1MaxSizeReached() {
        return processor.queue1MaxSizeReached.get();
    }

    public int getQueue2MaxSizeReached() {
        return processor.queue2MaxSizeReached.get();
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        var computer = new Computer();
        var generator1 = new GenerateProcess("firstQueue", computer.queue1, 100, computer.stop);
        var generator2 = new GenerateProcess("secondQueue", computer.queue2, 20, computer.stop);

        computer.run();
        generator1.start();
        generator2.start();

        computer.stopAfter(5000);

        generator1.join();
        generator2.join();
        computer.join();

        System.out.println("Hello world");
        System.out.println("Hello world22222");

        System.out.println("Queue Max 1 Size Reached: " + computer.getQueue1MaxSizeReached());
        System.out.println("Queue Max 2 Size Reached: " + computer.getQueue2MaxSizeReached());
    }
}