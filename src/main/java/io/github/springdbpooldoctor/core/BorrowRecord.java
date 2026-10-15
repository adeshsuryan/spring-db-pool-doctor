package io.github.springdbpooldoctor.core;

/** Tracks one borrowed connection until it is returned to the pool. */
public final class BorrowRecord {
    private final long borrowNanos;
    private final long borrowWallClockMs;
    private final StackTraceElement[] borrowStack;

    BorrowRecord(long borrowNanos, long borrowWallClockMs, StackTraceElement[] borrowStack) {
        this.borrowNanos = borrowNanos;
        this.borrowWallClockMs = borrowWallClockMs;
        this.borrowStack = borrowStack;
    }

    public long borrowNanos() { return borrowNanos; }
    public long borrowWallClockMs() { return borrowWallClockMs; }
    public StackTraceElement[] borrowStack() { return borrowStack; }
    public long ageMs(long nowNanos) { return (nowNanos - borrowNanos) / 1_000_000L; }
}
