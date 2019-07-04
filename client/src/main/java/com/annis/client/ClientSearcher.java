package com.annis.client;

import com.annis.foo.constants.UDPConstants;
import com.annis.lib.utils.ByteUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * 客户端搜索类
 */
public class ClientSearcher {
    private static final int LISTEN_PORT = UDPConstants.PORT_CLIENT_RESPONSE;

    public static ServerInfo searchServer(int timeout) {
        System.out.println("UDPSearchServer Started.");

        //成功收到回送的栅格
        CountDownLatch receiverLatch = new CountDownLatch(1);
        Listener listener = null;
        try {
            listener = listen(receiverLatch);
            sendBroadcast();
            receiverLatch.await(timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //完成
        System.out.println("UDPSearched Finished.");
        if (listener == null) {
            return null;
        }
        List<ServerInfo> devices = listener.getServerAndClose();
        if (devices.size() > 0) {
            return devices.get(0);
        }
        return null;
    }

    private static Listener listen(CountDownLatch receiveLatch) throws InterruptedException {
        System.out.println("UDPSearcher start listen.");
        CountDownLatch startDownLatch = new CountDownLatch(1);
        Listener listener = new Listener(LISTEN_PORT, startDownLatch, receiveLatch);
        listener.start();
        startDownLatch.await();
        return listener;
    }

    private static void sendBroadcast() throws IOException {
        System.out.println("UDPSearcher sendBroadCast started.");
        //作为搜索方,让系统自动分配端口
        DatagramSocket ds = new DatagramSocket();

        //构建一份请求数据
        ByteBuffer byteBuffer = ByteBuffer.allocate(128);
        //头部
        byteBuffer.put(UDPConstants.HEADER);
        //cmd命令
        byteBuffer.putShort((short) 1);
        //回送的端口 信息
        byteBuffer.putInt(LISTEN_PORT);

        //直接构建packet
        DatagramPacket requestPacket = new DatagramPacket(byteBuffer.array(), byteBuffer.position() + 1);
        //广播地址
        requestPacket.setAddress(InetAddress.getByName("255.255.255.255"));
        //设置服务器 端口
        requestPacket.setPort(UDPConstants.PORT_SERVER);

        //发送
        ds.send(requestPacket);
        ds.close();
        //完成
        System.out.println("UDPSearcher sendBroadcast finished.");
    }

    private static class Listener extends Thread {
        private final int listenPort;
        private final CountDownLatch startDownLatch;
        private final CountDownLatch receiveDownLatch;

        private final List<ServerInfo> serverInfoList = new ArrayList<>();
        private final byte[] buffer = new byte[128];
        private final int minLen = UDPConstants.HEADER.length + 2 + 4;
        private boolean done = false;
        private DatagramSocket ds = null;

        private Listener(int listenPort, CountDownLatch startDownLatch, CountDownLatch receiveDownLatch) {
            super();
            this.listenPort = listenPort;
            this.startDownLatch = startDownLatch;
            this.receiveDownLatch = receiveDownLatch;
        }

        @Override
        public void run() {
            //通知已启动
            startDownLatch.countDown();
            try {
                //监听回送端口
                ds = new DatagramSocket(listenPort);
                DatagramPacket receivePack = new DatagramPacket(buffer, buffer.length);
                //构建接收实体
                while (!done) {
                    //接收
                    if (ds.isClosed()) {
                        continue;
                    }
                    ds.receive(receivePack);

                    //打印接收到的信息 与发送者的信息
                    String ip = receivePack.getAddress().getHostAddress();
                    int port = receivePack.getPort();
                    int dataLen = receivePack.getLength();
                    byte[] data = receivePack.getData();
                    boolean isValid = dataLen >= minLen &&
                            ByteUtils.startsWith(data, UDPConstants.HEADER);
                    System.out.println("UDPSearcher receive form:"
                            + ip + ":" + port + "\t dataValid:" + isValid);

                    if (!isValid) {
                        //无效继续
                        continue;
                    }
                    //使用ByteBuffer 并跳过头部
                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, UDPConstants.HEADER.length, dataLen);
                    final short cmd = byteBuffer.getShort();
                    final int serverPort = byteBuffer.getInt();
                    if (cmd != 2 || serverPort <= 0) {
                        System.out.println("UDPSearcher receive cmd:" + cmd + "\tserverPort:" + serverPort);
                        continue;
                    }

                    String sn = new String(buffer, minLen, dataLen - minLen);
                    ServerInfo info = new ServerInfo(sn, ip, serverPort);
                    serverInfoList.add(info);
                    /**
                     * 这里是不科学的不能获取到更多的服务器信息
                     * 应该是再跑线程计时
                     */
                    //成功接收到一份
                    receiveDownLatch.countDown();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close();
            }
            System.out.println("UDPSearcher listener finished.");
        }

        List<ServerInfo> getServerAndClose() {
            done = true;
            close();
            return serverInfoList;
        }

        private void close() {
            if (ds != null) {
                ds.close();
                ds = null;
            }
        }
    }
}