### Intro

This is a Tor android example, using tor binary on android.

* Pre build android tor binary: 

    https://github.com/n8fr8/tor-android

* A Java library for controlling a Tor instance via its control port which is used in the Android apps:

    https://github.com/akwizgran/jtorctl 

### Workflow

* Download, copy and unzip `android tor binary` into `raw` folder:

    ```groovy
    [build.gradle]
    project.configurations {
        tor
    }
    dependencies {
        // https://bintray.com/briarproject/org.briarproject/tor-android
        tor 'org.briarproject:tor-android:0.3.4.8@zip'
    }
    project.afterEvaluate {
        project.copy {
            from configurations.tor.collect { zipTree(it) }
            into 'src/main/res/raw'
        }
    }
    ```

* Initialize some params

    `MainActivity.onCreate()` new one `torPlugin` object, and initialize some params in its constructor.

* Start run

    `MainActivity.onCreate()` call `new Thread(torPlugin).start()` to start torPlugin.

* Install assets

    In `TorPlugin.run()`, it first call `TorPlugin.installAssets()` to install or update the assets.
    The assets is `android tor binary` which has been downloaded into `raw` folder.
    The assets will be installed into `torDirectory` folder.

* Run `android tor binary` in `torDirectory` folder

    Using a process to run `android tor binary`, and gets the process's input stream when the binary start to run.
    ```android
    ProcessBuilder pb = new ProcessBuilder(torPath, "-f", configPath, OWNER, pid);
    torProcess = pb.start();
    Scanner stdout = new Scanner(torProcess.getInputStream());
    ```
    
* Interact with tor

    When `android tor binary` start running, we can use `jtorctl` lib to interact with it.
    ```groovy
    [build.gradle]
    dependencies {
        implementation 'org.briarproject:jtorctl:0.3'
    }
    ```

* Open a control socket and communicate with tor

    open a control socket and using this control socket to construct a controlConnection to communicate with tor:
    ```android
    controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
    controlConnection = new TorControlConnection(controlSocket);
    ```
    the default CONTROL_PORT of tor is 59051.

### How to build and run

* Build

    In root dir, using `./gradlew build` to build this apk, using `./gradlew installDebug` or `adb install xxx.apk` to install.

* Run

    After install, open this apk and wait, after a while, the logcat should print:`Tor has already bootstrapped`, this means Tor has already bootstrapped.


