package com.annis.lib.core;

import java.io.Closeable;

/**
 * 公共数据封装
 * 提供了类型以及基本的长度定义
 */
public abstract class Packet implements Closeable {
    /**
     * 数据类型
     */
    protected byte type;
    /**
     * 数据长度
     */
    protected int length;

    public byte type() {
        return type;
    }

    public int length() {
        return length;
    }
}
