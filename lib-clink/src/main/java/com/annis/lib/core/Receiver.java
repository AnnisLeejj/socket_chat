package com.annis.lib.core;

import java.io.Closeable;
import java.io.IOException;

public interface Receiver extends Closeable {
    void setReceiveListener(IoArgs.IoArgsEventListener listener);

    boolean receiveAsynce(IoArgs args) throws IOException;
}
