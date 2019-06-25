package com.annis.lib.core;

import java.io.IOException;

public interface Receiver extends Cloneable{
    boolean receiveAsynce(IoArgs.IoArgsEventListener listener)throws IOException;
}
