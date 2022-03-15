package com.alphalxy;

import java.lang.invoke.LambdaMetafactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * {@link MethodInvoker} is the interface that represent a method invocation.
 * {@link MethodInvoker#invoke(Object, Object[])} is similar to {@link Method#invoke(Object, Object...)}
 * but implemented by {@code invokeDynamic} and {@link LambdaMetafactory#metafactory}.
 *
 * @author AlphaLxy
 * @see MethodInterceptor
 * @since 1.0.0
 */
@FunctionalInterface
public interface MethodInvoker {
    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     * Individual parameters are automatically unwrapped to match primitive
     * formal parameters, and both primitive and reference parameters
     * are subject to method invocation conversions as necessary.
     * <p>Similar to {@link Method#invoke(Object, Object...)}.
     * <pre>
     * public interface Foo {
     *     String bar(String string);
     *     void baz(String string);
     * }
     * MethodInvoker bar = (target, args) -> ((Foo)target).bar((String)args[0]);
     * MethodInvoker baz = (target, args) -> {((Foo)target).baz((String)args[0]); return null;};
     * </pre>
     *
     * @param target the object the underlying method is invoked from.
     * @param args   the arguments used for the method call.
     * @return the result of dispatching the method represented by
     * this object on {@code obj} with parameters {@code args}
     * @throws Throwable the exception to throw from the method invocation
     * without wrapping in an {@link InvocationTargetException}.
     * @see MethodInterceptor#intercept
     */
    Object invoke(Object target, Object[] args) throws Throwable;
}
