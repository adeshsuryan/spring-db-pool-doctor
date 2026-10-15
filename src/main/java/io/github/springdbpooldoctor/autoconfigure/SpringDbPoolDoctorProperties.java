package io.github.springdbpooldoctor.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.db-pool-doctor")
public class SpringDbPoolDoctorProperties {

    /** Master switch for pool monitoring. */
    private boolean enabled = true;
    /** Interval between pool-statistic scans. */
    private long scanIntervalMs = 10000;
    /** Active/max percentage that produces a WARNING finding. */
    private int activePercentWarning = 80;
    /** Active/max percentage that produces a CRITICAL finding. */
    private int activePercentCritical = 95;
    /** Emit an INFO finding when no rule fires. */
    private boolean logHealthySnapshots = false;

    /** Wrap DataSource beans to measure acquisition and hold time. */
    private boolean instrumented = false;
    /** Fraction of borrows that capture a stack trace (0.0-1.0). */
    private double stackSampleRate = 0.1;
    /** Acquisition p95 above this produces ACQUISITION_LATENCY_HIGH. */
    private long acquisitionWarnMs = 3000;
    /** Hold p95 above this produces CONNECTION_HOLD_DURATION_HIGH. */
    private long holdWarnMs = 2000;
    /** A borrow open longer than this may be a leak candidate. */
    private long leakThresholdMs = 60000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getScanIntervalMs() { return scanIntervalMs; }
    public void setScanIntervalMs(long value) { this.scanIntervalMs = Math.max(1000, value); }
    public int getActivePercentWarning() { return activePercentWarning; }
    public void setActivePercentWarning(int value) { this.activePercentWarning = clamp(value); }
    public int getActivePercentCritical() { return activePercentCritical; }
    public void setActivePercentCritical(int value) { this.activePercentCritical = clamp(value); }
    public boolean isLogHealthySnapshots() { return logHealthySnapshots; }
    public void setLogHealthySnapshots(boolean value) { this.logHealthySnapshots = value; }

    public boolean isInstrumented() { return instrumented; }
    public void setInstrumented(boolean instrumented) { this.instrumented = instrumented; }
    public double getStackSampleRate() { return stackSampleRate; }
    public void setStackSampleRate(double value) { this.stackSampleRate = Math.max(0.0, Math.min(1.0, value)); }
    public long getAcquisitionWarnMs() { return acquisitionWarnMs; }
    public void setAcquisitionWarnMs(long value) { this.acquisitionWarnMs = Math.max(10, value); }
    public long getHoldWarnMs() { return holdWarnMs; }
    public void setHoldWarnMs(long value) { this.holdWarnMs = Math.max(10, value); }
    public long getLeakThresholdMs() { return leakThresholdMs; }
    public void setLeakThresholdMs(long value) { this.leakThresholdMs = Math.max(1000, value); }

    private int clamp(int value) { return Math.max(1, Math.min(100, value)); }
}
