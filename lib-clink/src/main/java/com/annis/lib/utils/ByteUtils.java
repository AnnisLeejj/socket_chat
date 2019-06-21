package com.annis.lib.utils;

public class ByteUtils {
    public static boolean startsWith(byte[] data, byte[] header) {
        if (data == null) return false;
        if (header == null) return true;
        if (data.length < header.length) return false;
        for (int i = 0; i < header.length; i++) {
            if (data[i] != header[i]) {
                return false;
            }
        }
        return true;
    }
}
