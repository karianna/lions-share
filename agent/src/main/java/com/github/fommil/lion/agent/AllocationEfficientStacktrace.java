package com.github.fommil.lion.agent;

import lombok.NoArgsConstructor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static lombok.AccessLevel.PRIVATE;

/**
 * Trades the cost of constructing too large a stack trace against
 * reflective lookup of Throwable internals. Tries its best to
 * minimise the creation of new objects --- an essential requirement
 * in instrumentation sampling code paths.
 */
@NoArgsConstructor(access = PRIVATE)
public class AllocationEfficientStacktrace extends Throwable {

    public static final long serialVersionUID = 0L;

    /**
     * @param skipFrames to skip at the start
     * @param max        maximum size of the stacktrace
     * @return the stack trace excluding some noise at the start
     */
    public static StackTraceElement[] stack(int skipFrames, int max) {
        try {
            return new AllocationEfficientStacktrace().trace(skipFrames + 1, max);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Method getStackTraceDepth;
    private static final Method getStackTraceElement;

    static {
        try {
            getStackTraceDepth = Throwable.class.getDeclaredMethod("getStackTraceDepth");
            getStackTraceDepth.setAccessible(true);
            getStackTraceElement = Throwable.class.getDeclaredMethod("getStackTraceElement", Integer.TYPE);
            getStackTraceElement.setAccessible(true);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final StackTraceElement[] EMPTY = new StackTraceElement[0];

    StackTraceElement[] trace(int skip, int max) throws InvocationTargetException, IllegalAccessException {
        int depth = (Integer) getStackTraceDepth.invoke(this);
        int size = Math.min(depth - skip, max);
        if (size < 1) return EMPTY;

        StackTraceElement[] trace = new StackTraceElement[size];
        for (int i = 0; i < (depth - skip) && i < size; i++) {
            StackTraceElement el = (StackTraceElement) getStackTraceElement.invoke(this, i + skip);
            trace[i] = el;
        }
        return trace;
    }
}
