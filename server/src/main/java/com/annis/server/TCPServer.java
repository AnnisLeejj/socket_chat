package com.annis.server;

import com.annis.server.handler.ClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 与客户端 TCP 通讯
 */
public class TCPServer implements ClientHandler.ClientHandlerCallback {
    private final int port;
    private ClientListener mListener;
    private List<ClientHandler> clientHandlerList = Collections.synchronizedList(new ArrayList<>());

    //转发线程池 - - 消息异步转发
    private final ExecutorService forwardingTHreadPoolExecutor;

    public TCPServer(int portServer) {
        this.port = portServer;
        forwardingTHreadPoolExecutor = Executors.newSingleThreadExecutor();
    }

    public boolean start() {
        try {
            ClientListener listener = new ClientListener(port);
            mListener = listener;
            listener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {
        if (mListener != null) {
            mListener.exit();
        }
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
        private ServerSocket server;
        private boolean done = false;

        private ClientListener(int port) throws IOException {
            server = new ServerSocket(port);
            System.out.println("服务器信息:" + server.getInetAddress() + ":" + server.getLocalPort());
        }

        @Override
        public void run() {
            System.out.println("服务器准备就绪~~");
            //等待客户端连接
            do {
                Socket client;
                try {
                    client = server.accept();
                } catch (IOException e) {
                    continue;
                }
                try {
                    //客户端构建异步线程
                    ClientHandler clientHandler = new ClientHandler(client, TCPServer.this);
                    //读取信息并打印
                    clientHandler.readToPrint();

                    synchronized (TCPServer.this) {
                        clientHandlerList.add(clientHandler);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("客户端连接异常:" + e.getMessage());
                }
            } while (!done);
            System.out.println("服务器已关闭!");
        }

        public void exit() {
            done = true;
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
