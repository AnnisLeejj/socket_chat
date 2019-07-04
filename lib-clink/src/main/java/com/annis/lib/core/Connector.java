package com.annis.lib.core;

import com.annis.lib.box.StringReceivePacket;
import com.annis.lib.box.StringSendPacket;
import com.annis.lib.impl.SocketChannelAdapter;
import com.annis.lib.impl.async.AsyncReceiveDispatcher;
import com.annis.lib.impl.async.AsyncSendDispatcher;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.UUID;

public class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangedListener {
    private UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;
    private SendDispatcher sendDispatcher;
    private ReceiveDispatcher receiveDispatcher;

    public void setup(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        IoContext ioContext = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, ioContext.getIoProvider(), this);

        this.sender = adapter;
        this.receiver = adapter;

        sendDispatcher = new AsyncSendDispatcher(sender);
        receiveDispatcher = new AsyncReceiveDispatcher(receiver, receivePacketCallback);
        //启动接收
        receiveDispatcher.start();
//        readNextMessage();
    }

    /**
     * 发送信息
     *
     * @param msg
     */
    public void send(String msg) {
        SendPacket packet = new StringSendPacket(msg);
        sendDispatcher.send(packet);
    }

//    /**
//     * 读取信息
//     */
//    private void readNextMessage() {
//        if (receiver != null) {
//            try {
//                receiver.receiveAsynce(echoReceiveListener);
//            } catch (IOException e) {
//                System.out.println("开始接受数据异常:" + e.getMessage());
//            }
//        }
//    }

    @Override
    public void close() throws IOException {
        receiveDispatcher.close();
        sendDispatcher.close();
        sender.close();
        receiver.close();
        channel.close();
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {

    }

    //上一版,读取的操作
//    private IoArgs.IoArgsEventListener echoReceiveListener = new IoArgs.IoArgsEventListener() {
//        @Override
//        public void onStarted(IoArgs args) {
//
//        }
//
//        @Override
//        public void onCompleted(IoArgs args) {
//            //打印数据
//            onReceiveNewMessage(args.bufferString());
//            //读取下一条数据
//            readNextMessage();
//        }
    //    };
    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = new ReceiveDispatcher.ReceivePacketCallback() {
        @Override
        public void onReceivePacketCompleted(ReceivePacket packet) {
            if (packet instanceof StringReceivePacket) {
                String msg = ((StringReceivePacket) packet).string();
                onReceiveNewMessage(msg);
            }
        }
    };

    protected void onReceiveNewMessage(String msg) {
        System.out.println(key.toString() + ":" + msg);
    }

}