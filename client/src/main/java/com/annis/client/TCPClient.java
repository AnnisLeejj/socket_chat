package com.annis.client;

import com.annis.lib.utils.CloseUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class TCPClient {
    public static void linkWith(ServerInfo info) throws IOException {
        Socket socket = new Socket();
        //设置超时
        socket.setSoTimeout(3000);
        //连接本地,端口20000;超时时间3000ms
        socket.connect(new InetSocketAddress(InetAddress.getByName(info.getAddress()), info.getPort()), 3000);
        System.out.println("已发起服务器连接,并进入后续流程~~");
        System.out.println("客户端信息:" + socket.getLocalAddress() + ":" + socket.getLocalPort());
        System.out.println("服务端信息:" + socket.getInetAddress() + ":" + socket.getPort());

        try {
            ReadHandler readHandler = new ReadHandler(socket.getInputStream());
            readHandler.start();
            //发送接收数据
            write(socket);

            //退出操作
            readHandler.exit();
        } catch (Exception e) {
            System.out.println("异常关闭");
        }
        //释放资源
        socket.close();
        System.out.println("客户端已退出~");
    }

    private static void write(Socket client) throws IOException {
        //构建键盘输入流
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));

        //得到Socket 输出流,并转换为打印流
        OutputStream outputStream = client.getOutputStream();
        PrintStream socketPrintStream = new PrintStream(outputStream);

        do {
            //键盘读取一行
            String str = input.readLine();
            //发送到服务器
            socketPrintStream.println(str);

            if ("00bye00".equalsIgnoreCase(str)) {
                break;
            }
        } while (true);

        //释放资源
        socketPrintStream.close();
    }

    static class ReadHandler extends Thread {
        private boolean done = false;
        private final InputStream inputStream;

        ReadHandler(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                //得到输入流,用于接收数据
                BufferedReader socketInput = new BufferedReader(
                        new InputStreamReader(inputStream));
                do {
                    String str;
                    try {
                        //客户端拿到一条数据
                        str = socketInput.readLine();
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                    if (str == null) {
                        System.out.println("连接已关闭,无法读取数据");
                        break;
                    }
                    //打印到屏幕,并回送数据长度
                    System.out.println("接收到 服务器消息:" + str);

                } while (!done);
            } catch (IOException e) {
                if (!done) {
                    System.out.println("连接异常断开:" + e.getMessage());
                }
            } finally {
                //连接关闭
                CloseUtils.close(inputStream);
            }
        }

        void exit() {
            done = true;
            CloseUtils.close(inputStream);
        }
    }
}
