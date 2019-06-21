package com.annis.server.handler;

import com.annis.lib.utils.CloseUtils;
import com.sun.istack.internal.NotNull;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端消息处理
 */
public class ClientHandler {
    private final Socket socket;
    private final ClientReadHandler readHandler;
    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;

    public ClientHandler(@NotNull Socket socket, @NotNull ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socket = socket;
        readHandler = new ClientReadHandler(socket.getInputStream());
        writeHandler = new ClientWriteHandler(socket.getOutputStream());
        this.clientHandlerCallback = clientHandlerCallback;
        System.out.println("新客户端连接:" + socket.getInetAddress() + ":" + socket.getPort());

    }


    public void send(String message) {
        writeHandler.send(message);

    }

    public void readToPrint() {
        readHandler.start();
    }

    public void exit() {
        System.out.println("客户端已退出:" + socket.getInetAddress() + ":" + socket.getPort());
        readHandler.exit();
        writeHandler.exit();
        CloseUtils.close(socket);
    }

    private void exitBySelf() {
        exit();

    }

    public interface ClientHandlerCallback {
        //自身关闭通知
        void onSelfClose(ClientHandler handler);

        //收到信息时通知
        void onNewMessageArrived(ClientHandler handler, String msg);
    }


    class ClientReadHandler extends Thread {
        private boolean done = false;
        private final InputStream inputStream;

        ClientReadHandler(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                //得到输入流,用于接收数据
                BufferedReader socketInput = new BufferedReader(
                        new InputStreamReader(inputStream));
                do {
                    Thread.sleep(500);
                    //客户端拿到一条数据
                    String str = socketInput.readLine();
                    if (str == null) {
                        System.out.println("客户端已无法读取数据");
                        ClientHandler.this.exitBySelf();
                        break;
                    }
                    //打印到屏幕,并回送数据长度
                    System.out.println("接收到消息(" + socket.getInetAddress() + ":" + socket.getPort() + "):" + str);

                } while (!done);
            } catch (IOException e) {
                if (!done) {
                    System.out.println("连接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
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

    class ClientWriteHandler {
        private boolean done = false;
        private final PrintStream printStream;
        private final ExecutorService executorService;

        ClientWriteHandler(OutputStream outputStream) {
            this.printStream = new PrintStream(outputStream);
            this.executorService = Executors.newSingleThreadExecutor();
        }

        void send(String message) {
            if (!executorService.isShutdown())
                executorService.execute(new WriteRunnable(message));
        }

        void exit() {
            done = true;
            CloseUtils.close(printStream);
            executorService.shutdownNow();
        }

        class WriteRunnable implements Runnable {
            private final String msg;

            WriteRunnable(String msg) {
                this.msg = msg;
            }

            @Override
            public void run() {
                if (ClientWriteHandler.this.done)
                    return;
                try {
                    ClientWriteHandler.this.printStream.println(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
