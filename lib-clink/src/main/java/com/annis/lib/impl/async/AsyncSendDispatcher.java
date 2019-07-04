package com.annis.lib.impl.async;

import com.annis.lib.core.IoArgs;
import com.annis.lib.core.SendDispatcher;
import com.annis.lib.core.SendPacket;
import com.annis.lib.core.Sender;
import com.annis.lib.utils.CloseUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSendDispatcher implements SendDispatcher {
    private final Sender sender;
    /**
     * 非阻塞,线程安全的队列
     */
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue<>();

    /**
     * 判断当前是否在发送
     */
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);


    private IoArgs ioArgs = new IoArgs();
    private SendPacket packetTemp;

    /**
     * 数据包 长度
     */
    private int total;
    /**
     * 数据包 当前处理进度
     */
    private int position;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
    }

    @Override
    public void send(SendPacket sendPacket) {
        queue.offer(sendPacket);
        if (isSending.compareAndSet(false, true)) {
            sendNextPacket();
        }
    }

    @Override
    public void cancel(SendPacket sendPacket) {

    }

    /**
     * 获取队列的消息包
     *
     * @return
     */
    private SendPacket takePacket() {
        /*
        这里是阻塞吗
         */
        SendPacket sendPacket = queue.poll();
        if (sendPacket != null && sendPacket.isCanceled()) {
            return takePacket();
        }
        return sendPacket;
    }

    private void sendNextPacket() {
        SendPacket temp = packetTemp;
        if (temp != null) {
            CloseUtils.close(temp);
        }

        SendPacket packet = packetTemp = takePacket();
        if (packet == null) {
            isSending.set(false);
            return;
        }

        total = packet.length();
        position = 0;

        sendCurrentPacket();

    }

    private void sendCurrentPacket() {
        IoArgs args = ioArgs;
        //开始,清理
        args.startWriting();
        if (position >= total) {
            //当前数据已发送完成,发送下一条
            sendNextPacket();
            return;
        } else if (position == 0) {
            //该条信息的首包,需要携带长度信息
            args.writeLength(total);
        }

        byte[] bytes = packetTemp.bytes();
        //把bytes 的数据写入到IoArgs
        int count = args.readFrom(bytes, position);
        position += count;

        //完成封装
        args.finishWriting();

        try {
            sender.sendAsync(args, ioArgsEventListener);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            SendPacket packet = this.packetTemp;
            if (packet != null) {
                packetTemp = null;
                CloseUtils.close(packet);
            }
        }
    }

    private final IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            //继续发送当前信息
            sendCurrentPacket();
        }
    };
}
