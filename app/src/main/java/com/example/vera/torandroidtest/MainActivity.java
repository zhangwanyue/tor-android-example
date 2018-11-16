package com.example.vera.torandroidtest;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipInputStream;

import static java.util.logging.Level.INFO;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Non-UI Context
        Context appContext = getApplicationContext();
        File torDirectory = appContext.getDir("tor", MODE_PRIVATE);//torDirectory: /data/data/com.example.vera.torandroidtest/app_tor
        // new one TorPlugin
        TorPlugin torPlugin = new TorPlugin(appContext, torDirectory);
        // run torPlugin
        new Thread(torPlugin).start();
    }
}
