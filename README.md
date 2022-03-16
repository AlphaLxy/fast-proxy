# FastProxy
FastProxy is a library to create dynamic proxy class using `org.ow2.asm:asm`.

### Getting started
```xml
<dependency>
    <groupId>com.alphalxy</groupId>
    <artifactId>fast-proxy</artifactId>
    <version>0.0.1</version>
</dependency>
```

```java
import com.alphalxy.FastProxy;
```

### Create proxy instance
Just invoke `newProxyInstance` method.

```java
Foo target = new Foo() {
    // ...
};
MethodInterceptor interceptor = new MethodInterceptor() {
    @Override
    public Object intercept(Object proxy, Method method, MethodInvoker invoker, Object[] args) throws Throwable {
        // do with method
        return invoker.invoke(target, args);
    }
};
Foo foo = (Foo)FastProxy.newProxyInstance(Foo.class.getClassLoader(), new Class<?>[] {Foo.class}, interceptor);
```
Or use lambda expression.
```java
Foo target = new Foo() {
    // ...
};
MethodInterceptor interceptor = (proxy, method, invoker, args) -> invoker.invoke(target, args);
Foo foo = (Foo)FastProxy.newProxyInstance(Foo.class.getClassLoader(), new Class<?>[] {Foo.class}, interceptor);
```

### Check proxy

```java
FastProxy.isProxyInstance(f);
FastProxy.isProxyClass(Foo.class);
```

### Benchmark

### How

Use lambda express as `MethodInvoker` for each method.

For any interface
```java
public interface Foo {
    String bar(String string);
    void baz(String string);
}
```
generate a `$Proxy` class using `org.ow2.asm:asm`.

```java
import com.alphalxy.MethodInterceptor;
import java.lang.reflect.Method;

public final class $Proxy implements Foo {
    private final MethodInterceptor interceptor;
    private static final Method M_0 = Foo.class.getMethod("bar", String.class);
    private static final Method M_1 = Foo.class.getMethod("baz", String.class);

    public $Proxy(MethodInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public String bar(String string) {
        return interceptor.intercept(this, M_0, (target, args) -> {
            return ((Foo)target).bar((String)args[0]);
        }, new Object[] {string});
    }

    public void baz(String string) {
        interceptor.intercept(this, M_1, (target, args) -> {
            ((Foo)target).baz((String)args[0]);
            return null;
        }, new Object[] {string});
    }
}
```




