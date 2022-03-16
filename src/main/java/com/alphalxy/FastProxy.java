package com.alphalxy;

import org.objectweb.asm.Type;
import org.objectweb.asm.*;

import java.io.InputStream;
import java.lang.invoke.LambdaMetafactory;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.objectweb.asm.Opcodes.*;

/**
 * {@link FastProxy} provides static methods for creating dynamic proxy
 * classes and instances.
 *
 * <p>Similar to {@link Proxy} but more efficient.
 *
 * <p>To create a proxy for some interface {@code Foo}:
 * <pre>
 * MethodInterceptor interceptor = new MethodInterceptor(...);
 * Foo f = (Foo) FastProxy.newProxyInstance(Foo.class.getClassLoader(),
 *                                          new Class&lt;?&gt;[] { Foo.class },
 *                                          interceptor);
 * </pre>
 *
 * @author AlphaLxy
 * @see MethodInterceptor
 * @see MethodInvoker
 * @since 1.0.0
 */
public class FastProxy {
    /**
     * Returns an instance of a proxy class for the specified interfaces
     * that dispatches method invocations to the specified method interceptor.
     *
     * @param classLoader the class loader to define the proxy class.
     * @param interfaces  the list of interfaces for the proxy class to implement.
     * @param interceptor the method interceptor to dispatch method invocations to.
     * @return a proxy instance with the specified method interceptor of a proxy class that
     * is defined by the specified class loader and that implements the specified interfaces.
     * @throws IllegalArgumentException if any of the restrictions on the parameters are violated.
     * @throws NullPointerException if the {@code interfaces} array argument or any of its elements
     * are {@code null}, or if the method interceptor, {@code interceptor}, is {@code null}.
     */
    public static Object newProxyInstance(ClassLoader classLoader,
                                          Class<?>[] interfaces, MethodInterceptor interceptor) {
        Objects.requireNonNull(classLoader);
        Objects.requireNonNull(interceptor);

        Class<?>[] orderedInterfaces = Arrays.copyOf(interfaces, interfaces.length);
        // order by name and hashCode
        Comparator<Class<?>> comparing = Comparator.comparing(Class::getName);
        Arrays.sort(orderedInterfaces, comparing.thenComparing(Object::hashCode));
        // classLoader + orderedInterfaces
        List<Object> key = new ArrayList<>(orderedInterfaces.length + 1);
        key.add(classLoader);
        key.addAll(Arrays.asList(orderedInterfaces));

        Type proxyType = getProxyType(orderedInterfaces);
        Class<?> proxyClass = PROXY_CLASSES.computeIfAbsent(key, k -> {
            byte[] bytes = newProxyClass(orderedInterfaces, proxyType.getInternalName());
            return defineClass(classLoader, proxyType.getClassName(), bytes);
        });
        Constructor<?> constructor = PROXY_CONSTRUCTORS.computeIfAbsent(proxyClass, k -> {
            try {
                return proxyClass.getConstructor(MethodInterceptor.class);
            } catch (NoSuchMethodException e) {
                throw new InternalError(e);
            }
        });
        try {
            return constructor.newInstance(interceptor);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Returns true if and only if the specified class was dynamically
     * generated to be a proxy class using the {@code newProxyInstance} method.
     *
     * @param cl the class to test.
     * @return {@code true} if the class is a proxy class and {@code false} otherwise.
     * @throws NullPointerException if {@code cl} is {@code null}.
     */
    public static boolean isProxyClass(Class<?> cl) {
        return PROXY_CONSTRUCTORS.containsKey(Objects.requireNonNull(cl));
    }

    /**
     * Returns true if and only if the specified object was dynamically
     * generated to be a proxy class using the {@code newProxyInstance} method.
     *
     * @param object the object to test.
     * @return {@code true} if the object is a proxy instance and {@code false} otherwise.
     * @throws NullPointerException if {@code object} is {@code null}.
     */
    public static boolean isProxyInstance(Object object) {
        return PROXY_CONSTRUCTORS.containsKey(Objects.requireNonNull(object).getClass());
    }

    /**
     * Proxy class counter.
     */
    private static final AtomicLong COUNTER = new AtomicLong(0);
    /**
     * Proxy classes.
     */
    private static final Map<List<Object>, Class<?>> PROXY_CLASSES = new ConcurrentHashMap<>();
    /**
     * Proxy class constructors.
     */
    private static final Map<Class<?>, Constructor<?>> PROXY_CONSTRUCTORS = new ConcurrentHashMap<>();

    /**
     * Bootstrap method handler {@link LambdaMetafactory#metafactory}.
     */
    private static final Handle BOOTSTRAP_METHOD_HANDLE = getBootstrapMethodHandle();
    /**
     * Method descriptor of {@link MethodInterceptor#intercept}.
     */
    private static final String INTERCEPT_METHOD_DESCRIPTOR = getInterceptMethodDescriptor();
    /**
     * Method descriptor of {@link MethodInvoker#invoke}.
     */
    private static final String INVOKE_METHOD_DESCRIPTOR = getInvokeMethodDescriptor();
    /**
     * Method descriptor of {@link Class#getMethod}.
     */
    private static final String GET_METHOD_DESCRIPTOR = getGetMethodDescriptor();
    /**
     * Descriptor of bootstrap method <code>() -&gt; MethodInvoker</code>.
     */
    private static final String BOOTSTRAP_DESCRIPTOR = "()" + Type.getDescriptor(MethodInvoker.class);
    /**
     * <code>com/alphalxy/MethodInterceptor</code>.
     */
    private static final String INTERCEPTOR_TYPE = Type.getInternalName(MethodInterceptor.class);
    /**
     * <code>Lcom/alphalxy/MethodInterceptor;</code>.
     */
    private static final String INTERCEPTOR_TYPE_DESCRIPTOR = Type.getDescriptor(MethodInterceptor.class);
    /**
     * <code>Ljava/lang/reflect/Method;</code>.
     */
    private static final String METHOD_TYPE_DESCRIPTOR = Type.getDescriptor(Method.class);
    /**
     * {@link ClassLoader#getClassLoadingLock(String)}.
     */
    @SuppressWarnings("JavadocReference")
    private static final Method GET_CLASS_LOADING_LOCK;
    /**
     * {@link ClassLoader#findLoadedClass(String)}.
     */
    @SuppressWarnings("JavadocReference")
    private static final Method FIND_LOADED_CLASS;
    /**
     * {@link ClassLoader#defineClass(String, byte[], int, int)}.
     */
    @SuppressWarnings("JavadocReference")
    private static final Method DEFINE_CLASS;

    static {
        try {
            Class<ClassLoader> c = ClassLoader.class;
            GET_CLASS_LOADING_LOCK = c.getDeclaredMethod("getClassLoadingLock", String.class);
            FIND_LOADED_CLASS = c.getDeclaredMethod("findLoadedClass", String.class);
            DEFINE_CLASS = c.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            // Tested for java 8 11 17
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method objectFieldOffset = unsafeType.getDeclaredMethod("objectFieldOffset", Field.class);
            Method putBoolean = unsafeType.getMethod("putBoolean", Object.class, long.class, boolean.class);
            long offset = getOverrideFieldOffset(unsafe, objectFieldOffset);
            // setAccessible
            putBoolean.invoke(unsafe, GET_CLASS_LOADING_LOCK, offset, true);
            putBoolean.invoke(unsafe, FIND_LOADED_CLASS, offset, true);
            putBoolean.invoke(unsafe, DEFINE_CLASS, offset, true);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Generate proxy class.
     */
    private static byte[] newProxyClass(Class<?>[] interfaces, String proxyName) {
        // fill interfaceNames
        String[] interfaceNames = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            interfaceNames[i] = Type.getInternalName(interfaces[i]);
        }

        // new class writer
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        // public final class ${proxyName} implements ${interfaces}
        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, proxyName, null, "java/lang/Object", interfaceNames);
        // private final MethodInterceptor interceptor;
        cw.visitField(ACC_PRIVATE + ACC_FINAL, "interceptor", INTERCEPTOR_TYPE_DESCRIPTOR, null, null);
        // public Proxy(MethodInterceptor interceptor) {
        //   this.interceptor = interceptor;
        // }
        visitConstructor(cw, proxyName);
        // static {}
        MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        // for each method of each interface
        int index = 0;
        for (Class<?> interfaceClass : interfaces) {
            Type serviceType = Type.getType(interfaceClass);
            for (Method method : interfaceClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    visitMethod(cw, clinit, serviceType, method, proxyName, index++);
                }
            }
        }
        clinit.visitInsn(RETURN);
        // ClassWriter.COMPUTE_MAXS
        clinit.visitMaxs(0, 0);
        return cw.toByteArray();
    }

    /**
     * Get proxy type.
     *
     * @see Proxy#newProxyInstance
     */
    private static Type getProxyType(Class<?>[] interfaces) {
        String proxyPkg = null;
        Set<Class<?>> interfaceSet = new HashSet<>();
        for (Class<?> interfaceClass : interfaces) {
            if (!interfaceClass.isInterface()) {
                throw new IllegalArgumentException(interfaceClass.getName() + " is not an interface");
            }
            int flags = interfaceClass.getModifiers();
            // Record the package of a non-public proxy interface
            // so that the proxy class will be defined in the same package.
            // Verify that all non-public proxy interfaces are in the same package.
            if (!Modifier.isPublic(flags)) {
                String name = interfaceClass.getName();
                int n = name.lastIndexOf('.');
                String pkg = ((n == -1) ? "" : name.substring(0, n + 1));
                if (proxyPkg == null) {
                    proxyPkg = pkg;
                } else if (!pkg.equals(proxyPkg)) {
                    throw new IllegalArgumentException("non-public interfaces from different packages");
                }
            }
            if (!interfaceSet.add(interfaceClass)) {
                throw new IllegalArgumentException("repeated interface: " + interfaceClass.getName());
            }
        }
        // Choose a name for the proxy class to generate.
        if (proxyPkg == null) {
            // if no non-public proxy interfaces, use com.alphalxy package
            return Type.getObjectType("com/alphalxy/$Proxy" + COUNTER.getAndIncrement());
        } else {
            return Type.getObjectType(proxyPkg.replace('.', '/') + "$Proxy" + COUNTER.getAndIncrement());
        }
    }

    /**
     * Write method field, override method and lambda method.
     */
    private static void visitMethod(ClassWriter cw, MethodVisitor clinit, Type serviceType,
                                    Method method, String proxyName, int index) {
        String lambdaMethodName = "lambda$" + method.getName() + "$" + index;
        String field = "M_" + index;
        // write field
        cw.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC, field, METHOD_TYPE_DESCRIPTOR, null, null);
        // write field initializer
        visitFieldInitializer(clinit, serviceType, proxyName, method, field);
        // write override method
        visitOverrideMethod(cw, proxyName, method, field, lambdaMethodName);
        // write lambda method
        visitLambdaMethod(cw, serviceType.getInternalName(), method, lambdaMethodName);
    }

    /**
     * Write field initializer.
     */
    private static void visitFieldInitializer(MethodVisitor clinit, Type serviceType, String proxyName, Method method,
                                              String field) {
        // init method field
        // Method M_0 = ${serviceType}.class.getMethod(${methodName}, argsType[0], argsType[1], argsType[2], ...)
        clinit.visitLdcInsn(serviceType);
        clinit.visitLdcInsn(method.getName());
        Parameter[] parameters = method.getParameters();
        int parameterSize = parameters.length;
        visitIntConstant(clinit, parameterSize);
        clinit.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        for (int i = 0; i < parameters.length; i++) {
            clinit.visitInsn(DUP);
            visitIntConstant(clinit, i);
            visitClassConstant(clinit, parameters[i].getType());
            clinit.visitInsn(AASTORE);
        }
        clinit.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethod", GET_METHOD_DESCRIPTOR, false);
        clinit.visitFieldInsn(PUTSTATIC, proxyName, field, METHOD_TYPE_DESCRIPTOR);
    }

    /**
     * Write override method.
     * <pre>
     * public final ${returnType} ${methodName}(arg0, arg1, args2, ...) {
     *     return? this.interceptor.intercept(this, ${method},
     *         (obj, args) -&gt; obj.${methodName}(args[0], args[1], args[2], ...),
     *         new Object[]{arg0, arg1, args2, ...}
     *     );
     * }
     * </pre>
     */
    private static void visitOverrideMethod(ClassWriter cw, String proxyName, Method method,
                                            String methodFieldName, String lambdaMethodName) {
        Parameter[] parameters = method.getParameters();
        int parameterSize = parameters.length;

        MethodVisitor mv;
        // public final ${returnType} ${methodName}(arg0, arg1, args2, ...)
        mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, method.getName(), Type.getMethodDescriptor(method), null, null);
        Label start = new Label();
        mv.visitLabel(start);

        // this.interceptor
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, proxyName, "interceptor", INTERCEPTOR_TYPE_DESCRIPTOR);
        // this
        mv.visitVarInsn(ALOAD, 0);
        // METHOD
        mv.visitFieldInsn(GETSTATIC, proxyName, methodFieldName, METHOD_TYPE_DESCRIPTOR);
        // (obj, args) -> obj.${methodName}(args[0], args[1], args[2], ...)
        Object[] bootstrapMethodArguments = bootstrapMethodArguments(proxyName, lambdaMethodName);
        mv.visitInvokeDynamicInsn("invoke", BOOTSTRAP_DESCRIPTOR, BOOTSTRAP_METHOD_HANDLE, bootstrapMethodArguments);
        // new Object[]{arg0, arg1, args2, ...}
        visitIntConstant(mv, parameterSize);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        // 0 is this, so + 1
        int offset = 1;
        for (int i = 0; i < parameterSize; i++) {
            // load new Object[]
            mv.visitInsn(DUP);
            // load i
            visitIntConstant(mv, i);
            Class<?> type = parameters[i].getType();
            // load args[i]
            mv.visitVarInsn(getLoadOpcode(type), offset++);
            visitBox(mv, type);
            if (type == Double.TYPE || type == Long.TYPE) {
                offset++;
            }
            // store args[i] to new Object[i]
            mv.visitInsn(AASTORE);
        }
        // this.interceptor.intercept(this, ${method},
        //    (obj, args) -> obj.${methodName}(args[0], args[1], args[2], ...),
        //    new Object[]{arg0, arg1, args2, ...}
        //)
        mv.visitMethodInsn(INVOKEINTERFACE, INTERCEPTOR_TYPE, "intercept", INTERCEPT_METHOD_DESCRIPTOR, true);
        // return
        Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) {
            mv.visitInsn(RETURN);
        } else {
            visitUnboxOrCheckCast(mv, returnType);
            mv.visitInsn(getReturnOpcode(returnType));
        }
        Label end = new Label();
        mv.visitLabel(end);
        mv.visitLocalVariable("this", "L" + proxyName + ";", null, start, end, 0);
        // 0 is this, so + 1
        offset = 1;
        for (Parameter param : parameters) {
            Class<?> type = param.getType();
            mv.visitLocalVariable(param.getName(), Type.getDescriptor(type), null, start, end, offset++);
            if (type == Double.TYPE || type == Long.TYPE) {
                offset++;
            }
        }
        // ClassWriter.COMPUTE_MAXS
        mv.visitMaxs(0, 0);
    }

    /**
     * Write lambda method.
     * <pre>
     * private static synthetic Object ${lambdaMethodName}(Object obj, Object[] args) {
     *     return ((${interfaceName})obj).${method}(args[0], args[1], args[2], ...);
     * }
     * </pre>
     */
    private static void visitLambdaMethod(ClassWriter cw, String interfaceName, Method method,
                                          String lambdaMethodName) {
        int lambdaAccess = ACC_PRIVATE + ACC_STATIC + ACC_SYNTHETIC;
        MethodVisitor mv;
        // private static synthetic Object ${lambdaMethodName}(Object obj, Object[] args)
        mv = cw.visitMethod(lambdaAccess, lambdaMethodName, INVOKE_METHOD_DESCRIPTOR, null, null);
        Label start = new Label();
        mv.visitLabel(start);
        // (${interfaceName})obj
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(CHECKCAST, interfaceName);
        // args[0], args[1], args[2], ...
        Parameter[] parameters = method.getParameters();
        int parameterSize = parameters.length;
        for (int i = 0; i < parameterSize; i++) {
            Parameter parameter = parameters[i];
            // load args
            mv.visitVarInsn(ALOAD, 1);
            // load i
            visitIntConstant(mv, i);
            // load args[i]
            mv.visitInsn(AALOAD);
            // cast to parameter type
            visitUnboxOrCheckCast(mv, parameter.getType());
        }
        // ((${interfaceName})obj).${method}(args[0], args[1], args[2], ...)
        mv.visitMethodInsn(INVOKEINTERFACE, interfaceName, method.getName(), Type.getMethodDescriptor(method), true);
        // return
        Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) {
            mv.visitInsn(ACONST_NULL);
        } else {
            visitBox(mv, returnType);
        }
        mv.visitInsn(ARETURN);
        Label end = new Label();
        mv.visitLabel(end);
        // LocalVariableTable
        mv.visitLocalVariable("obj", "Ljava/lang/Object;", null, start, end, 0);
        mv.visitLocalVariable("args", "[Ljava/lang/Object;", null, start, end, 1);
        // ClassWriter.COMPUTE_MAXS
        mv.visitMaxs(0, 0);
    }

    /**
     * Write constructor.
     * <pre>
     * public ${proxyName}(MethodInterceptor interceptor) {
     *     this.interceptor = interceptor;
     * }
     * </pre>
     */
    private static void visitConstructor(ClassWriter cw, String proxyName) {
        MethodVisitor mv;
        String descriptor = "(" + INTERCEPTOR_TYPE_DESCRIPTOR + ")V";
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", descriptor, null, null);
        Label start = new Label();
        mv.visitLabel(start);
        mv.visitVarInsn(ALOAD, 0);
        // super()
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        // this.interceptor = interceptor
        mv.visitFieldInsn(PUTFIELD, proxyName, "interceptor", INTERCEPTOR_TYPE_DESCRIPTOR);
        mv.visitInsn(RETURN);
        Label end = new Label();
        mv.visitLabel(end);
        // LocalVariableTable
        mv.visitLocalVariable("this", "L" + proxyName + ";", null, start, end, 0);
        mv.visitLocalVariable("interceptor", INTERCEPTOR_TYPE_DESCRIPTOR, null, start, end, 1);
        // ClassWriter.COMPUTE_MAXS
        mv.visitMaxs(0, 0);
    }

    /**
     * Bootstrap method handler {@link LambdaMetafactory#metafactory}.
     */
    private static Handle getBootstrapMethodHandle() {
        String name = "metafactory";
        for (Method method : LambdaMetafactory.class.getMethods()) {
            if (name.equals(method.getName())) {
                String owner = Type.getInternalName(LambdaMetafactory.class);
                return new Handle(Opcodes.H_INVOKESTATIC, owner, name, Type.getMethodDescriptor(method), false);
            }
        }
        throw new InternalError();
    }

    /**
     * Intercept method descriptor.
     */
    private static String getInterceptMethodDescriptor() {
        for (Method method : MethodInterceptor.class.getMethods()) {
            if ("intercept".equals(method.getName())) {
                return Type.getMethodDescriptor(method);
            }
        }
        throw new InternalError();
    }

    /**
     * Method descriptor of {@link MethodInvoker#invoke}.
     */
    private static String getInvokeMethodDescriptor() {
        for (Method method : MethodInvoker.class.getMethods()) {
            if ("invoke".equals(method.getName())) {
                return Type.getMethodDescriptor(method);
            }
        }
        throw new InternalError();
    }

    /**
     * Method Descriptor of {@link Class#getMethod} method.
     */
    @SuppressWarnings("SameReturnValue")
    private static String getGetMethodDescriptor() {
        // Class.class.getMethod("getMethod", String.class, Class[].class)
        return "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;";
    }

    /**
     * Bootstrap method arguments.
     */
    private static Object[] bootstrapMethodArguments(String proxyName, String lambdaMethodName) {
        Type type = Type.getType(INVOKE_METHOD_DESCRIPTOR);
        Handle handle = new Handle(H_INVOKESTATIC, proxyName, lambdaMethodName, INVOKE_METHOD_DESCRIPTOR, false);
        return new Object[] {type, handle, type};
    }

    /**
     * Box.
     */
    private static void visitBox(MethodVisitor mv, Class<?> type) {
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            } else if (type == Boolean.TYPE) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            } else if (type == Byte.TYPE) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            } else if (type == Character.TYPE) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            } else if (type == Short.TYPE) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            } else if (type == Double.TYPE) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            } else if (type == Float.TYPE) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            } else if (type == Long.TYPE) {
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            } else {
                throw new InternalError();
            }
        }
    }

    /**
     * Unbox or checkCast.
     */
    private static void visitUnboxOrCheckCast(MethodVisitor mv, Class<?> type) {
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            } else if (type == Boolean.TYPE) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            } else if (type == Byte.TYPE) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
            } else if (type == Character.TYPE) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
            } else if (type == Short.TYPE) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
            } else if (type == Double.TYPE) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            } else if (type == Float.TYPE) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
            } else if (type == Long.TYPE) {
                mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            } else {
                throw new InternalError();
            }
        } else if (type != Object.class) {
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(type));
        }
    }

    private static void visitClassConstant(MethodVisitor mv, Class<?> type) {
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
            } else if (type == Boolean.TYPE) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
            } else if (type == Byte.TYPE) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
            } else if (type == Character.TYPE) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
            } else if (type == Short.TYPE) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
            } else if (type == Double.TYPE) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
            } else if (type == Float.TYPE) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
            } else if (type == Long.TYPE) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
            } else {
                throw new InternalError();
            }
        } else {
            mv.visitLdcInsn(Type.getType(type));
        }
    }

    /**
     * ICONST_0 ~ ICONST_5 or BIPUSH or SIPUSH
     */
    private static void visitIntConstant(MethodVisitor mv, int i) {
        if (i < 6) {
            // ICONST_0 ~ ICONST_5
            mv.visitInsn(i + 3);
        } else if (i < 128) {
            // java.lang.Byte.MAX_VALUE = 127
            mv.visitIntInsn(BIPUSH, i);
        } else {
            mv.visitIntInsn(SIPUSH, i);
        }
    }

    /**
     * Get load op code for type.
     */
    @SuppressWarnings("DuplicatedCode")
    private static int getLoadOpcode(Class<?> type) {
        if (type == Integer.TYPE) {
            return ILOAD;
        } else if (type == Boolean.TYPE) {
            return ILOAD;
        } else if (type == Byte.TYPE) {
            return ILOAD;
        } else if (type == Character.TYPE) {
            return ILOAD;
        } else if (type == Short.TYPE) {
            return ILOAD;
        } else if (type == Double.TYPE) {
            return DLOAD;
        } else if (type == Float.TYPE) {
            return FLOAD;
        } else if (type == Long.TYPE) {
            return LLOAD;
        } else {
            return ALOAD;
        }
    }

    /**
     * Get return op code for type.
     */
    @SuppressWarnings("DuplicatedCode")
    private static int getReturnOpcode(Class<?> type) {
        if (type == Integer.TYPE) {
            return IRETURN;
        } else if (type == Boolean.TYPE) {
            return IRETURN;
        } else if (type == Byte.TYPE) {
            return IRETURN;
        } else if (type == Character.TYPE) {
            return IRETURN;
        } else if (type == Short.TYPE) {
            return IRETURN;
        } else if (type == Double.TYPE) {
            return DRETURN;
        } else if (type == Float.TYPE) {
            return FRETURN;
        } else if (type == Long.TYPE) {
            return LRETURN;
        } else {
            return ARETURN;
        }
    }

    /**
     * Define a class.
     */
    private static Class<?> defineClass(ClassLoader classLoader, String className, byte[] bytes) {
        try {
            synchronized (GET_CLASS_LOADING_LOCK.invoke(classLoader, className)) {
                Object type = FIND_LOADED_CLASS.invoke(classLoader, className);
                if (type == null) {
                    return (Class<?>)DEFINE_CLASS.invoke(classLoader, className, bytes, 0, bytes.length);
                } else {
                    throw new IllegalStateException("Cannot define already loaded type: " + type);
                }
            }
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException)throwable;
            }
            if (throwable instanceof Error) {
                throw (Error)throwable;
            }
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Since Java 12, the override field is hidden from the reflection API.
     * To circumvent this, we create a mirror class of AccessibleObject
     * that defines the same fields and has the same field layout such
     * that the override field will receive the same class offset.
     * Doing so, we can write to the offset location and still set a value to it,
     * despite it being hidden from the reflection API.
     *
     * @see
     * <a href="https://github.com/raphw/byte-buddy/blob/master/byte-buddy-dep/src/main/java/net/bytebuddy/dynamic/loading/ClassInjector.java">ClassInjector.java</a>
     */
    private static int getOverrideFieldOffset(Object unsafe, Method objectFieldOffset) throws Exception {
        // read AccessibleObject.class bytes
        String className = AccessibleObject.class.getName();
        // mirror class name
        String mirrorClassName = "com.alphalxy.AccessibleObject";
        URL resource = ClassLoader.getSystemClassLoader().getResource(className.replace(".", "/") + ".class");
        try (InputStream inputStream = Objects.requireNonNull(resource).openStream()) {
            ClassWriter cw = new ClassWriter(0);
            new ClassReader(inputStream).accept(new ClassVisitor(ASM8) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName,
                                  String[] interfaces) {
                    // replace name
                    cw.visit(version, access, mirrorClassName.replace(".", "/"), signature, superName, interfaces);
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                                               Object value) {
                    // copy all fields
                    cw.visitField(access, name, descriptor, signature, null);
                    return null;
                }
            }, 0);
            byte[] bytes = cw.toByteArray();
            // defineClass mirror class
            ClassLoader classLoader = new ClassLoader() {{
                defineClass(mirrorClassName, bytes, 0, bytes.length);
            }};
            // load mirror class
            Class<?> mirrorClass = Class.forName(mirrorClassName, false, classLoader);
            Field override = mirrorClass.getDeclaredField("override");
            // get override field offset
            return ((Long)objectFieldOffset.invoke(unsafe, override)).intValue();
        }
    }
}
