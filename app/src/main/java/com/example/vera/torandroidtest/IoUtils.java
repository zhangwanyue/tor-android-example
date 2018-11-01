package com.example.vera.torandroidtest;

import android.support.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
}
