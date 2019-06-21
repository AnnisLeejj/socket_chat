package com.annis.server;

import com.annis.foo.constants.UDPConstants;
import com.annis.lib.utils.ByteUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * 设备信息提供  (用于接收广播)
 */
public class ServerProvider {
    private static Provider PROVIDER_INSTANCE;

    static void start(int port) {
        stop();
        String sn = UUID.randomUUID().toString();
        Provider provider = new Provider(sn, port);
        provider.start();
        PROVIDER_INSTANCE = provider;
    }

    static void stop() {
        if (PROVIDER_INSTANCE != null) {
            PROVIDER_INSTANCE.exit();
            PROVIDER_INSTANCE = null;
        }
    }

    private static class Provider extends Thread {
        private final byte[] sn;
        private final int port;

        private boolean done = false;
        private DatagramSocket ds = null;

        final byte[] buffer = new byte[128];

        private Provider(String sn, int port) {
            this.sn = sn.getBytes();
            this.port = port;
        }

        @Override
        public void run() {
            System.out.println("UDPProvider Started.");
            try {
                ds = new DatagramSocket(UDPConstants.PORT_SERVER);
                //接收消息的Packet
                DatagramPacket receivePack = new DatagramPacket(buffer, buffer.length);
                while (!done) {
                    //接收
                    ds.receive(receivePack);
                    //打印接收到的信息与发送者的信息
                    //发送者的IP地址
                    String clienIP = receivePack.getAddress().getHostAddress();
                    int clientPort = receivePack.getPort();
                    int clientDataLen = receivePack.getData().length;
                    byte[] clientData = receivePack.getData();
                    boolean isValid = clientDataLen >= (UDPConstants.HEADER.length + 2 + 4)
                            && ByteUtils.startsWith(clientData, UDPConstants.HEADER);

                    System.out.println("ServerProvider receive form ip:" +
                            clienIP + ":" + clientPort + "\t dataValid:" + isValid);
                    if (!isValid) {
                        //无效 继续
                        continue;
                    }
                    //解析命令 与 回送端口
                    //头部验证信息长度
                    int index = UDPConstants.HEADER.length;
                    short cmd = (short) ((clientData[index++] << 8) | (clientData[index++] & 0xFF));

                    int responsePort = (((clientData[index++] & 0xFF) << 24) |
                            ((clientData[index++] & 0xFF) << 16) |
                            ((clientData[index++] & 0xFF) << 8) |
                            ((clientData[index++] & 0xFF)));

                    //判断合法性
                    if (cmd == 1 && responsePort > 0) {
                        //构建一份回送数据
                        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                        byteBuffer.put(UDPConstants.HEADER);
                        byteBuffer.putShort((short) 2);
                        byteBuffer.putInt(port);
                        byteBuffer.put(sn);
                        int len = byteBuffer.position();
                        //直接根据发送者构建一份回送消息
                        DatagramPacket responsePacket = new DatagramPacket(buffer, len,
                                receivePack.getAddress(), responsePort);
                        ds.send(responsePacket);
                        System.out.println("ServerProvider response to:" + clienIP + ":" + clientPort + "\tdataLen:" + len);
                    } else {
                        System.out.println("ServerProvider receive cmd nonsupport; cmd:" + cmd + "\t port:" + port);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private void close() {
            if (ds != null) {
                ds.close();
                ds = null;
            }
        }

        /**
         * 提供结束
         */
        void exit() {
            done = true;
            close();
        }
    }
}
