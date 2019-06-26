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

        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER);
        boolean isSucceed = tcpServer.start();
        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        ServerProvider.start(TCPConstants.PORT_SERVER);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

        String str = null;
        do {
            String line = bufferedReader.readLine();
            tcpServer.broadcast(line);
        } while (!"00bye00".equalsIgnoreCase(str));

        ServerProvider.stop();
        tcpServer.stop();

        IoContext.close();
    }
}
