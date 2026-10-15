package io.github.springdbpooldoctor.instrumented;

import io.github.springdbpooldoctor.core.BorrowRecord;
import io.github.springdbpooldoctor.core.InstrumentedStats;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;

/**
 * Invocation handler behind a Connection proxy. Records hold duration on the
 * first close(); all other calls delegate to the real connection unchanged.
 * JDBC contract details (unwrap/isWrapperFor/equals/hashCode/idempotent close)
 * are preserved explicitly.
 */
final class InstrumentedConnectionHandler implements InvocationHandler {

    private final Connection delegate;
    private final BorrowRecord borrow;
    private final InstrumentedStats stats;
    private boolean closed;

    private InstrumentedConnectionHandler(Connection delegate, BorrowRecord borrow, InstrumentedStats stats) {
        this.delegate = delegate;
        this.borrow = borrow;
        this.stats = stats;
    }

    static Connection wrap(Connection delegate, BorrowRecord borrow, InstrumentedStats stats) {
        return (Connection) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new InstrumentedConnectionHandler(delegate, borrow, stats));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        switch (name) {
            case "close":
                if (!closed) {
                    closed = true;
                    stats.borrowReturned(borrow);
                }
                break;
            case "isClosed":
                // fall through to delegate; closed flag mirrors it anyway
                break;
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "InstrumentedConnection[" + delegate + "]";
            default:
                break;
        }
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
