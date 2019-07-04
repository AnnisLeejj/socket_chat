package com.annis.lib.impl;

import com.annis.lib.core.IoProvider;
import com.annis.lib.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IoSelectorProvider implements IoProvider {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    //是否处于这个过程
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
                new IoProviderThreadFactory("IoProvider-Output-Thread-"));

        //开始输入输出的监听
        startRead();
        startWrite();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallback callback) throws ClosedChannelException {
        return registerSelector(channel, readSelector, SelectionKey.OP_READ, inRegInput,
                inputCallbackMap, callback) != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallback callback) {
        return registerSelector(channel, readSelector, SelectionKey.OP_WRITE, inRegOutput,
                outputCallbackMap, callback) != null;
    }

    @Override
    public boolean unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap);
        return false;
    }

    @Override
    public boolean unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, inputCallbackMap);
        return false;
    }

    private void startRead() {
        Thread thread = new Thread("Clink IoSelectorProvider ReadSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        if (readSelector.select() == 0) {
                            waitSelection(inRegInput);
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
                while (!isClosed.get()) {
                    try {
                        if (writeSelector.select() == 0) {//阻塞 或 没有事件 返回0
                            //判断是否处于注册状态,如果是则等待注册完成后被唤醒
                            waitSelection(inRegOutput);
                            continue;
                        }
                        Set<SelectionKey> selectionKeys = writeSelector.selectedKeys();
                        selectionKeys.forEach(key -> {
                            if (key.isValid()) {
                                handleSelection(key, SelectionKey.OP_WRITE, outputCallbackMap, outputHandlPool);
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

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            inputHandlPool.shutdown();
            outputHandlPool.shutdown();

            inputCallbackMap.clear();
            outputCallbackMap.clear();

            readSelector.wakeup();
            writeSelector.wakeup();

            CloseUtils.close(readSelector,writeSelector);
        }
    }

    private static void waitSelection(final AtomicBoolean locker) {
        synchronized (locker) {
            if (locker.get()) {
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 注册
     *
     * @param channel
     * @param selector
     * @param registerOps
     * @param locker
     * @param map
     * @param runnable
     * @return
     */
    private static SelectionKey registerSelector(SocketChannel channel, Selector selector,
                                                 int registerOps, AtomicBoolean locker,
                                                 HashMap<SelectionKey, Runnable> map,
                                                 Runnable runnable) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            //设置锁定状态
            locker.set(true);
            try {
                //唤醒当前的selector,让selector不处于select()状态
                selector.wakeup();
                //
                SelectionKey key = null;
                if (channel.isRegistered()) {
                    //查询是否已经注册过
                    key = channel.keyFor(selector);
                    if (key != null) {
                        key.interestOps(key.readyOps() | registerOps);
                    }
                }
                if (key == null) {
                    try {
                        //注册selector得到key
                        key = channel.register(selector, registerOps);
                        //注册回调
                        map.put(key, runnable);
                    } catch (ClosedChannelException e) {
//                        e.printStackTrace();
                        return null;
                    }
                }
                return key;
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

    /**
     * 解除注册
     *
     * @param channel
     * @param selector
     */
    private static void unRegisterSelection(SocketChannel channel, Selector selector,
                                            Map<SelectionKey, Runnable> map) {
        if (channel.isRegistered()) {
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                //取消所有监听   interestOps 是设置->可以指定监听或不监听 具体事件
                key.cancel();
                map.remove(key);
                selector.wakeup();
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