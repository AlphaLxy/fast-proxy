package com.alphalxy.test;

import java.io.Serializable;
import java.lang.annotation.Annotation;

/**
 * @author AlphaLxy
 * @since 1.0.0
 */
public class PublicTarget implements PublicInterface {
    @Override
    public void testVoid() {
    }

    @Override
    public long testPrimitive(String p1, Object p2, Annotation p3, int p4, String p5, String p6, String p7, String p8,
                              String p9, String p10, String p11, String p12, float p13, boolean p14, char p15,
                              double p16, byte p17, short p18, int p19, long p20) throws Throwable {
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
