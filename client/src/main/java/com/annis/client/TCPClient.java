package com.annis.client;

import com.annis.lib.utils.CloseUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class TCPClient {
    private final Socket socket;
    private final ReadHandler readHandler;
    private final PrintStream printStream;

    public TCPClient(Socket socket, ReadHandler readHandler) throws IOException {
        this.socket = socket;
        this.readHandler = readHandler;
        printStream = new PrintStream(socket.getOutputStream());
    }

    public void exit() {
        readHandler.exit();
        CloseUtils.close(printStream);
        CloseUtils.close(socket);
    }

    public void send(String msg) {
        if (printStream != null) {
            printStream.println(msg);
        }
    }

    public static TCPClient startWith(ServerInfo info) throws IOException {
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

            return new TCPClient(socket, readHandler);
        } catch (Exception e) {
            System.out.println("异常关闭");
            CloseUtils.close(socket);
        }
        return null;
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
