package com.annis.client;

import java.io.IOException;
import java.net.*; 
import java.util.ArrayList;
import java.util.List;

public class ClientTest {
    private static boolean done;

    public static void main(String[] args) throws IOException {
        performanceTest();
       // Selector selector;
    }

    /**
     * 性能测试
     */
    private static void performanceTest() throws IOException {
        ServerInfo info = ClientSearcher.searchServer(10);
        System.out.println("Server:" + info);
        if (info == null)
            return;

        int size = 0;

        final List<TCPClient> tcpClients = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            try {
                TCPClient tcpClient = TCPClient.startWith(info);
                if (tcpClient == null) {
                    System.out.println("连接异常~~~");
                    continue;
                }

                tcpClients.add(tcpClient);
                System.out.println("连接成功:" + (++size));
            } catch (IOException e) {
                System.out.println("连接异常");
            }
            //过快服务器可能会放弃连接
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Runnable runnable = () -> {
            while (!done) {
                tcpClients.forEach(e -> {
                    e.send("Hello~~");
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();

        //等待线程完成
        System.in.read();
        done = true;
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //结束客户端
        tcpClients.forEach(TCPClient::exit);

    }

    private static void connect() throws IOException {
        Socket socket = new Socket();
//        socket.setSoTimeout(2000);
        socket.connect(new InetSocketAddress(InetAddress.getByName("192.168.2.101"), 30512), 2000);

    }
}
