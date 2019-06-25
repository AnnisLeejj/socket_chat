package com.annis.lib.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class IoArgs {
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer bbuffer = ByteBuffer.wrap(byteBuffer);

    public int read(SocketChannel channel) throws IOException {
        bbuffer.clear();
        return channel.read(bbuffer);
    }

    public int write(SocketChannel channel) throws IOException {
        return channel.write(bbuffer);
    }

    public String bufferString() {
        //丢弃换行符
        return new String(byteBuffer, 0, bbuffer.position() - 1);
    }

    public interface IoArgsEventListener {
        void onStarted(IoArgs args);

        void onCompleted(IoArgs args);
    }
}