package com.annis.server;

import com.annis.lib.utils.CloseUtils;
import com.annis.server.handler.ClientHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 与客户端 TCP 通讯
 */
public class TCPServer implements ClientHandler.ClientHandlerCallback {
    private final int port;
    private ClientListener listener;
    private List<ClientHandler> clientHandlerList = Collections.synchronizedList(new ArrayList<>());
    private Selector selector;
    private ServerSocketChannel serverChannel;
    //转发线程池 - - 消息异步转发
    private final ExecutorService forwardingTHreadPoolExecutor;

    public TCPServer(int portServer) {
        this.port = portServer;
        forwardingTHreadPoolExecutor = Executors.newSingleThreadExecutor();
    }

    public boolean start() {
        try {
            selector = Selector.open();

            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            //设置为非阻塞
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(port));

            //注册客户端连接到达监听
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            this.serverChannel = serverChannel;

            System.out.println("服务器信息:" + serverChannel.getLocalAddress().toString());
            //启动客户端监听
            ClientListener listener = this.listener = new ClientListener();
            listener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {
        if (listener != null) {
            listener.exit();
        }

        CloseUtils.close(serverChannel);
        CloseUtils.close(selector);

        synchronized (TCPServer.this) {
            clientHandlerList.forEach(client -> {
                client.exit();
            });
            clientHandlerList.clear();
        }
        forwardingTHreadPoolExecutor.shutdownNow();
    }

    public synchronized void broadcast(String message) {
        clientHandlerList.forEach(client -> {
            client.send(message);
        });
    }

    @Override
    public synchronized void onSelfClose(ClientHandler handler) {
        clientHandlerList.remove(handler);
    }

    @Override
    public void onNewMessageArrived(ClientHandler handler, String msg) {
        //打印到屏幕
        System.out.println("Received(" + handler.getClientInfo() + "):" + msg);

        //不能阻塞
        forwardingTHreadPoolExecutor.execute(() -> {
            synchronized (TCPServer.this) {
                clientHandlerList.forEach(client -> {
                    if (!client.equals(handler)) {//不给发送的客户端发送
                        client.send(msg);
                    }
                });
            }
        });
    }

    private class ClientListener extends Thread {
        //        private ServerSocket server;
        private boolean done = false;
//
//        private ClientListener() throws IOException {
////            server = new ServerSocket(port);
////            System.out.println("服务器信息:" + server.getInetAddress() + ":" + server.getLocalPort());
//        }

        @Override
        public void run() {
            Selector selector = TCPServer.this.selector;
            System.out.println("服务器准备就绪~~");
            //等待客户端连接
            do {
                try {
                    //select() 是一个阻塞方法
                    if (selector.select() == 0) {
                        if (done) break;
                        continue;
                    }
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        if (done) {
                            break;
                        }
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        //检查当前的key 的状态是否是我们关注的事件

                        //客户端到达状态
                        if (key.isAcceptable()) {
                            ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                            //非阻塞状态拿到一个客户端
                            SocketChannel socketChannel = channel.accept();
                            try {
                                //客户端构建异步线程
                                ClientHandler clientHandler = new ClientHandler(socketChannel, TCPServer.this);
                                //读取信息并打印
                                //clientHandler.readToPrint();

                                synchronized (TCPServer.this) {
                                    clientHandlerList.add(clientHandler);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("客户端连接异常:" + e.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    //continue;
                }
            } while (!done);
            System.out.println("服务器已关闭!");
        }

        public void exit() {
            done = true;
            //唤醒当前的阻塞
            selector.wakeup();
        }
    }
}
