package com.alphalxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * {@link MethodInterceptor} is the interface implemented by
 * the <i>method interceptor</i> of a proxy instance.
 *
 * <p>Each proxy instance has an associated method interceptor in.
 * When a method is invoked on a proxy instance, the method invocation
 * is encoded and dispatched to the {@link MethodInterceptor#intercept}
 * method of its method interceptor.
 *
 * <p>Similar to {@link InvocationHandler} but more efficient by replace
 * {@link Method#invoke} by {@link MethodInvoker#invoke}.
 *
 * @author AlphaLxy
 * @see MethodInvoker
 * @since 1.0.0
 */
@FunctionalInterface
public interface MethodInterceptor {
    /**
     * Processes a method invocation on a proxy instance and returns the result.
     * This method will be invoked on an interceptor when a method is invoked
     * on a proxy instance that it is associated with.
     * <p>Similar to {@link InvocationHandler} but more efficient. Should use
     * {@link MethodInvoker#invoke(Object, Object[])} instead of
     * {@link Method#invoke(Object, Object...)}.
     *
     * @param proxy   the proxy instance that the method was invoked on.
     * @param method  method the {@code Method} instance corresponding to
     *                the interface method invoked on the proxy instance.  The declaring
     *                class of the {@code Method} object will be the interface that
     *                the method was declared in, which may be a superinterface of the
     *                proxy interface that the proxy class inherits the method through.
     * @param invoker method invoker that represent the method invocation.
     * @param args    an array of objects containing the values of the arguments
     *                passed in the method invocation on the proxy instance,
     *                or {@code new Object[]} if interface method takes no arguments.
     *                Arguments of primitive types are wrapped in instances of the
     *                appropriate primitive wrapper class,
     *                such as {@code java.lang.Integer} or {@code java.lang.Boolean}.
     * @return the value to return from the method invocation on the proxy instance.
     * If the declared return type of the interface method is a primitive type,
     * then the value returned by this method must be an instance of the corresponding
     * primitive wrapper class; otherwise, it must be a type assignable to the declared
     * return type. If the value returned by this method is {@code null} and the interface
     * method's return type is primitive, then a {@code NullPointerException} will be
     * thrown by the method invocation on the proxy instance. If the value returned by
     * this method is otherwise not compatible with the interface method's declared
     * return type as described above, a {@code ClassCastException} will be thrown by the
     * method invocation on the proxy instance.
     * @throws Throwable the exception to throw from the method invocation on the proxy instance
     * without wrapping in an {@link InvocationTargetException}.
     */
    Object intercept(Object proxy, Method method, MethodInvoker invoker, Object[] args) throws Throwable;
}
