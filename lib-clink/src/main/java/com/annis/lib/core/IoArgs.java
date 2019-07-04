package com.annis.lib.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 数据包
 * 一条信息(数据包)可能拆分到  多个 数据包{@link IoArgs} 中
 */
public class IoArgs {
    private int limit = 256;
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    /**
     * 从 bytes 中读取数据
     *
     * @return
     */
    public int readFrom(byte[] bytes, int offset) {
        int size = Math.min(bytes.length - offset, buffer.remaining());
        buffer.put(bytes, offset, size);
        return size;
    }

    /**
     * 写入数据到bytes中
     *
     * @return
     */
    public int writeTo(byte[] bytes, int offset) {
        int size = Math.max(bytes.length - offset, buffer.remaining());
        buffer.get(bytes, offset, size);
        return size;
    }

    /**
     * 从SocketChannel读取数据
     *
     * @param channel
     * @return
     * @throws IOException
     */
    public int readFrom(SocketChannel channel) throws IOException {
        startWriting();

        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            //todo 理解read 的含义
            int len = channel.read(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        finishWriting();
        return bytesProduced;
    }

    /**
     * 写数据到SocketChannel
     *
     * @param channel
     * @return
     * @throws IOException
     */
    public int writeTo(SocketChannel channel) throws IOException {
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.write(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        return bytesProduced;
    }

    /**
     * 开始写入数据到IoArgs
     */
    public void startWriting() {
        buffer.clear();
        //定义容纳区间
        buffer.limit(limit);
    }

    /**
     * 写完数据后调用
     */
    public void finishWriting() {
        buffer.flip();
    }

//    public String bufferString() {
//        //丢弃换行符
//        return new String(byteBuffer, 0, buffer.position() - 1);
//    }

    /**
     * 设置单次写操作的容纳区间
     *
     * @param limit 区间大小
     */
    public void limit(int limit) {
        this.limit = limit;
    }

    /**
     * 写入 本次信息的总长度
     *
     * @param total
     */
    public void writeLength(int total) {
        buffer.putInt(total);
    }

    /**
     * 读取 本次信息的总长度
     */
    public int reaLength() {
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
    }

    public interface IoArgsEventListener {

        void onStarted(IoArgs args);

        void onCompleted(IoArgs args);
    }
}