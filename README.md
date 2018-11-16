## 简介
这是一个在android上使用tor进行网络通信的demo。
实现了：
1. 在app中启动一个进程运行`tor-android`二进制文件
2. 通过`control port`和运行的tor进程进行交互
3. 启动两个线程分别模拟hidden service与client，进行通信

## 使用的预编译文件及第三方库简介
* 预编译好的`tor-android`二进制文件：
  https://github.com/n8fr8/tor-android
  
* 通过tor提供的`control port`与tor交互的java库`jtorctl`：
  https://github.com/akwizgran/jtorctl
  
* tor的`control port`的协议规范：
  https://gitweb.torproject.org/torspec.git/tree/control-spec.txt

## 工作流程简介
### 下载`tor-android`，并解压到项目的`raw`文件夹中
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

### 文件装载
在`installAssets()`中，将`tor-android`及`geoip`,`torrc`等文件载入到手机中，并赋予`tor-android`文件执行权限

### 启动tor进程
在`startTorProcess()`中，启动一个进程运行`tor-android`二进制文件，并使用`__OwningControllerProcess`参数指定tor监控进程号为pid的进程（pid在这里是当前程序的进程号），如果该进程消失了，tor会自动停止。
然后等待生成用来认证的`authenticate cookie file`。

### 连接到tor的控制端口(`control port`)
本demo中将`jtorctl`第三方库构建成了一个模块添加进来，并新增了`addOnionV3`接口，用来生成`hidden service v3`。
在`openControlConnectionAndWaitForBootstrapped()`中，新建一个socket，连接到tor提供的控制端口(`control port`)，并使用第三方库`jtorctl`建立一个`controlConnection`对象与tor进行交互控制。
交互前需要使用刚生成的`authenticate cookie file`通过`controlConnection`向tor进行认证。
需要使用`takeOwnership()`接口告知tor监控该`control port上`的连接，当连接关闭后，tor就停止。协议文档中对`TAKEOWNERSHIP`命令的介绍如下：
> This command is intended to be used with the
 __OwningControllerProcess configuration option.  A controller that
 starts a Tor process which the user cannot easily control or stop
 should 'own' that Tor process:  
>   * When starting Tor, the controller should specify its PID in an
     __OwningControllerProcess on Tor's command line.  This will
     cause Tor to poll for the existence of a process with that PID,
     and exit if it does not find such a process.  (This is not a
     completely reliable way to detect whether the 'owning
     controller' is still running, but it should work well enough in
     most cases.)
>   * Once the controller has connected to Tor's control port, it
     should send the TAKEOWNERSHIP command along its control
     connection.  At this point, *both* the TAKEOWNERSHIP command and
     the __OwningControllerProcess option are in effect: Tor will
     exit when the control connection ends *and* Tor will exit if it
     detects that there is no process with the PID specified in the
     __OwningControllerProcess option.
>   * After the controller has sent the TAKEOWNERSHIP command, it
     should send "RESETCONF __OwningControllerProcess" along its
     control connection.  This will cause Tor to stop polling for the
     existence of a process with its owning controller's PID; Tor
     will still exit when the control connection ends.
         
该方法中还使用了`setEvents()`接口，请求服务端在设置的事件发生时向客户端发送通知。
并且使用`getInfo()`接口，从服务端获取`status/bootstrap-phase`信息，如果返回状态中包含“PROGRESS=100”，表示tor的circuit已经成功建立，可以进行通信了。

### 启动两个线程分别模拟hidden service与client，进行通信
* 新建两个线程模拟hidden service和client，进行通信
该过程在`testServerAndClient()`中。

* 服务端绑定端口
在`bindToLocalPort()`中，服务端新建一个ServerSocket，并打开一个端口（该端口为target port）提供服务。

* 发布hidden service



















