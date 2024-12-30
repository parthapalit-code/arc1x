package org.arc;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A lock-free, garbage-free reference counting container that manages object lifecycle
 * and pooling. The container itself is pooled for maximum memory efficiency.
 *
 * @param <T> The type of object being managed
 */
public final class Arc<T> implements AutoCloseable {

    private static final VarHandle REF_COUNT;

    static {
        try {
            REF_COUNT = MethodHandles.lookup().findVarHandle(Arc.class, "refCount", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ArcPool<T> sourcePool;
    private final Node<Arc<T>> node;  // Reference to this Arc's node for reuse
    private volatile int refCount; // Accessed using varHandle
    private T value;

    private Arc(Node<Arc<T>> node, ArcPool<T> sourcePool) {
        this.node = node;
        this.sourcePool = sourcePool;
        this.refCount = 0;
    }

    public T get() {
        int count = (int) REF_COUNT.getVolatile(this);
        if (count <= 0) {
            throw new ZeroReferenceException();
        }
        return value;
    }

    public Arc<T> copy() {
        int current;
        do {
            current = (int) REF_COUNT.getVolatile(this);
            if (current <= 0) {
                throw new ZeroReferenceException();
            }
            if (current >= Integer.MAX_VALUE - 1) {
                throw new IllegalStateException("Reference count overflow");
            }
        } while (!REF_COUNT.compareAndSet(this, current, current + 1));
        return this;
    }

    void initializeReference() {
        if (!REF_COUNT.compareAndSet(this, 0, 1)) {
            throw new IllegalStateException("Reference count is not zero while initializing");
        }
    }

    @Override
    public void close() {
        release();
    }

    public void release() {
        int current;
        do {
            current = (int) REF_COUNT.getVolatile(this);
            if (current <= 0) {
                throw new IllegalStateException("Reference count underflow");
            }
        } while (!REF_COUNT.compareAndSet(this, current, current - 1));

        if (current == 1) {
            sourcePool.returnObject(this);
        }
    }

    public static class ZeroReferenceException extends IllegalStateException {
        public ZeroReferenceException() {
            super("Attempting to access object with zero references");
        }
    }

    public static final class ArcPool<T> {
        private static final ConcurrentHashMap<String, ArcPool<?>> POOLS = new ConcurrentHashMap<>();
        private final AtomicReference<Node<Arc<T>>> poolHead;
        private final AtomicInteger poolSize;
        private final Supplier<T> factory;
        private final int maxPoolSize;
        private AtomicLong consumerMask;

        public ArcPool(Supplier<T> factory, int maxPoolSize) {
            this.factory = factory;
            this.maxPoolSize = maxPoolSize;
            this.poolHead = new AtomicReference<>(null);
            this.poolSize = new AtomicInteger(0);
            this.consumerMask = new AtomicLong(0);
        }

        public int getPoolSize() {
            return poolSize.get();
        }

        public Arc<T> acquire() {
            Node<Arc<T>> node = popNode(poolHead, poolSize);
            Arc<T> arc;

            if (node != null) {
                arc = node.value;
                if (arc.value == null) {
                    arc.value = factory.get();
                }
            } else {
                node = new Node<>();
                arc = new Arc<>(node, this);
                arc.value = factory.get();
                node.value = arc;
            }

            arc.initializeReference();

            return arc;
        }

        void returnObject(Arc<T> arc) {
            if (poolSize.get() < maxPoolSize) {
                arc.node.next = null;
                pushNode(poolHead, arc.node, poolSize);
            } else {
                arc.value = null;
                arc.node.value = null;
                arc.node.next = null;
            }
        }

        private <V> void pushNode(AtomicReference<Node<V>> head, Node<V> node, AtomicInteger size) {
            Node<V> oldHead;
            do {
                oldHead = head.get();
                node.next = oldHead;
            } while (!head.compareAndSet(oldHead, node));
            size.incrementAndGet();
        }

        private <V> Node<V> popNode(AtomicReference<Node<V>> head, AtomicInteger size) {
            Node<V> oldHead;
            Node<V> newHead;
            do {
                oldHead = head.get();
                if (oldHead == null) {
                    return null;
                }
                newHead = oldHead.next;
            } while (!head.compareAndSet(oldHead, newHead));

            oldHead.next = null;
            size.decrementAndGet();
            return oldHead;
        }

        public long registerConsumer() {
            long oldMask;
            long consumerBit;
            long newMask;
            do {
                oldMask = consumerMask.get();
                consumerBit = findNextWithDifferentLeftmostBit(oldMask);
                newMask = oldMask | consumerBit;
            } while (!consumerMask.compareAndSet(oldMask, newMask));
            return consumerBit;
        }

        public static long findNextWithDifferentLeftmostBit(long v1) {
            if (v1 == 0) return 1;  // Special case for 0

            // Find the position of leftmost set bit
            int leftmostBitPos = 63 - Long.numberOfLeadingZeros(v1);

            // Create a mask with all 1s up to leftmostBitPos
            long mask = (1L << (leftmostBitPos + 1)) - 1;

            // Clear all bits from leftmostBitPos and set the next bit
            return (v1 & ~mask) | (1L << (leftmostBitPos + 1));
        }
    }

    private static final class Node<T> {
        T value;
        volatile Node<T> next;
    }
}
