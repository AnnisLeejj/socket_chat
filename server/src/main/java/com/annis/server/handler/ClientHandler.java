package com.annis.server.handler;

import com.annis.lib.core.Connector;
import com.annis.lib.utils.CloseUtils;
import com.sun.istack.internal.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端消息处理
 */
public class ClientHandler extends Connector {
    //    private final Connector connector;
    //    private final SocketChannel socketChannel;
//    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;
    private final String clientInfo;

    public ClientHandler(@NotNull SocketChannel socketChannel, @NotNull ClientHandlerCallback clientHandlerCallback) throws IOException {
//        this.socketChannel = socketChannel;
        this.clientInfo = socketChannel.getRemoteAddress().toString();

        this.clientHandlerCallback = clientHandlerCallback;

        setup(socketChannel);

//        connector = new Connector() {
//            @Override
//            public void onChannelClosed(SocketChannel channel) {
//                super.onChannelClosed(channel);
//                exitBySelf();
//            }
//
//            @Override
//            protected void onReceiveNewMessage(String msg) {
//                super.onReceiveNewMessage(msg);
//                clientHandlerCallback.onNewMessageArrived(ClientHandler.this, msg);
//            }
//        };
//        connector.setup(socketChannel);
        //客户端信息读取
//        Selector readSelector = Selector.open();
//        socketChannel.register(readSelector, SelectionKey.OP_READ);
//        readHandler = new ClientReadHandler(readSelector);

//        Selector writeSelector = Selector.open();
//        socketChannel.register(writeSelector, SelectionKey.OP_WRITE);
//        writeHandler = new ClientWriteHandler(writeSelector);


        System.out.println("新客户端连接:" + clientInfo);
    }

//    public void send(String message) {
//        writeHandler.send(message);
//    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        exitBySelf();
    }

    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClose(this);
    }

    public void exit() {
        CloseUtils.close(this);
        // writeHandler.exit();
        // CloseUtils.close(socketChannel);
        System.out.println("客户端已退出:" + clientInfo);
    }

    @Override
    protected void onReceiveNewMessage(String msg) {
        super.onReceiveNewMessage(msg);
        clientHandlerCallback.onNewMessageArrived(this,msg);
    }

    public interface ClientHandlerCallback {

        //自身关闭通知
        void onSelfClose(ClientHandler handler);

        //收到信息时通知
        void onNewMessageArrived(ClientHandler handler, String msg);
    }

    /**
     * 读取客服端发送来的信息
     */
    class ClientReadHandler extends Thread {
        private boolean done = false;
        private final Selector readSelector;
        private final ByteBuffer byteBuffer;

        ClientReadHandler(Selector readSelector) {
            this.readSelector = readSelector;
            this.byteBuffer = ByteBuffer.allocate(256);
        }

        @Override
        public void run() {
            try {
                do {
                    //客户端拿到一条数据
                    if (readSelector.select() == 0) {
                        if (done) {
                            break;
                        }
                        continue;
                    }

                    Iterator<SelectionKey> iterator = readSelector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        if (key.isReadable()) {
                            SocketChannel client = (SocketChannel) key.channel();

                            //读取时,清空上次读取内容
                            int read = client.read(byteBuffer);
                            if (read >= 0) {
                                // assert readFrom == byteBuffer.position();
                                // -1 是为了丢弃换行符
                                String str = new String(byteBuffer.array(), 0, read - 1);

                                //通知到TCPServer 读取到内容
                                ClientHandler.this.clientHandlerCallback.onNewMessageArrived(ClientHandler.this, str);
                            } else {
                                System.out.println("客户端已无法读取数据");
                                ClientHandler.this.exitBySelf();
                                break;
                            }
                        }
                    }


                } while (!done);
            } catch (IOException e) {
                if (!done) {
                    System.out.println("连接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } finally {
                //连接关闭
                CloseUtils.close(readSelector);
            }
        }

        void exit() {
            done = true;
            //可能是阻塞状态,需要唤醒一下
            readSelector.wakeup();
            CloseUtils.close(readSelector);
        }
    }

    /**
     * 向客户端发送信息
     */
    class ClientWriteHandler {
        private boolean done = false;
        private final ExecutorService executorService;
        private final Selector writeSelector;
        private final ByteBuffer byteBuffer;

        ClientWriteHandler(Selector writeSelector) {
            this.writeSelector = writeSelector;
            byteBuffer = ByteBuffer.allocate(256);
            this.executorService = Executors.newSingleThreadExecutor();
        }

        void send(String message) {
            if (done) return;
            executorService.execute(new WriteRunnable(message));
        }

        void exit() {
            done = true;
            CloseUtils.close(writeSelector);
            executorService.shutdownNow();
        }

        class WriteRunnable implements Runnable {
            private final String msg;

            WriteRunnable(String msg) {
                this.msg = msg + "\n";
            }

            @Override
            public void run() {
                if (ClientWriteHandler.this.done)
                    return;
                try {
                    byteBuffer.clear();
                    byteBuffer.put(msg.getBytes());
                    //反转 ???????
                    //clear()   ->指针指向 0
                    //put() 后  ->指针指向 msg.getBytes().length
                    //如果这个时候发送,则会 发送 从当前的 position 到byteBuffer 结尾的数据
                    byteBuffer.flip();
                    while (!done && byteBuffer.hasRemaining()) {
                        int len = 0;//socketChannel.write(byteBuffer);
                        //len==0 是合法的, socketChannel 可能不能及时响应
                        if (len < 0) {//一定是有异常
                            System.out.println("客户端已无法发送数据!");
                            ClientHandler.this.exitBySelf();
                            break;
                        }
                    }
                } catch (Exception e) {
                    ClientHandler.this.exitBySelf();
                }
            }
        }
    }
}