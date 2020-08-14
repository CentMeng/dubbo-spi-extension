package com.msj.dubbo.spi.extension.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

/**
 * @Description: dubbo consumer日志
 * @Author: Vincent.M mengshaojie@188.com
 * @Date 2018/7/30 下午7:56
 * @Version: 1.0.0
 */
@Activate(group = {CommonConstants.CONSUMER},order = 1)
public class ConsumerAccessLogFilter implements Filter {

    private static final String APPLICATION = "application";
    private static final String ACCESS_LOG_PATH = "accesslogpath";

    private volatile boolean inited = false;
    private static final Object lock = new Object();

    private AccessLogWrapper logWrapper;


    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        invocation.getAttachments().put("consumer", invoker.getUrl().getParameter(APPLICATION));
        init(invoker.getUrl().getParameter(ACCESS_LOG_PATH), invoker.getUrl().getParameter(APPLICATION) + "-consumer");
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
