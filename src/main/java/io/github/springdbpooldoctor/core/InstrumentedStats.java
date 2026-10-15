package io.github.springdbpooldoctor.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Bounded, thread-safe per-pool instrumentation statistics.
 * Latency samples are kept in fixed-size rings; open borrows are tracked in a
 * bounded deque so a misbehaving application cannot grow memory unbounded.
 */
public class InstrumentedStats {

    private static final int RING_CAPACITY = 512;
    private static final int MAX_OPEN_BORROWS = 4096;

    private final long[] acquisitionRing = new long[RING_CAPACITY];
    private final long[] holdRing = new long[RING_CAPACITY];
    private int acquisitionIdx;
    private int acquisitionSize;
    private int holdIdx;
    private int holdSize;

    private final LongAdder acquisitionSuccess = new LongAdder();
    private final LongAdder acquisitionFailure = new LongAdder();
    private final LongAdder stackTracesCaptured = new LongAdder();
    private final ConcurrentLinkedDeque<BorrowRecord> openBorrows = new ConcurrentLinkedDeque<>();
    private final AtomicLong openBorrowCount = new AtomicLong();
    private final AtomicLong stackSampleCounter = new AtomicLong();

    private final double stackSampleRate;

    public InstrumentedStats(double stackSampleRate) {
        this.stackSampleRate = Math.max(0.0, Math.min(1.0, stackSampleRate));
    }

    public void recordAcquisition(long nanos, boolean success) {
        synchronized (acquisitionRing) {
            acquisitionRing[acquisitionIdx] = nanos;
            acquisitionIdx = (acquisitionIdx + 1) % RING_CAPACITY;
            if (acquisitionSize < RING_CAPACITY) acquisitionSize++;
        }
        if (success) acquisitionSuccess.increment(); else acquisitionFailure.increment();
    }

    /** Registers a borrow; returns null if the open-borrow bound is reached. */
    public BorrowRecord borrowStarted() {
        if (openBorrowCount.get() >= MAX_OPEN_BORROWS) return null;
        StackTraceElement[] stack = sampleStack() ? captureStack() : null;
        BorrowRecord record = new BorrowRecord(System.nanoTime(), System.currentTimeMillis(), stack);
        openBorrows.add(record);
        openBorrowCount.incrementAndGet();
        return record;
    }

    public void borrowReturned(BorrowRecord record) {
        if (record == null) return;
        if (openBorrows.remove(record)) openBorrowCount.decrementAndGet();
        synchronized (holdRing) {
            holdRing[holdIdx] = System.nanoTime() - record.borrowNanos();
            holdIdx = (holdIdx + 1) % RING_CAPACITY;
            if (holdSize < RING_CAPACITY) holdSize++;
        }
    }

    private boolean sampleStack() {
        if (stackSampleRate <= 0.0) return false;
        if (stackSampleRate >= 1.0) return true;
        // deterministic sub-sampling: capture roughly 1/rate-th borrow
        long mod = Math.max(1, Math.round(1.0 / stackSampleRate));
        return stackSampleCounter.incrementAndGet() % mod == 0;
    }

    private StackTraceElement[] captureStack() {
        stackTracesCaptured.increment();
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int from = Math.min(6, stack.length);
        int to = Math.min(from + 30, stack.length);
        return Arrays.copyOfRange(stack, from, to);
    }

    public InstrumentedSnapshot snapshot(long leakThresholdMs) {
        long[] acq;
        synchronized (acquisitionRing) { acq = Arrays.copyOf(acquisitionRing, acquisitionSize); }
        long[] hold;
        synchronized (holdRing) { hold = Arrays.copyOf(holdRing, holdSize); }
        Arrays.sort(acq);
        Arrays.sort(hold);

        long now = System.nanoTime();
        int overThreshold = 0;
        List<BorrowRecord> oldest = new ArrayList<>();
        for (BorrowRecord b : openBorrows) {
            if (b.ageMs(now) >= leakThresholdMs) {
                overThreshold++;
                if (oldest.size() < 3) oldest.add(b);
            }
        }
        return new InstrumentedSnapshot(
                acq.length, percentileMs(acq, 50), percentileMs(acq, 95), percentileMs(acq, 99),
                hold.length, percentileMs(hold, 50), percentileMs(hold, 95), percentileMs(hold, 99),
                acquisitionSuccess.sum(), acquisitionFailure.sum(),
                openBorrowCount.get(), overThreshold, List.copyOf(oldest));
    }

    private static double percentileMs(long[] sorted, int pct) {
        if (sorted.length == 0) return 0.0;
        int idx = Math.min(sorted.length - 1, (int) Math.ceil(pct / 100.0 * sorted.length) - 1);
        return sorted[Math.max(0, idx)] / 1_000_000.0;
    }
}
