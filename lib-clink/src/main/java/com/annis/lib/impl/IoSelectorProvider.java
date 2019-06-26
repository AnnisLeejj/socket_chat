package com.annis.lib.impl;

import com.annis.lib.core.IoProvider;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IoSelectorProvider implements IoProvider {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final AtomicBoolean inRegInput = new AtomicBoolean(false);
    private final AtomicBoolean inRegOutput = new AtomicBoolean(false);
    private final Selector readSelector;
    private final Selector writeSelector;

    private final HashMap<SelectionKey, Runnable> inputCallbackMap = new HashMap<>();
    private final HashMap<SelectionKey, Runnable> outputCallbackMap = new HashMap<>();

    private final ExecutorService inputHandlPool;
    private final ExecutorService outputHandlPool;

    public IoSelectorProvider() throws IOException {
        readSelector = Selector.open();
        writeSelector = Selector.open();
        inputHandlPool = Executors.newFixedThreadPool(4,
                new IoProviderThreadFactory("IoProvider-Input-Thread-"));
        outputHandlPool = Executors.newFixedThreadPool(4,
                new IoProviderThreadFactory("IoProvider-Input-Thread-"));
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallback callback) throws ClosedChannelException {
        channel.register(readSelector, SelectionKey.OP_READ);
        return false;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallback callback) {
        return false;
    }

    @Override
    public boolean unRegisterInput(SocketChannel channel) {
        return false;
    }

    @Override
    public boolean unRegisterOutput(SocketChannel channel) {
        return false;
    }

    private void startRead() {
        Thread thread = new Thread("Clink IoSelectorProvider ReadSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        if (readSelector.select() == 0) {
                            continue;
                        }
                        Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                        selectionKeys.forEach(key -> {
                            if (key.isValid()) {
                                handleSelection(key, SelectionKey.OP_READ, inputCallbackMap, inputHandlPool);
                            }
                        });
                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    private void startWrite() {
        Thread thread = new Thread("Clink IoSelectorProvider WriteSelector Thread") {
            @Override
            public void run() {

            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    @Override
    public void close() throws IOException {

    }

    private static void register(SocketChannel channel, Selector selector,
                                 int registerOps, AtomicBoolean locker,
                                 HashMap<SelectionKey, Runnable> map,
                                 Runnable runnable) {
        synchronized (locker) {
            //设置锁定状态
            locker.set(true);
            try {
                //唤醒当前的selector,让selector不处于select()状态
                selector.wakeup();
                //6:47

            } finally {
                //解除锁定状态
                locker.set(false);
                try {
                    //通知其他线程
                    locker.notify();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void handleSelection(SelectionKey key, int keyOps,
                                 HashMap<SelectionKey, Runnable> inputMap,
                                 ExecutorService inputPool) {
        //重点
        //取消继续都KeyOps的监听
        key.interestOps(key.readyOps() & ~keyOps);

        Runnable runnable = null;
        try {
            runnable = inputMap.get(key);
        } catch (Exception ignored) {
        }
        if (runnable != null && !inputPool.isShutdown()) {
            //异步调度
            inputPool.execute(runnable);
        }
    }

    static class IoProviderThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        IoProviderThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            this.group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY) ;
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
