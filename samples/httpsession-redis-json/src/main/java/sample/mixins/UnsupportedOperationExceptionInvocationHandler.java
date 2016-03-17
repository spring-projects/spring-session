package sample.mixins;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author jitendra on 8/3/16.
 */
public class UnsupportedOperationExceptionInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        throw new UnsupportedOperationException(method + " is not supported");
    }
}
