package org.codelibs.elasticsearch.abtest.exception;

public class AbTestException extends RuntimeException {
    public AbTestException() {
        super();
    }

    public AbTestException(final String msg) {
        super(msg);
    }

    public AbTestException(final Throwable t) {
        super(t);
    }

    public AbTestException(final String msg, final Throwable t) {
        super(msg, t);
    }
}
