package com.annis.lib.impl.async;

import com.annis.lib.box.StringReceivePacket;
import com.annis.lib.core.IoArgs;
import com.annis.lib.core.ReceiveDispatcher;
import com.annis.lib.core.ReceivePacket;
import com.annis.lib.core.Receiver;
import com.annis.lib.utils.CloseUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher {
    private final AtomicBoolean isCLose = new AtomicBoolean(false);
    private final Receiver receiver;
    private final ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    private ReceivePacket packetTemp;
    private byte[] buffer;
    private int total;
    private int position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        receiver.setReceiveListener(ioArgsEventListener);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }

    @Override
    public void stop() {

    }


    private void registerReceive() {
        try {
            receiver.receiveAsynce(ioArgs);
        } catch (IOException e) {
            closeAndeeNotify();
        }
    }

    private void closeAndeeNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void close() throws IOException {
        if (isCLose.compareAndSet(false, true)) {
            ReceivePacket packet = this.packetTemp;
            if (packet != null) {
                packetTemp = null;
                CloseUtils.close(packet);
            }
        }
    }

    private IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {
            int receiveSize;
            if (packetTemp == null) {
                receiveSize = 4;
            } else {
                receiveSize = Math.min(total - position, args.capacity());
            }
            //设置本次接受数据大小
            args.limit(receiveSize);
        }

        @Override
        public void onCompleted(IoArgs args) {
            assemblePacket(args);
            //继续接受下一条数据
            registerReceive();
        }
    };


    /**
     * 解析数据到Packet
     *
     * @param args
     */
    private void assemblePacket(IoArgs args) {
        if (packetTemp == null) {
            int length = args.reaLength();
            packetTemp = new StringReceivePacket(length);
            buffer = new byte[length];
            total = length;
            position = 0;
        }
        int count = args.writeTo(buffer, 0);
        if (count > 0) {
            packetTemp.save(buffer, count);
            position += count;
            if (position == total) {
                completePacket();
                packetTemp = null;
            }
        }
    }

    /**
     * 完成数据接受操作
     */
    private void completePacket() {
        ReceivePacket packet = this.packetTemp;
        CloseUtils.close(packet);
        callback.onReceivePacketCompleted(packet);
    }
}
