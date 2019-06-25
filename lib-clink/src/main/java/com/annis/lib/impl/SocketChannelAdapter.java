package com.annis.lib.impl;

import com.annis.lib.core.IoArgs;
import com.annis.lib.core.Receiver;
import com.annis.lib.core.Sender;

import java.io.Closeable;
import java.io.IOException;

public class SocketChannelAdapter implements Sender, Receiver, Closeable
{
    @Override
    public boolean receiveAsynce(IoArgs.IoArgsEventListener listener) throws IOException {
        return false;
    }

    @Override
    public boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException {
        return false;
    }

    @Override
    public void close() throws IOException {

    }
}
