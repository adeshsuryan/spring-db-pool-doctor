package io.github.springdbpooldoctor.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.db-pool-doctor")
public class SpringDbPoolDoctorProperties {
    private boolean enabled = true;
    private long scanIntervalMs = 10000;
    private int activePercentWarning = 80;
    private int activePercentCritical = 95;
    private boolean logHealthySnapshots = false;

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
    private int clamp(int value) { return Math.max(1, Math.min(100, value)); }
}
