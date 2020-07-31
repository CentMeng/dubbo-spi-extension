package com.msj.dubbo.spi.extension.filter;

import com.msj.dubbo.spi.extension.util.SleepyTask;
import org.apache.dubbo.common.utils.NamedThreadFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Description: 异步日志记录器
 * @Author: Vincent.M mengshaojie@188.com
 * @Date 2018/7/30 下午8:01
 * @Version: 1.0.0
 */
public class AsyncLogger extends SleepyTask {

    private final String directory;

    private final String suffix;

    private final ArrayBlockingQueue<Record> queue;

    private final RollingPattern rollingPattern;

    private static final SimpleDateFormat CONTENT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private volatile String fileName = "";

    private PrintWriter writer;

    public AsyncLogger(String prefix, String suffix) {
        this(prefix, suffix, 10000);
    }

    public AsyncLogger(String prefix, String suffix, int queueSize) {
        this(prefix, suffix, queueSize, new TimeBasedRollingPattern("yyyy-MM-dd"));
    }

    public AsyncLogger(String prefix, String suffix, RollingPattern pattern) {
        this(prefix, suffix, 10000, pattern);
    }

    public AsyncLogger(String prefix, String suffix, int queueSize, RollingPattern pattern) {
        super(Executors.newSingleThreadExecutor(new NamedThreadFactory("dubbo-asyncLogger-" + prefix, true)));
        this.directory = prefix;
        this.suffix = suffix;
        this.queue = new ArrayBlockingQueue<Record>(queueSize);
        this.rollingPattern = pattern;
    }

    /***
     * 获取文件句柄
     */
    private void open() {
        try {
            File path = new File(directory + fileName + suffix);
            path.getParentFile().mkdirs();
            writer = new PrintWriter(new BufferedWriter(new FileWriter(path, true), 128000), false);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Deprecated
    public String getDatePath(long date) {
        String timestamp = new Timestamp(date).toString();
        return timestamp.substring(0, 10);
    }

    private String getDatePath(String timestamp) {
        return timestamp.substring(0, 10);
    }

    /***
     * print log
     * @param message   message
     * @return boolean
     */
    public boolean log(String message) {
        boolean re = queue.offer(new Record(System.currentTimeMillis(), message));
        weakUp();
        return re;
    }

    @Override
    protected void runTask() {
        flush();
    }

    /***
     * flush log of Memory to file
     */
    public synchronized void flush() {
        Record rec;
        while ((rec = queue.poll()) != null) {
            log(rec);
        }
        if (writer != null)
            writer.flush();
    }

    /***
     * print record
     * @param rec   record
     */
    private void log(Record rec) { //单线程中执行, 无需考虑并行问题.
        roll(rec);
        try {
            Date now = new Date(rec.time);
            writer.println(CONTENT_DATE_FORMAT.format(now) + ": " + rec.message);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /***
     * roll log
     * @param rec   record
     */
    private void roll(Record rec) {
        String fileName = rollingPattern.fileName(rec);
        if (!fileName.equals(this.fileName)) {
            close();
            this.fileName = fileName;
            open();
        }
    }

    /**
     * close file handle
     */
    private void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        fileName = "";
    }

    /***
     * 关闭
     */
    public void shutdown() {
        ((ExecutorService) executor).shutdown();
        flush();
        close();
    }

    /**
     * 日志
     */
    public static final class Record {
        public final long time;
        public final String message;

        public Record(long time, String message) {
            this.time = time;
            this.message = message;
        }
    }

    /**
     * 日志滚动接口
     */
    public static interface RollingPattern {
        String fileName(Record record);
    }

    /**
     * 基于时间的日志滚动
     */
    public static class TimeBasedRollingPattern implements RollingPattern {
        private final SimpleDateFormat format;

        public TimeBasedRollingPattern(String pattern) {
            this.format = new SimpleDateFormat(pattern);
        }

        @Override
        public String fileName(Record record) {
            return format.format(new Date(record.time));
        }
    }
}
