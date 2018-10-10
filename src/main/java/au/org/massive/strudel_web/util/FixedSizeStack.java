package au.org.massive.strudel_web.util;

import java.util.Stack;

/**
 * A stack with maximum size, where elements at the bottom of the stack are removed
 * if excess elements are added
 */
public class FixedSizeStack<T> extends Stack<T> {

    private int maxSize;

    public FixedSizeStack(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public T push(T object) {
        while (size() >= maxSize) {
            remove(0);
        }
        return super.push(object);
    }
}
