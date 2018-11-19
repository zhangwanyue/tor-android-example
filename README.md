# 简介

这是一个在android上使用tor进行网络通信的demo。

实现了：

1. 在app中启动一个进程运行`tor-android`二进制文件

2. 通过`control port`和运行的tor进程进行交互

3. 启动两个线程分别模拟hidden service与client，进行通信

# 使用的预编译文件及第三方库简介

* 预编译好的`tor-android`二进制文件：
  https://github.com/n8fr8/tor-android
  
* 通过tor提供的`control port`与tor交互的java库`jtorctl`：
  https://github.com/akwizgran/jtorctl
  
* tor的`control port`的协议规范：
  https://gitweb.torproject.org/torspec.git/tree/control-spec.txt

# 工作流程简介

## 下载`tor-android`，并解压到项目的`raw`文件夹中

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

## 文件装载

在`installAssets()`中，将`tor-android`及`geoip`,`torrc`等文件载入到手机中，并赋予`tor-android`文件执行权限

## <span id="start-tor">启动tor进程</span>

在`startTorProcess()`中，启动一个进程运行`tor-android`二进制文件，并使用`__OwningControllerProcess`参数指定tor监控进程号为pid的进程（pid在这里是当前程序的进程号），如果该进程消失了，tor会自动停止。

然后等待生成用来认证的`authenticate cookie file`。

## <span id="connect-to-control-port">连接到tor的控制端口(`control port`)</span>

本demo中将`jtorctl`第三方库构建成了一个模块添加进来，并新增了`addOnionV3`接口，用来生成`hidden service v3`。

在`openControlConnectionAndWaitForBootstrapped()`中，新建一个socket，连接到tor提供的控制端口(`control port`)，并使用第三方库`jtorctl`建立一个`controlConnection`对象与tor进行交互控制。

交互前需要使用刚生成的`authenticate cookie file`通过`controlConnection`向tor进行认证。

需要使用`takeOwnership()`接口告知tor监控该`control port`上的连接，当连接关闭后，tor就停止。协议文档中对`TAKEOWNERSHIP`命令的介绍如下：

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

## 启动两个线程分别模拟hidden service与client，进行通信

新建两个线程模拟`hidden service`和`client`，进行通信。

该过程在`testServerAndClient()`中。

### 服务端

#### 服务端绑定端口

在`bindToLocalPort()`中，服务端新建一个ServerSocket，并绑定到一个端口（该端口为target port）提供服务。

#### 发布hidden service

在`publishHiddenService()`中，服务端通过controlConnection向tor发送ADD_ONION命令，映射本地的target port到虚拟端口virtual port。

客户端将通过服务端的hidden service address和virtual port访问服务端，实际访问的是服务端的target port的服务。

这里设定的virtual port为80。

Tor通过controlConnection向服务端返回应答，应答中包含生成的hidden service address和private key等信息。

至此，服务端hidden service已经建立完成。

#### 服务端等待客户端的连接

在`accessClientConnect()`中，服务端等待客户端连接，并向客户端发送一条信息："Hello client"

### 客户端

#### 配置tor proxy代理

在建立客户端socket的时候，需要使用到tor提供的代理端口（socks port），才能访问tor网络。该代理（该代理是`socks5`代理）端口在torrc文件中会进行配置：

```
/**
 * file: torrc
 */
SocksPort 59050
```

并且在代码中使用tor提供的代理:

```java
InetSocketAddress proxy = new InetSocketAddress("127.0.0.1", SOCKS_PORT);
```

#### 连接到服务端

使用代理连接到服务端，并读取服务端发来的信息。

* 关于远程解析.onion域名
 
因为服务端的域名为xxx.onion，不是可以本地解析的地址，需要tor代理进行远程解析。

按理来说，在java中，进行远程解析应该使用`InetSocketAddress.createUnresolved`构造需要远程解析的域名地址，放入`socket.connect`方法中：

```java
Socket socket  = new Socket(proxy);
socket.connect(InetSocketAddress.createUnresolved(hiddenServiceAddress, HIDDENSERVICE_VIRTUAL_PORT));
```

但是该方法在android中会遇到`java.net.UnknownHostException: Host is unresolved`的异常。

关于该问题的一个参考：[android-how-to-let-tor-service-to-perform-the-dns-resolution-using-socket](https://stackoverflow.com/questions/39308705/android-how-to-let-tor-service-to-perform-the-dns-resolution-using-socket)
> Android will probably perform DNS resolution via DNS server specified in network configuration and the resolution of onion address will not work.

本代码中使用`SocksSocket`类重写了`Socket`类的`connect`方法，在客户端发送连接请求给服务端时，直接将域名直接封装到`socks5`数据包中，发送给代理。

并处理好`connect`方法中的客户端服务端的认证协商过程，关于该过程的详解请见`socks5`协议：[rfc1928](https://www.ietf.org/rfc/rfc1928.txt)

```java
socks5Socket = new SocksSocket(proxy, CONNECT_TO_PROXY_TIMEOUT, EXTRA_SOCKET_TIMEOUT);
socks5Socket.connect(InetSocketAddress.createUnresolved(hiddenServiceAddress, HIDDENSERVICE_VIRTUAL_PORT));
```

### 使用tor的控制端口(`control port`)使app与tor process进行交互

#### 初始化`controlConnection`

关于初始化`controlConnection`的过程在上文的[启动tor进程](#start-tor)和[连接到tor的控制端口](#connect-to-control-port)中已经有详细的介绍了。

#### 使用`jtorctl`类库中的方法对tor process进行交互

在初始化过程完成之后，就可以使用`jtorctl`类库中的方法对tor process进行交互。

关于`jtorctl`类库中相关方法的使用和说明已经封装在`ControlPortOperation`类中，可以直接调用该类中的静态方法。

比如，在`TorPlugin.run()`中调用了如下代码：

```java
ControlPortOperation.getConf(controlConnection, "SocksPort");//查询配置参数SocksPort的值
ControlPortOperation.getInfo(controlConnection, "version");//查询tor的版本号信息
ControlPortOperation.setEvents(controlConnection, this, Arrays.asList(EVENTS));//请求服务端在设置的事件发生时向客户端发送通知
```



















