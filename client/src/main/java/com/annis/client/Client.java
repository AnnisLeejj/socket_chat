package com.annis.client;

import java.io.IOException;

public class Client {
    public static void main(String[] args) {
        ServerInfo info = ClientSearcher.searchServer(10);
        System.out.println(info);

        if (info != null) {
            try {
                TCPClient.linkWith(info);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
