package com.annis.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerTest {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket =new ServerSocket(30512);
        System.out.println("服务端连接："+serverSocket.getInetAddress()+":"+serverSocket.getLocalPort());
        Socket client = serverSocket.accept();
        serverSocket.getInetAddress();
        System.out.println("客户端连接："+client.getInetAddress()+":"+client.getPort());
        client.close();
        serverSocket.close();
    }
}
