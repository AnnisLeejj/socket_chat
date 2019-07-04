package com.annis.client;

import com.annis.lib.core.IoContext;
import com.annis.lib.impl.IoSelectorProvider;

import java.io.*;
import java.net.Socket;

public class Client {
    public static void main(String[] args) throws IOException {
        //oldLogic();

        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();
        ServerInfo info = ClientSearcher.searchServer(100);
        System.out.println("Server:" + info);

        if (info != null) {
            TCPClient tcpClient = null;
            try {
                tcpClient = TCPClient.startWith(info);
                if (tcpClient == null) {
                    return;
                }
                write(tcpClient);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (tcpClient != null) {
                    tcpClient.exit();
                }
            }
        }
        IoContext.close();
    }

    private static void write(TCPClient tcpClient) throws IOException {
        //构建键盘输入流
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));

        do {
            //键盘读取一行
            String str = input.readLine();
            //发送到服务器
            tcpClient.send(str);
            tcpClient.send(str);
            tcpClient.send(str);
            tcpClient.send(str);

            if ("00bye00".equalsIgnoreCase(str)) {
                break;
            }
        } while (true);
    }
//    private void oldLogic() {
//        ServerInfo info = ClientSearcher.searchServer(10);
//        System.out.println("Server:" + info);
//
//        if (info != null) {
//            TCPClient tcpClient = null;
//            try {
//                tcpClient = TCPClient.startWith(info);
//                if (tcpClient == null) return;
//                write(tcpClient);
//            } catch (IOException e) {
//                e.printStackTrace();
//            } finally {
//                if (tcpClient != null) {
//                    tcpClient.exit();
//                }
//            }
//        }
//    }

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
}
