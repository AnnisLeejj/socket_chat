package com.annis.lib.core;

import java.io.Closeable;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

public interface IoProvider extends Closeable {
    boolean registerInput(SocketChannel channel, HandleInputCallback callback) throws ClosedChannelException;

    boolean registerOutput(SocketChannel channel, HandleOutputCallback callback);

    boolean unRegisterInput(SocketChannel channel);

    boolean unRegisterOutput(SocketChannel channel);

    abstract class HandleInputCallback implements Runnable {
        @Override
        public void run() {
            canProviderInput();
        }

        protected abstract void canProviderInput();
    }

    abstract class HandleOutputCallback implements Runnable {
        private Object attach;

        @Override
        public void run() {
            canProviderOutput(attach);
        }

        public void setAttach(Object attach) {
            this.attach = attach;
        }

        public final <T> T getAttach() {
            @SuppressWarnings({"UnncessaryLocalVariable", "unchecked"})
            T attach = (T) this.attach;
            return attach;
        }

        protected abstract void canProviderOutput(Object o);
    }

}
