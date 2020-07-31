package com.msj.dubbo.spi.extension.util;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Description: 实现抽象可休眠的任务，避免浪费cpu
 * @Author: Vincent.M mengshaojie@188.com
 * @Date 2018/7/30 下午8:03
 * @Version: 1.0.0
 */
public abstract class SleepyTask implements Runnable {

    private final AtomicBoolean should = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean(false);
    protected final Executor executor;

    public SleepyTask() {
        this(Executors.newCachedThreadPool());
    }

    public SleepyTask(Executor executor) {
        this.executor = executor;
    }

    @Override
    public final void run() {
        try {
            while (should.compareAndSet(true, false)) {
                try {
                    runTask();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            running.set(false);
        }
    }

    /***
     * 叫醒线程继续执行
     * @return boolean
     */
    public boolean weakUp() {
        should.set(true);
        if (running.compareAndSet(false, true)) {
            executor.execute(this);
            return true;
        }
        return false;
    }

    /***
     * 子类应该实现此方法
     */
    protected abstract void runTask();
}
