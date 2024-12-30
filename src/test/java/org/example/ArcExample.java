package org.example;

import org.arc.Arc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ArcExample {
    public static void main(String[] args) {
        Arc.ArcPool<ExpensiveObject> arcPool = new Arc.ArcPool<>(ExpensiveObject::new, 512);

        Runner runner = new Runner(arcPool, 5);

        ArcConsumer consumer1 = new ArcConsumer(1);
        ArcConsumer consumer2 = new ArcConsumer(2);
        ArcConsumer consumer3 = new ArcConsumer(5);

        ArcProducer producer = new ArcProducer(arcPool, 2);
        producer.consumers.add(consumer1);
        producer.consumers.add(consumer2);
        producer.consumers.add(consumer3);

        // Start multiple threads
        Thread[] threads = new Thread[5];
        threads[0] = new Thread(consumer1);
        threads[1] = new Thread(consumer2);
        threads[2] = new Thread(producer);
        threads[3] = new Thread(runner);
        threads[4] = new Thread(consumer3);

        for (Thread value : threads) {
            value.start();
        }
        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Objects created = " + ExpensiveObject.counter.get());
        System.out.println("Arc pool size = " + arcPool.getPoolSize());
    }

    static class ExpensiveObject {
        final static AtomicInteger counter = new AtomicInteger();
        final int id = counter.incrementAndGet();
        final MutableBuilder mutableBuilder = new MutableBuilder();
        boolean exitSignal;

        void doSomething(long taskDuration) {
            try {
                Thread.sleep(taskDuration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        boolean isExitSignal() {
            return exitSignal;
        }

        class MutableBuilder {
            void setExitSignal() {
                exitSignal = true;
            }

            MutableBuilder clear() {
                exitSignal = false;
                return this;
            }
        }
    }

    static class Runner implements Runnable {
        private final Arc.ArcPool<ExpensiveObject> pool;
        private final long taskDuration;

        Runner(Arc.ArcPool<ExpensiveObject> pool, long taskDuration) {
            this.pool = pool;
            this.taskDuration = taskDuration;
        }

        @Override
        public void run() {
            int total = 1001;
            for (int i = 0; i < total; i++) {
                try (Arc<ExpensiveObject> arc = pool.acquire()) {
                    ExpensiveObject object = arc.get();
                    object.mutableBuilder.clear();
                    object.doSomething(taskDuration);
                    if (object.isExitSignal()) {
                        throw new IllegalStateException("Runner should never see an exit signal");
                    }
                }
                if (i % 100 == 0) {
                    System.out.println(Thread.currentThread().getName() + ": Total runner operations = " + i);
                }
            }
            System.out.println(Thread.currentThread().getName() + ": Runner exiting");
        }
    }

    static class ArcConsumer implements Runnable {
        final ConcurrentLinkedQueue<Arc<ExpensiveObject>> queue = new ConcurrentLinkedQueue<>();
        final long taskDuration;

        ArcConsumer(long taskDuration) {
            this.taskDuration = taskDuration;
        }

        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + ": Starting consumer");
            while (true) {
                Arc<ExpensiveObject> item;
                int workCount = 0;
                boolean exit = false;
                while ((item = queue.poll()) != null) {
                    try (Arc<ExpensiveObject> arc = item) {
                        ExpensiveObject object = arc.get();
                        //System.out.println(Thread.currentThread().getName() + ": Using object id " + object.id);
                        object.doSomething(taskDuration);
                        exit |= object.isExitSignal();
                    }
                    workCount++;
                    if (workCount % 100 == 0) {
                        System.out.println(Thread.currentThread().getName() + ": Consumer workCount = " + workCount);
                    }
                }
                if (workCount == 0) {
                    try {
                        Thread.sleep(taskDuration);
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                } else {
                    System.out.println(Thread.currentThread().getName() + ": Consumer total workCount = " + workCount);
                }
                if (exit) {
                    System.out.println(Thread.currentThread().getName() + ": Consumer exiting");
                    break;
                }
            }
        }
    }

    static class ArcProducer implements Runnable {
        final List<ArcConsumer> consumers = new ArrayList<>();
        final Arc.ArcPool<ExpensiveObject> pool;
        final long delay;

        ArcProducer(Arc.ArcPool<ExpensiveObject> arcPool, long delay) {
            this.pool = arcPool;
            this.delay = delay;
        }

        @Override
        public void run() {
            int total = 1001;
            for (int i = 0; i < total; i++) {
                try (Arc<ExpensiveObject> arc = pool.acquire()) {
                    if (i == total - 1) {
                        System.out.println(Thread.currentThread().getName() + ": Setting exit signal");
                        ExpensiveObject.MutableBuilder builder = arc.get().mutableBuilder;
                        builder.clear()
                                .setExitSignal();
                    }
                    for (ArcConsumer consumer : consumers) {
                        arc.copy();
                        consumer.queue.offer(arc);
                    }
                }
                if (i % 100 == 0) {
                    System.out.println(Thread.currentThread().getName() + ": Total producer operations = " + i);
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }
    }
}