package com.alphalxy.test;

import java.io.Serializable;
import java.lang.annotation.Annotation;

/**
 * @author AlphaLxy
 * @since 1.0.0
 */
public interface PublicInterface<A extends Number, B extends Serializable, C> {
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

    default byte testReturnByte() {
        return 1;
    }

    default short testReturnShort() {
        return 2;
    }

    default char testReturnChar() {
        return 3;
    }

    default int testReturnInt() {
        return 4;
    }

    default boolean testReturnBool() {
        return true;
    }

    default float testReturnFloat() {
        return 1.0F;
    }

    default double testReturnDouble() {
        return 2.0D;
    }

}
