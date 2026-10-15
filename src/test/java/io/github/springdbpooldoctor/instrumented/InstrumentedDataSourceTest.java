package io.github.springdbpooldoctor.instrumented;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.springdbpooldoctor.core.InstrumentedStats;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class InstrumentedDataSourceTest {

    @Test
    void measuresAcquisitionAndHoldDuration() throws Exception {
        DataSource delegate = mock(DataSource.class);
        Connection real = mock(Connection.class);
        when(delegate.getConnection()).thenReturn(real);

        InstrumentedStats stats = new InstrumentedStats(1.0);
        InstrumentedDataSource ds = new InstrumentedDataSource(delegate, stats);

        Connection conn = ds.getConnection();
        assertNotNull(conn);
        assertEquals(1, stats.snapshot(60_000).openBorrowCount());

        conn.close();
        conn.close(); // idempotent close

        var snap = stats.snapshot(60_000);
        assertEquals(0, snap.openBorrowCount());
        assertEquals(1, snap.holdSamples());
        assertEquals(1, snap.acquisitionSamples());
        assertEquals(1, snap.acquisitionSuccessCount());
        assertEquals(0, snap.acquisitionFailureCount());
    }

    @Test
    void unwrapAndIsWrapperForDelegate() throws Exception {
        DataSource delegate = mock(DataSource.class);
        when(delegate.isWrapperFor(DataSource.class)).thenReturn(true);
        when(delegate.unwrap(DataSource.class)).thenReturn(delegate);

        InstrumentedDataSource ds = new InstrumentedDataSource(delegate, new InstrumentedStats(0.0));
        assertTrue(ds.isWrapperFor(DataSource.class));
        assertSame(delegate, ds.unwrap(DataSource.class));
        assertSame(ds, ds.unwrap(InstrumentedDataSource.class));
    }

    @Test
    void acquisitionFailurePropagatesAndIsCounted() throws Exception {
        DataSource delegate = mock(DataSource.class);
        when(delegate.getConnection()).thenThrow(new SQLException("refused"));

        InstrumentedStats stats = new InstrumentedStats(0.0);
        InstrumentedDataSource ds = new InstrumentedDataSource(delegate, stats);

        assertThrows(SQLException.class, ds::getConnection);
        assertEquals(1, stats.snapshot(60_000).acquisitionFailureCount());
    }

    @Test
    void leakThresholdCountsAgedBorrows() throws Exception {
        DataSource delegate = mock(DataSource.class);
        Connection real = mock(Connection.class);
        when(delegate.getConnection()).thenReturn(real);

        InstrumentedStats stats = new InstrumentedStats(1.0);
        InstrumentedDataSource ds = new InstrumentedDataSource(delegate, stats);
        ds.getConnection();
        Thread.sleep(30);
        assertEquals(1, stats.snapshot(10).borrowsOverLeakThreshold());
        assertEquals(0, stats.snapshot(60_000).borrowsOverLeakThreshold());
    }

    @Test
    void borrowSiteStackCapturedWhenSampling() throws Exception {
        DataSource delegate = mock(DataSource.class);
        when(delegate.getConnection()).thenReturn(mock(Connection.class));

        InstrumentedStats stats = new InstrumentedStats(1.0);
        new InstrumentedDataSource(delegate, stats).getConnection();
        Thread.sleep(30);
        var snap = stats.snapshot(10);
        assertFalse(snap.oldestOverThreshold().isEmpty());
        assertNotNull(snap.oldestOverThreshold().get(0).borrowStack());
    }
}
