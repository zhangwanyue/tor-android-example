package com.example.vera.torandroidtest;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Scanner;

/**
 * Created by vera on 18-10-30.
 */

public class getRawFileTest {
    Context appContext;

    public getRawFileTest(Context appContext) {
        this.appContext = appContext;
    }

    public void readRawFile(){
       String fileName = "hello";
       InputStream ins = getAndroidResourceInputStream(fileName, "");
       String str = convert(ins);
       Log.i("TorAndroidTest", str);
    }

    public InputStream getAndroidResourceInputStream(String name, String extension) {
        Resources res = appContext.getResources();
        // extension is ignored on Android, resources are retrieved without it
        int resId =
                res.getIdentifier(name, "raw", appContext.getPackageName());
        return res.openRawResource(resId);
    }

    public String convert(InputStream inputStream){
        Scanner scanner = new Scanner(inputStream);
        return scanner.useDelimiter("\\A").next();
    }
}
