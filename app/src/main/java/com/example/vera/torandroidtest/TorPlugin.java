package com.example.vera.torandroidtest;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.vera.torandroidtest.utils.IoUtils;

import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
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
    public static final String TAG = "TorAndroidTest";
    private String architecture = "arm";
    private static final String OWNER = "__OwningControllerProcess";
    private static final int COOKIE_TIMEOUT_MS = 3000; // Milliseconds
    private static final int COOKIE_POLLING_INTERVAL_MS = 200; // Milliseconds
    private static final int CONNECT_TO_PROXY_TIMEOUT = 5000; // Milliseconds
    private static final int EXTRA_SOCKET_TIMEOUT = 30000; // Milliseconds
    // control port in torrc: ControlPort 59051
    private int CONTROL_PORT = 59051;
    private static final String[] EVENTS = {
            "CIRC", "ORCONN", "HS_DESC", "NOTICE", "WARN", "ERR"
    };
    // hidden service's virtual tcp port(remote client call this port to connect to server's hidden service)
    private static int HIDDENSERVICE_VIRTUAL_PORT = 80;
    // the target port for the given virtual port(server's hidden service open this port to listen for client's connect)
    private static int HIDDENSERVICE_TARGET_PORT = 8080;
    // tor proxy port in torrc: SocksPort 59050
    private static int SOCKS_PORT = 59050;
    private static final String HS_ADDRESS_STRING = "onionAddress";
    private static final String HS_PRIVKEY_STRING = "onionPrivKey";
    private String hiddenServicePrivateKey = null;
    private String hiddenServiceAddress = null;
    // Activity context: getApplicationContext()
    Context appContext;
    // tor files in android device
    private File torDirectory;
    private File torFile;
    private File configFile;
    private File doneFile; // used for checking whether the assets is up to date
    private File geoIpFile;
    private File cookieFile;
    // socket of tor
    private volatile ServerSocket socket = null;
    private volatile Socket controlSocket = null;
    private volatile TorControlConnection controlConnection = null;

    private ServerSocket serverSocket;

    public TorPlugin(Context appContext, File torDirectory){
        this.appContext = appContext;
        this.torDirectory = torDirectory;
        this.torFile = new File(torDirectory, "tor");
        this.configFile = new File(torDirectory, "torrc");
        this.doneFile = new File(torDirectory, "done");
        this.geoIpFile = new File(torDirectory, "geoip");
        this.cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
    }

    @Override
    public void run() {
        Log.i(TAG, "running start");
        // check directory before install assets
        if (!torDirectory.exists()) {
            if (!torDirectory.mkdirs()) {
                Log.i(TAG,"Could not create Tor directory.");
            }
        }
        // Install or update the assets if necessary
        if (!assetsAreUpToDate())
            installAssets();

        // delete old cookieFile
        if (cookieFile.exists() && !cookieFile.delete())
            Log.w(TAG, "Old auth cookie not deleted");

        // Start a new Tor process
        startTorProcess();

        // open tor control connection and wait for tor to get bootstrapped
        openControlConnectionAndWaitForBootstrapped();

        // act as a server to publish a hidden service, and then act as a client to connect to it's hidden service
//        testServerAndClient();

        // communicate with a locally running tor process using control connection
        ControlPortOperation.getConf(controlConnection, "SocksPort");
        ControlPortOperation.getInfo(controlConnection, "version");
        ControlPortOperation.setEvents(controlConnection, this, Arrays.asList(EVENTS));
    }

    private void installAssets(){
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

    private void startTorProcess(){
        // Start a new Tor process
        Log.i(TAG,"Starting Tor");
        String torPath = torFile.getAbsolutePath();
        String configPath = configFile.getAbsolutePath();
        String pid = String.valueOf(getProcessId());
        Process torProcess;
        ProcessBuilder pb =
                new ProcessBuilder(torPath, "-f", configPath, OWNER, pid);
        Log.i(TAG, "torPath: " + torPath + " configPath: " + configPath + " OWNER: " + OWNER + " pid: " + pid);
        // set process builder's home environment and working directory
        Map<String, String> env = pb.environment();
        env.put("HOME", torDirectory.getAbsolutePath());
        pb.directory(torDirectory);
        try {
            // run tor android binary
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
            Log.e(TAG, "Exception at starting tor-android-binary: " + e.getMessage());
        }
    }

    private void openControlConnectionAndWaitForBootstrapped(){
        try {
            // Open a control connection and authenticate using the cookie file
            controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
            controlConnection = new TorControlConnection(controlSocket);
            controlConnection.authenticate(IoUtils.read(cookieFile));
            // Tell Tor to exit when the control connection is closed
            controlConnection.takeOwnership();
            controlConnection.resetConf(Collections.singletonList(OWNER));
            // Register to receive events from the Tor process
            controlConnection.setEventHandler(this);
            controlConnection.setEvents(Arrays.asList(EVENTS));
            // Check whether Tor has already bootstrapped
            String phase = "";
            while(phase == null || !phase.contains("PROGRESS=100")) {
                phase = controlConnection.getInfo("status/bootstrap-phase");
                Log.i(TAG, "status/bootstrap-phase: " + phase);
                if (phase != null && phase.contains("PROGRESS=100")) {
                    Log.i(TAG, "Tor has already bootstrapped");
                }
                try {
                    Thread.sleep(3*1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Open control connection error: " + e.getMessage());
        }
    }

    private void testServerAndClient(){

        bindToLocalPort();
        new Thread(new Runnable() {
            public void run() {
                accessClientConnect();
            }
        }).start();
        publishHiddenService();

        try {
            Thread.sleep(50*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        new Thread(new Runnable() {
            public void run() {
                connectToRemote();
            }
        }).start();
    }


    private void publishHiddenService() {
        Map<Integer, String> portLines =
                Collections.singletonMap(HIDDENSERVICE_VIRTUAL_PORT, "127.0.0.1:" + HIDDENSERVICE_TARGET_PORT);
        Map<String, String> response;
        try {
            if (hiddenServicePrivateKey == null) {
                response = controlConnection.addOnion(portLines);
            } else {
                response = controlConnection.addOnion(hiddenServicePrivateKey, portLines);
            }
            if (!response.containsKey(HS_ADDRESS_STRING)) {
                Log.w(TAG,"Tor did not return a hidden service address");
                return;
            }
            if (hiddenServicePrivateKey == null && !response.containsKey(HS_PRIVKEY_STRING)) {
                Log.w(TAG,"Tor did not return a private key");
                return;
            }
            hiddenServiceAddress = response.get(HS_ADDRESS_STRING) + ".onion";
            hiddenServicePrivateKey = response.get(HS_PRIVKEY_STRING);
            Log.i(TAG, "hiddenServiceAddress: " + hiddenServiceAddress);
            Log.i(TAG, "hiddenServicePrivateKey: " + hiddenServicePrivateKey);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void bindToLocalPort(){
        ServerSocket ss = null;
        try {
            ss = new ServerSocket();
            Log.i(TAG, "[SERVER] start server");
            ss.bind(new InetSocketAddress("127.0.0.1", HIDDENSERVICE_TARGET_PORT));
        } catch (IOException e) {
            e.printStackTrace();
        }
        serverSocket = ss;
    }

    private void accessClientConnect(){
        Socket clientSocket = null;
        PrintWriter out = null;
        while(true) {
            try {
                if (serverSocket != null) {
                    clientSocket = serverSocket.accept();
                    Log.i(TAG, "[SERVER] receive connect from client");
                    out = new PrintWriter(clientSocket.getOutputStream(), true);
                    String message = "Hello client";
                    out.println(message);
                    Log.i(TAG, "[SERVER] send a message to client: " + message);
                }
            }catch (IOException e){
                e.printStackTrace();
            }finally {
                //等待客户端接受完消息
                try {
                    Thread.sleep(2*1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.i(TAG, "[SERVER] close client socket");
                tryToClose(out);
                tryToClose(clientSocket);
            }
        }
    }

    private void connectToRemote(){
        SocksSocket socks5Socket = null;
        BufferedReader in = null;
        while(true) {
            try {
                Log.i(TAG, "[CLIENT] start client");
                InetSocketAddress proxy = new InetSocketAddress("127.0.0.1",
                        SOCKS_PORT);
                socks5Socket = new SocksSocket(proxy, CONNECT_TO_PROXY_TIMEOUT, EXTRA_SOCKET_TIMEOUT);
                socks5Socket.connect(InetSocketAddress.createUnresolved(hiddenServiceAddress, HIDDENSERVICE_VIRTUAL_PORT));
                in = new BufferedReader(new InputStreamReader(socks5Socket.getInputStream()));
                Log.i(TAG, "[CLIENT] receive reply from server: " + in.readLine());
            } catch (IOException e) {
                e.printStackTrace();
                Log.i(TAG, "[CLIENT] Could not connect to " + hiddenServiceAddress);
            } finally {
                tryToClose(socks5Socket);
                Log.i(TAG, "[CLIENT] close client");
                try {
                    Thread.sleep(10*1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
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


    private InputStream getAndroidResourceInputStream(String name, String extension) {
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
            e.printStackTrace();
        }
    }

    private void tryToClose(@Nullable Socket s) {
        try {
            if (s != null) s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean assetsAreUpToDate() {
        return doneFile.lastModified() > getLastUpdateTime();
    }

    private long getLastUpdateTime() {
        try {
            PackageManager pm = appContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), 0);
            return pi.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private int getProcessId() {
        return android.os.Process.myPid();
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
    public void circuitStatus(String status, String circID, String path) {
        Log.d(TAG, "Circuit "+circID+" is now "+status+" (path="+path+")");
    }

    @Override
    public void message(String severity, String msg) {
        Log.d(TAG, severity + " " + msg);
    }

    @Override
    public void orConnStatus(String status, String orName) {
        Log.d(TAG, "OR connection " + status + " " + orName);
        if (status.equals("CLOSED") || status.equals("FAILED")) {
            // Check whether we've lost connectivity
            Log.i(TAG, "we lost connectivity");
            // this should have an updateConn
        }
    }

    @Override
    public void unrecognized(String type, String msg) {
        if (type.equals("HS_DESC") && msg.startsWith("UPLOADED"))
            Log.d(TAG, "Descriptor uploaded");
    }


}
