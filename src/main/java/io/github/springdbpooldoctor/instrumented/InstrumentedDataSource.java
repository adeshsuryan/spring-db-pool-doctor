package io.github.springdbpooldoctor.instrumented;

import io.github.springdbpooldoctor.core.BorrowRecord;
import io.github.springdbpooldoctor.core.InstrumentedStats;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Decorating DataSource that measures getConnection() latency and the duration
 * each borrowed connection is held. Fails open: if instrumentation itself
 * throws, the real connection is still returned untouched.
 */
public class InstrumentedDataSource implements DataSource {

    private final DataSource delegate;
    private final InstrumentedStats stats;

    public InstrumentedDataSource(DataSource delegate, InstrumentedStats stats) {
        this.delegate = delegate;
        this.stats = stats;
    }

    public DataSource getDelegate() { return delegate; }
    public InstrumentedStats getStats() { return stats; }

    @Override
    public Connection getConnection() throws SQLException {
        long start = System.nanoTime();
        try {
            Connection real = delegate.getConnection();
            stats.recordAcquisition(System.nanoTime() - start, true);
            return wrapInstrumented(real);
        } catch (SQLException e) {
            stats.recordAcquisition(System.nanoTime() - start, false);
            throw e;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        long start = System.nanoTime();
        try {
            Connection real = delegate.getConnection(username, password);
            stats.recordAcquisition(System.nanoTime() - start, true);
            return wrapInstrumented(real);
        } catch (SQLException e) {
            stats.recordAcquisition(System.nanoTime() - start, false);
            throw e;
        }
    }

    private Connection wrapInstrumented(Connection real) {
        try {
            BorrowRecord borrow = stats.borrowStarted();
            return InstrumentedConnectionHandler.wrap(real, borrow, stats);
        } catch (RuntimeException instrumentationFailure) {
            return real; // fail open — never block the real connection path
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface == InstrumentedDataSource.class) return iface.cast(this);
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface == InstrumentedDataSource.class || delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }

    @Override
    public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
