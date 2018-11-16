package com.example.vera.torandroidtest.utils;

import android.support.annotation.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by vera on 18-10-30.
 */

public class IoUtils {
    public static void copyAndClose(InputStream in, OutputStream out) {
        byte[] buf = new byte[4096];
        try {
            while (true) {
                int read = in.read(buf);
                if (read == -1) break;
                out.write(buf, 0, read);
            }
            in.close();
            out.flush();
            out.close();
        } catch (IOException e) {
            tryToClose(in);
            tryToClose(out);
        }
    }

    private static void tryToClose(@Nullable Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException e) {
            // We did our best
        }
    }

    public static void read(InputStream in, byte[] b) throws IOException {
        int offset = 0;
        while (offset < b.length) {
            int read = in.read(b, offset, b.length - offset);
            if (read == -1) throw new EOFException();
            offset += read;
        }
    }

    // Workaround for a bug in Android 7, see
    // https://android-review.googlesource.com/#/c/271775/
    public static InputStream getInputStream(Socket s) throws IOException {
        try {
            return s.getInputStream();
        } catch (NullPointerException e) {
            throw new IOException(e);
        }
    }

    // Workaround for a bug in Android 7, see
    // https://android-review.googlesource.com/#/c/271775/
    public static OutputStream getOutputStream(Socket s) throws IOException {
        try {
            return s.getOutputStream();
        } catch (NullPointerException e) {
            throw new IOException(e);
        }
    }

    public static byte[] read(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        try {
            int offset = 0;
            while (offset < b.length) {
                int read = in.read(b, offset, b.length - offset);
                if (read == -1) throw new EOFException();
                offset += read;
            }
            return b;
        } finally {
            tryToClose(in);
        }
    }
}
