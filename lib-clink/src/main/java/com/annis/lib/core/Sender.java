package com.annis.lib.core;

import java.io.IOException;

public interface Sender extends Cloneable {
    boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException;
}
