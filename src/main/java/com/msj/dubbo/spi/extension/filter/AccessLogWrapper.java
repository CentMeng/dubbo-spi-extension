package com.msj.dubbo.spi.extension.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.*;
import com.alibaba.fastjson.JSON;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description:
 * @Author: Vincent.M mengshaojie@188.com
 * @Date 2018/7/30 下午7:56
 * @Version: 1.0.0
 */
public class AccessLogWrapper {

    private static final Logger logger = LoggerFactory.getLogger(AccessLogWrapper.class);
    private static final String LEVEL_ARG_KEY = "loglevel";
    private final AsyncLogger log;

    public AccessLogWrapper(String path, String logFileNamePrefix) {
        String home = null;
        if (!StringUtils.isBlank(path)) {
            home = path;
        } else {
            home = System.getProperty("catalina.base");
            if (home == null)
                home = "target";
            else
                home = home + "/logs";
            try {
                home = new File(home).getCanonicalPath();
            } catch (IOException e) {
                logger.error("logger home path failed: ", e);
            }
        }
        log = new AsyncLogger(home + "/" + logFileNamePrefix + ".", ".log", 10000);
        logger.info("dubbo log access logging in : " + home);
    }

    /**
     * 代理执行LogFilter invoke
     *
     * @param invoker    invoker
     * @param invocation invocation
     * @return Result
     */
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        Result result = null;
        RpcException ex = null;
        LogContext logContext = new LogContext(invoker, invocation);
        long start = System.currentTimeMillis();
        try {
            result = invoker.invoke(invocation);
        } catch (RpcException e) {
            ex = e;
        }
        long end = System.currentTimeMillis();
        log(logContext, invoker, invocation, ex, result, (end - start));
        //Filter里不能吞掉异常
        if (ex != null)
            throw ex;
        return result;
    }

    /**
     * 打印日志
     *
     * @param logContext 日志调用前的上下文
     * @param invoker    invoker
     * @param inv        inv
     * @param ex         异常
     * @param result     执行结果
     * @param elapsed    调用时长
     */
    private void log(LogContext logContext, Invoker<?> invoker, Invocation inv, RpcException ex, Result result, long elapsed) {
        try {
            StringBuilder logRecord = new StringBuilder();
            appendBeforeInvokeLog(logRecord, logContext, invoker, inv);
            appendAfterInvokeLog(logRecord, logContext, ex, result, elapsed);
            if (logRecord.length() == 0) return;
            log.log(logRecord.toString());
        } catch (Throwable t) {
            logger.warn("Exception in AccessLogFilter of service(" + invoker + " -> " + inv + ")", t);
        }
    }

    /**
     * 拼接调用前的日志
     *
     * @param logRecord  append container
     * @param logContext 日志调用前的上下文
     * @param invoker    invoker
     * @param inv        inv
     */
    private void appendBeforeInvokeLog(StringBuilder logRecord, LogContext logContext, Invoker<?> invoker, Invocation inv) {
        String consumer;
        int consumerPort;
        String provider;
        int providerPort;
        String consumerApp;
        String providerApp;
        RpcContext rpcContext = RpcContext.getContext();
        if (logContext.isConsumerSide()) {
            consumer = logContext.getLocalHost();
            consumerPort = logContext.getLocalPort();
            provider = logContext.getRemoteHost();
            providerPort = logContext.getRemotePort();
            providerApp = rpcContext.getAttachment("provider");
            if (StringUtils.isBlank(providerApp)) {
                providerApp = "";
            }
            consumerApp = logContext.getApplication();
        } else {
            consumer = logContext.getRemoteHost();
            consumerPort = logContext.getRemotePort();
            provider = logContext.getLocalHost();
            providerPort = logContext.getLocalPort();
            consumerApp = inv.getAttachment("consumer");
            if (StringUtils.isBlank(consumerApp)) {
                consumerApp = "";
            }
            providerApp = logContext.getApplication();
        }
        String serviceName = invoker.getInterface().getName();
        String version = invoker.getUrl().getParameter(CommonConstants.VERSION_KEY);
        String group = invoker.getUrl().getParameter(CommonConstants.GROUP_KEY);
        logRecord.append("consumer[").append(consumerApp).append(",")
                .append(consumer).append(':').append(consumerPort)
                .append("]")
                .append(" -> ")
                .append("provider[").append(providerApp).append(",")
                .append(provider).append(':').append(providerPort)
                .append("]")
                .append(" - ");
        if (null != group && group.length() > 0) {
            logRecord.append(group).append("/");
        }
        logRecord.append(serviceName);
        if (null != version && version.length() > 0) {
            logRecord.append(":").append(version);
        }
        logRecord.append(" ");
        logRecord.append(inv.getMethodName());
        logRecord.append("(");
        Class<?>[] types = inv.getParameterTypes();
        if (types != null && types.length > 0) {
            boolean first = true;
            for (Class<?> type : types) {
                if (first) {
                    first = false;
                } else {
                    logRecord.append(",");
                }
                logRecord.append(type.getName());
            }
        }
        logRecord.append(") ");
        //判断是否需要拼接参数
        if (logContext.filterLevel < FilterLevel.ARGUMENTS.level) {
            return;
        }
        Object[] args = inv.getArguments();
        if (args != null && args.length > 0) {
            logRecord.append(JSON.toJSONString(args));
        }
    }

    /***
     * 拼接调用后日志
     * @param logRecord log appender
     * @param result 调用结果
     * @param ex    ex
     * @param elapsed   执行时间
     */
    private void appendAfterInvokeLog(StringBuilder logRecord, LogContext logContext, RpcException ex, Result result, long elapsed) {
        logRecord.append(",cost:")
                .append(elapsed).append("ms");
        if (ex != null) {
            logRecord.append("FAILED(").append(ex.getMessage()).append(") ");
            logger.warn("service invoke failed! \n" + logRecord, ex);
            return;
        }
        logRecord.append(" DONE ");
        if (logContext.filterLevel < FilterLevel.RETURNS.level) {
            return;
        }
        logRecord.append("\n Return value(").append(JSON.toJSONString(result.getValue())).append(") ");
    }

    /***
     * 保存调用日志的上下文
     */
    private final class LogContext {
        private String application;
        private String localHost;
        private int localPort;
        private String remoteHost;
        private int remotePort;
        private boolean consumerSide;
        private Map<String, String> attachments;
        private int filterLevel;

        public LogContext(Invoker<?> invoker, Invocation invocation) {
            this.attachments = new HashMap<String, String>();
            this.filterLevel = invoker.getUrl().getMethodParameter(invocation.getMethodName(), LEVEL_ARG_KEY,
                    FilterLevel.SIMPLE.level);
            this.application = invoker.getUrl().getParameter("application");
            parse();
        }

        private void parse() {
            RpcContext context = RpcContext.getContext();
            this.localHost = context.getLocalHost();
            this.localPort = context.getLocalPort();
            this.remoteHost = context.getRemoteHost();
            this.remotePort = context.getRemotePort();
            this.consumerSide = context.isConsumerSide();
        }

        public String getApplication() {
            return application;
        }

        public String getLocalHost() {
            return localHost;
        }

        public int getLocalPort() {
            return localPort;
        }

        public String getRemoteHost() {
            return remoteHost;
        }

        public int getRemotePort() {
            return remotePort;
        }

        public Map<String, String> getAttachments() {
            return attachments;
        }

        public int getFilterLevel() {
            return filterLevel;
        }

        public boolean isConsumerSide() {
            return consumerSide;
        }
    }

    /***
     * 日志过滤级别
     */
    private enum FilterLevel {
        SIMPLE(5),
        ARGUMENTS(8),
        RETURNS(10);
        public final int level;

        FilterLevel(int level) {
            this.level = level;
        }
    }

}
