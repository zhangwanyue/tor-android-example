package com.example.vera.torandroidtest;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.Nullable;
import android.util.Log;

import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipInputStream;


/**
 * Created by vera on 18-10-30.
 */

public class TorPlugin implements EventHandler, Runnable{
    private static final String TAG = "TorAndroidTest";
    private String architecture = "arm";
    private static final String OWNER = "__OwningControllerProcess";
    private static final int COOKIE_TIMEOUT_MS = 3000;
    private static final int COOKIE_POLLING_INTERVAL_MS = 200;
    private int CONTROL_PORT = 59051;
    private static final String[] EVENTS = {
            "CIRC", "ORCONN", "HS_DESC", "NOTICE", "WARN", "ERR"
    };
    //Activity context: getApplicationContext()
    Context appContext;
    //get tor files in android
    private File torDirectory;
    private File torFile;
    private File configFile;
    private File doneFile;
    private File geoIpFile;
    private File cookieFile;
    //socket of tor
    private volatile ServerSocket socket = null;
    private volatile Socket controlSocket = null;
    private volatile TorControlConnection controlConnection = null;
    //is running
    protected volatile boolean running = false;
    private final ConnectionStatus connectionStatus;


    public TorPlugin(Context appContext, File torDirectory){
        this.appContext = appContext;
        this.torDirectory = torDirectory;
        this.torFile = new File(torDirectory, "tor");
        this.configFile = new File(torDirectory, "torrc");
        this.doneFile = new File(torDirectory, "done");
        this.geoIpFile = new File(torDirectory, "geoip");
        this.cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
        connectionStatus = new ConnectionStatus();
    }

    @Override
    public void run() {
        if (!torDirectory.exists()) {
            if (!torDirectory.mkdirs()) {
                Log.i(TAG,"Could not create Tor directory.");
            }
        }
        // Install or update the assets if necessary
        if (!assetsAreUpToDate())installAssets();
        if (cookieFile.exists() && !cookieFile.delete())
            Log.w(TAG,"Old auth cookie not deleted");
        // Start a new Tor process
        Log.i(TAG,"Starting Tor");
        String torPath = torFile.getAbsolutePath();
        String configPath = configFile.getAbsolutePath();
        String pid = String.valueOf(getProcessId());
        Process torProcess;
        ProcessBuilder pb =
                new ProcessBuilder(torPath, "-f", configPath, OWNER, pid);
        Log.i(TAG, "torPath: " + torPath + " configPath: " + configPath + " OWNER: " + OWNER + " pid: " + pid);
        Map<String, String> env = pb.environment();
        env.put("HOME", torDirectory.getAbsolutePath());
        pb.directory(torDirectory);
        try {
            torProcess = pb.start();
            // Log the process's standard output until it detaches
            Scanner stdout = new Scanner(torProcess.getInputStream());
            Scanner stderr = new Scanner(torProcess.getErrorStream());
            while (stdout.hasNextLine() || stderr.hasNextLine()) {
                if (stdout.hasNextLine()) {
                    Log.i(TAG, stdout.nextLine());
                }
                if (stderr.hasNextLine()) {
                    Log.i(TAG, stderr.nextLine());
                }
            }
            stdout.close();
            stderr.close();

            // Wait for the process to detach or exit
            int exit = torProcess.waitFor();
            Log.i(TAG, "exit value: " + exit);

            // Wait for the auth cookie file to be created/updated
            long start = System.currentTimeMillis();
            while (cookieFile.length() < 32) {
                if (System.currentTimeMillis() - start > COOKIE_TIMEOUT_MS) {
                    Log.w(TAG,"Auth cookie not created");
                }
                Thread.sleep(COOKIE_POLLING_INTERVAL_MS);
            }
            Log.i(TAG, "Auth cookie created");
        } catch (SecurityException | IOException | InterruptedException e ) {
            Log.e(TAG, "this cause a Exception" + e.getMessage());
        }
        try {
            // Open a control connection and authenticate using the cookie file
            controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
            controlConnection = new TorControlConnection(controlSocket);
            controlConnection.authenticate(read(cookieFile));
            // Tell Tor to exit when the control connection is closed
            controlConnection.takeOwnership();
            controlConnection.resetConf(Collections.singletonList(OWNER));
            running = true;
            // Register to receive events from the Tor process
            controlConnection.setEventHandler(this);
            controlConnection.setEvents(Arrays.asList(EVENTS));
            // Check whether Tor has already bootstrapped
            String phase = controlConnection.getInfo("status/bootstrap-phase");
            if (phase != null && phase.contains("PROGRESS=100")) {
                Log.i(TAG, "Tor has already bootstrapped");
                connectionStatus.setBootstrapped();
            }
        } catch (IOException e) {
            Log.e(TAG, "open control connection error: " + e.getMessage());
        }

    }

    public void installAssets(){
        Log.i(TAG, "Installing Tor binary for " + architecture);
        InputStream in = null;
        OutputStream out = null;
        try {
            Log.i(TAG, "delete doneFile: " + doneFile.getAbsolutePath());
            doneFile.delete();
            // Unzip the Tor binary to the filesystem
            in = getTorInputStream();
            out = new FileOutputStream(torFile);
            Log.i(TAG, "copy tor file to: " + torFile.getAbsolutePath());
            IoUtils.copyAndClose(in, out);
            // Make the Tor binary executable
            if (!torFile.setExecutable(true, true)) throw new IOException();
            // Unzip the GeoIP database to the filesystem
            in = getGeoIpInputStream();
            out = new FileOutputStream(geoIpFile);
            Log.i(TAG, "copy tor file to: " + geoIpFile.getAbsolutePath());
            IoUtils.copyAndClose(in, out);
            // Copy the config file to the filesystem
            in = getConfigInputStream();
            out = new FileOutputStream(configFile);
            Log.i(TAG, "copy tor file to: " + configFile.getAbsolutePath());
            IoUtils.copyAndClose(in, out);
            Log.i(TAG, "create new doneFile: " + doneFile.getAbsolutePath());
            doneFile.createNewFile();
        } catch (IOException e) {
            tryToClose(in);
            tryToClose(out);
            Log.e(TAG, "this cause a IOException: " + e.getMessage());
        }
    }

    private InputStream getTorInputStream() throws IOException {
        InputStream in = getAndroidResourceInputStream("tor_" + architecture, ".zip");
        ZipInputStream zin = new ZipInputStream(in);
        if (zin.getNextEntry() == null) throw new IOException();
        return zin;
    }

    private InputStream getConfigInputStream() throws IOException{
        InputStream in = getAndroidResourceInputStream("torrc", "");
        return in;
    }

    private InputStream getGeoIpInputStream() throws IOException {
        InputStream in = getAndroidResourceInputStream("geoip", ".zip");
        ZipInputStream zin = new ZipInputStream(in);
        if (zin.getNextEntry() == null) throw new IOException();
        return zin;
    }


    public InputStream getAndroidResourceInputStream(String name, String extension) {
        Resources res = appContext.getResources();
        // extension is ignored on Android, resources are retrieved without it
        int resId =
                res.getIdentifier(name, "raw", appContext.getPackageName());
        return res.openRawResource(resId);
    }

    private void tryToClose(@Nullable Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException e) {
            // We did our best
        }
    }

    private boolean assetsAreUpToDate() {
        return doneFile.lastModified() > getLastUpdateTime();
    }

    protected long getLastUpdateTime() {
        try {
            PackageManager pm = appContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), 0);
            return pi.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    protected int getProcessId() {
        return android.os.Process.myPid();
    }

    private byte[] read(File f) throws IOException {
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

    @Override
    public void streamStatus(String status, String id, String target) {
    }

    @Override
    public void newDescriptors(List<String> orList) {
    }

    @Override
    public void bandwidthUsed(long read, long written) {
    }

    @Override
    public void circuitStatus(String status, String id, String path) {
        if (status.equals("BUILT") &&
                connectionStatus.getAndSetCircuitBuilt()) {
            Log.i(TAG, "First circuit built");
        }
    }

    @Override
    public void message(String severity, String msg) {
        Log.i(TAG, severity + " " + msg);
        if (severity.equals("NOTICE") && msg.startsWith("Bootstrapped 100%")) {
            connectionStatus.setBootstrapped();
        }
    }

    @Override
    public void orConnStatus(String status, String orName) {
        Log.i(TAG, "OR connection " + status + " " + orName);
        if (status.equals("CLOSED") || status.equals("FAILED")) {
            // Check whether we've lost connectivity
            Log.i(TAG, "we lost connectivity");
            // this should have an updateConn
        }
    }

    @Override
    public void unrecognized(String type, String msg) {
        if (type.equals("HS_DESC") && msg.startsWith("UPLOADED"))
            Log.i(TAG, "Descriptor uploaded");
    }



    private static class ConnectionStatus {

        // All of the following are locking: this
        private boolean networkEnabled = false;
        private boolean bootstrapped = false, circuitBuilt = false;

        private synchronized void setBootstrapped() {
            bootstrapped = true;
        }

        private synchronized boolean getAndSetCircuitBuilt() {
            boolean firstCircuit = !circuitBuilt;
            circuitBuilt = true;
            return firstCircuit;
        }

        private synchronized void enableNetwork(boolean enable) {
            networkEnabled = enable;
            if (!enable) circuitBuilt = false;
        }

        private synchronized boolean isConnected() {
            return networkEnabled && bootstrapped && circuitBuilt;
        }
    }
}
