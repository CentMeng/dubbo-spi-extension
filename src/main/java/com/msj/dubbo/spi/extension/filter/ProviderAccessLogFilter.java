package com.msj.dubbo.spi.extension.filter;


import org.apache.dubbo.rpc.*;

/**
 * @Description:
 * @Author: Vincent.M mengshaojie@188.com
 * @Date 2018/7/30 下午7:55
 * @Version: 1.0.0
 */
public class ProviderAccessLogFilter implements Filter {

    private static final String APPLICATION = "application";
    private static final String ACCESS_LOG_PATH = "accesslogpath";

    private volatile boolean inited = false;
    private static final Object lock = new Object();

    private AccessLogWrapper logWrapper;

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        invocation.getAttachments().put("provider", invoker.getUrl().getParameter(APPLICATION));
        init(invoker.getUrl().getParameter(ACCESS_LOG_PATH), invoker.getUrl().getParameter(APPLICATION) + "-provider");
        return logWrapper.invoke(invoker, invocation);
    }

    private void init(String path, String name) {
        if (!inited) {
            if (logWrapper == null) {
                synchronized (lock) {
                    logWrapper = new AccessLogWrapper(path, name);
                }
            }
        }
    }
}
