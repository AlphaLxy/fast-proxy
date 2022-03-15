package com.alphalxy.test;

import com.alphalxy.FastProxy;
import com.alphalxy.MethodInterceptor;

import org.junit.Test;

import java.io.Serializable;
import java.lang.annotation.Annotation;

import static org.junit.Assert.*;

/**
 * @author AlphaLxy
 * @since 1.0.0
 */
@SuppressWarnings("rawtypes")
public class FastProxyTest {

    @Test
    public void testPublicProxy() throws Exception {
        ClassLoader classLoader = this.getClass().getClassLoader();
        PublicTarget target = new PublicTarget();
        PublicInterface publicProxy = (PublicInterface)FastProxy.newProxyInstance(classLoader,
            new Class[] {PublicInterface.class},
            (proxy, method, invoker, args) -> invoker.invoke(target, args));
        // default package
        assertTrue(publicProxy.getClass().getName().startsWith("com.alphalxy.$Proxy"));
        assertEquals(classLoader, publicProxy.getClass().getClassLoader());
        assertEquals("hello", publicProxy.testString("hello"));
        assertTrue(FastProxy.isProxyInstance(publicProxy));
        assertTrue(FastProxy.isProxyClass(publicProxy.getClass()));
        publicProxy.testVoid();
        assertFalse(FastProxy.isProxyInstance(new Object()));
        assertFalse(FastProxy.isProxyClass(Object.class));
        assertTrue(publicProxy.testReturnBool());
        assertEquals(1, publicProxy.testReturnByte());
        assertEquals(2, publicProxy.testReturnShort());
        assertEquals(3, publicProxy.testReturnChar());
        assertEquals(4, publicProxy.testReturnInt());
        assertEquals(1.0F, publicProxy.testReturnFloat(), 0.0);
        assertEquals(2.0D, publicProxy.testReturnDouble(), 0.0);
    }

    @Test
    public void testPackageProxy() throws Exception {
        ClassLoader classLoader = this.getClass().getClassLoader();
        PackageTarget target = new PackageTarget();
        PackageInterface packageProxy = (PackageInterface)FastProxy.newProxyInstance(classLoader,
            new Class[] {PackageInterface.class},
            (proxy, method, invoker, args) -> invoker.invoke(target, args));
        // default package
        assertTrue(packageProxy.getClass().getName().startsWith("com.alphalxy.test.$Proxy"));
        assertEquals(classLoader, packageProxy.getClass().getClassLoader());
        assertEquals("hello", packageProxy.testString("hello"));
        assertTrue(FastProxy.isProxyInstance(packageProxy));
        assertTrue(FastProxy.isProxyClass(packageProxy.getClass()));
        packageProxy.testVoid();
    }

    @Test
    public void testPrivateProxy() throws Exception {
        ClassLoader classLoader = this.getClass().getClassLoader();
        PrivateTarget target = new PrivateTarget();
        PrivateInterface privateProxy = (PrivateInterface)FastProxy.newProxyInstance(classLoader,
            new Class[] {PrivateInterface.class},
            (proxy, method, invoker, args) -> invoker.invoke(target, args));
        // default package
        assertTrue(privateProxy.getClass().getName().startsWith("com.alphalxy.test.$Proxy"));
        assertEquals(classLoader, privateProxy.getClass().getClassLoader());
        assertEquals("hello", privateProxy.testString("hello"));
        assertTrue(FastProxy.isProxyInstance(privateProxy));
        assertTrue(FastProxy.isProxyClass(privateProxy.getClass()));
        privateProxy.testVoid();
    }

    @Test
    public void testFail() {
        ClassLoader classLoader = this.getClass().getClassLoader();
        MethodInterceptor interceptor = (proxy, method, invoker, args) -> null;
        try {
            FastProxy.newProxyInstance(classLoader, new Class[] {PackageInterface.class, PrivateInterface.class}, interceptor);
            fail();
        } catch (ClassFormatError ignore) {
            // Duplicate method name
        }
        try {
            FastProxy.newProxyInstance(classLoader, new Class[] {PackageInterface.class, PackageInterface.class}, interceptor);
            fail();
        } catch (IllegalArgumentException ignore) {
        }
        try {
            FastProxy.newProxyInstance(classLoader, new Class[] {Object.class}, interceptor);
            fail();
        } catch (IllegalArgumentException ignore) {
        }
        try {
            FastProxy.newProxyInstance(null, new Class[] {PackageInterface.class, PrivateInterface.class}, interceptor);
            fail();
        } catch (NullPointerException ignore) {
        }
        try {
            FastProxy.newProxyInstance(classLoader, null, interceptor);
            fail();
        } catch (NullPointerException ignore) {
        }
        try {
            FastProxy.newProxyInstance(classLoader, new Class[] {PackageInterface.class, PrivateInterface.class}, null);
            fail();
        } catch (NullPointerException ignore) {
        }
    }

    private interface PrivateInterface<A extends Number, B extends Serializable, C> {
        static String testStatic() {
            return null;
        }

        void testVoid();

        long testPrimitive(String p1, Object p2, Annotation p3, int p4, String p5, String p6,
                           String p7, String p8, String p9, String p10, String p11, String p12,
                           float p13, boolean p14, char p15, double p16, byte p17, short p18, int p19,
                           long p20) throws Throwable;

        String testString(String string) throws Exception;

        <D extends Number, E extends Serializable, F> String testGeneric(A a, B b, C c, D d, E e, F f) throws Exception;
    }

    private static class PrivateTarget implements PrivateInterface {
        @Override
        public void testVoid() {

        }

        @Override
        public long testPrimitive(String p1, Object p2, Annotation p3, int p4, String p5, String p6, String p7,
                                  String p8, String p9, String p10, String p11, String p12, float p13, boolean p14,
                                  char p15, double p16, byte p17, short p18, int p19, long p20) throws Throwable {
            return 0;
        }

        @Override
        public String testString(String string) throws Exception {
            return string;
        }

        @Override
        public String testGeneric(Number number, Serializable serializable, Object o, Number number2,
                                  Serializable serializable2, Object o2) throws Exception {
            return null;
        }
    }
}