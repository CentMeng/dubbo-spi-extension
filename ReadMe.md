# dubbo-spi-extension

-----

> 此工程包含关于输出dubbo调用记录日志和dubbo全链路跟踪id，其中traceId是依赖MDC进行的，通过拦截器，从消费者MDC中获取到traceId，放到dubbo 的RpcContext中，然后提供者通过RpcContext获取到traceId，再设置到MDC中。根据配置日志pattern增加 `[%X{traceId}]` 来显示traceId

本工程基本都是基于dubbo的拦截器进行开发的，其中拦截器配置文件，在[filter配置文件](https://github.com/CentMeng/dubbo-spi-extension/blob/master/src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Filter),这是dubbo配置拦截器的基本规则。

## dubbo调用日志
### 实现类
- [ `ProviderAccessLogFilter`](https://github.com/CentMeng/dubbo-spi-extension/blob/master/src/main/java/com/msj/dubbo/spi/extension/filter/ProviderTraceIdFilter.java)
- [`ConsumerAccessLogFilter`](https://github.com/CentMeng/dubbo-spi-extension/blob/master/src/main/java/com/msj/dubbo/spi/extension/filter/ConsumerAccessLogFilter.java)

其中通过

```
 @Activate(group = {CommonConstants.CONSUMER})
```
来区分适用提供者还是消费者

### 项目调用
以提供者为例，进行介绍
在项目中properties文件配置以下属性

```
//日志级别
dubbo.protocol.parameters.loglevel=8
//日志路径
dubbo.protocol.parameters.accesslogpath=./logs/provider
//provider使用的拦截器
dubbo.provider.filter=providerLog
```

## traceId（全链路跟踪ID）
### 实现类

- [ `ProviderTraceIdFilter` ](https://github.com/CentMeng/dubbo-spi-extension/blob/master/src/main/java/com/msj/dubbo/spi/extension/filter/ProviderTraceIdFilter.java)
- [ `ConsumerTraceIdFilter`](https://github.com/CentMeng/dubbo-spi-extension/blob/master/src/main/java/com/msj/dubbo/spi/extension/filter/ConsumerTraceIdFilter.java)
- [ `ThreadMdcUtil`](https://github.com/CentMeng/dubbo-spi-extension/blob/master/src/main/java/com/msj/dubbo/spi/extension/util/ThreadMdcUtil.java)
### 实现依赖
dubbo全链路跟踪id，技术实现依赖于slf4j的MDC
>  __MDC 介绍__ 
MDC（Mapped Diagnostic Context，映射调试上下文）是 log4j 和 logback 提供的一种方便在多线程条件下记录日志的功能。MDC 可以看成是一个与当前线程绑定的Map，可以往其中添加键值对。MDC 中包含的内容可以被同一线程中执行的代码所访问。当前线程的子线程会继承其父线程中的 MDC 的内容。当需要记录日志时，只需要从 MDC 中获取所需的信息即可。MDC 的内容则由程序在适当的时候保存进去。对于一个 Web 应用来说，通常是在请求被处理的最开始保存这些数据。

简而言之，MDC就是日志框架提供的一个 `InheritableThreadLocal` ，项目代码中可以将键值对放入其中，然后使用指定方式取出打印即可。

在 log4j 和 logback 的取值方式为：
```
%X{traceId}

```

MDC使用的 `InheritableThreadLocal` 只是在线程被创建时继承，但是线程池中的线程是复用的，后续请求使用已有的线程将打印出之前请求的traceId。这时候就需要对线程池进行一定的包装，在线程在执行时读取之前保存的MDC内容。具体方法可参照[ `ThreadMdcUtil`](https://github.com/CentMeng/dubbo-spi-extension/blob/master/src/main/java/com/msj/dubbo/spi/extension/util/ThreadMdcUtil.java)

除了MDC之外，我们还需要了解下 __RpcContext__ 其RpcContext本质上是个ThreadLocal对象, 其 __维护了一次rpc交互的上下文信息.__ 

 
```
public class RpcContext {
    // *) 定义了ThreadLocal对象
    private static final ThreadLocal<RpcContext> LOCAL = new ThreadLocal() {
        protected RpcContext initialValue() {
            return new RpcContext();
        }
    };
    // *) 附带属性, 这些属性可以随RpcInvocation对象一起传递
    private final Map<String, String> attachments = new HashMap();

    public static RpcContext getContext() {
        return (RpcContext)LOCAL.get();
    }

    protected RpcContext() {
    }

    public String getAttachment(String key) {
        return (String)this.attachments.get(key);
    }

    public RpcContext setAttachment(String key, String value) {
        if(value == null) {
            this.attachments.remove(key);
        } else {
            this.attachments.put(key, value);
        }

        return this;
    }

    public void clearAttachments() {
        this.attachments.clear();
    }

}
```
由上面代码可知，我们可以将traceId放到attachments参数中，进行传递，但是在实际使用中， __RpcContext对象的attachment在一次rpc交互后被清空了__ ，这样会导致下一次就没有了traceId信息，所以我们需要在每次调用的时候重新设置traceId。


### 实现方案
看了实现类和实现依赖MDC，大家可能已经知道了，剩下的就是很简单的内容，就是对消费者和提供者进行拦截，消费者获取MDC值进行set，提供者进行get值，在set到MDC中。

上面是实现的具体抽象设计，下面看看，如果要使用本类，在引用的项目中需要做什么

1. 提供者和消费者配置filter,此处以properties配置文件为例
- 提供者
```
dubbo.provider.filter=providerLog,providerTraceId
```
- 消费者
```
dubbo.consumer.filter=consumerTraceId
```

2. 修改日志配置文件pattern
以logback为例,增加`%X{traceId}`
```xml
<property name="r_pattern" value="%d{yyyy-MM-dd HH:mm:ss} [%p] [%t] [%logger{5}] [%X{traceId}] %m%n"/> 
```