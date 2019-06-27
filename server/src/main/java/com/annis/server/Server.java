package com.annis.server;

import com.annis.foo.constants.TCPConstants;
import com.annis.lib.core.IoContext;
import com.annis.lib.impl.IoSelectorProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Server {
    public static void main(String[] args) throws IOException {
        IoContext.setup().ioProvider(new IoSelectorProvider()).start();

        /*
         * tcp 连接
         */
        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER);
        boolean isSucceed = tcpServer.start();
        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        /*
         * udp广播
         */
        ServerProvider.start(TCPConstants.PORT_SERVER);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

        String str;
        do {
            str = bufferedReader.readLine();
            tcpServer.broadcast(str);
        } while (!str.equalsIgnoreCase("00bye00"));

        ServerProvider.stop();
        tcpServer.stop();

        IoContext.close();
    }
}
