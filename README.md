# FastProxy
Alternatives to `java.lang.Proxy`.

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


## How

Generate lambda express as `MethodInvoker` for each method.


