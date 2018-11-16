package net.freehaven.tor.control;

import org.apache.commons.io.IOUtils;

import java.io.*;

/**
 * Created by vera on 18-11-5.
 */
public class Utils {
    public static byte[] read(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        int offset = 0;
        while (offset < b.length) {
            int read = in.read(b, offset, b.length - offset);
            if (read == -1) throw new EOFException();
            offset += read;
        }
        return b;
    }

    public static String convert(InputStream inputStream){
        String result = "";
        try {
            result = IOUtils.toString(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
