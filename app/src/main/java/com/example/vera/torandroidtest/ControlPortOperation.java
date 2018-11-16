package com.example.vera.torandroidtest;

import com.example.vera.torandroidtest.utils.IoUtils;

import net.freehaven.tor.control.ConfigEntry;
import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by vera on 18-11-13.
 */

public class ControlPortOperation {

    /**
     * SETCONF spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n261
     *
     * 更改配置参数的值，默认配置参数的值由配置文件（torrc文件）指定
     */
    public static void setConf(TorControlConnection torControlConnection, String key, String value){
        try {
            torControlConnection.setConf(key, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * RECONF spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n296
     *
     * 将配置参数的值重置为默认值
     */
    public static void resetConf(TorControlConnection torControlConnection, Collection<String> keys){
        try {
            torControlConnection.resetConf(keys);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * GETCONF spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n307
     *
     * 查询配置参数的值
     */
    public static void getConf(TorControlConnection torControlConnection, String key){
        try {
            List<ConfigEntry> configEntryList = torControlConnection.getConf(key);
            for(ConfigEntry configEntry : configEntryList){
                System.out.println(configEntry.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * SETEVENTS spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n337
     *
     * Each element of <b>events</b> is one of the following Strings:
     * ["CIRC" | "STREAM" | "ORCONN" | "BW" | "DEBUG" |
     *  "INFO" | "NOTICE" | "WARN" | "ERR" | "NEWDESC" | "ADDRMAP"] .
     *
     *  请求服务端在设置的事件发生时向客户端发送通知，所有未在SETEVENTS中设定的事件都不会收到服务端的通知
     */
    public static void setEvents(TorControlConnection torControlConnection, EventHandler eventHandler,List<String> events){
        //Request that the server inform the client about interesting events
        try {
            torControlConnection.setEventHandler(eventHandler);
            torControlConnection.setEvents(events);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * AUTHENTICATE spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n362
     *
     * By default, the current Tor implementation trusts all local users, and
     * the controller can authenticate itself by calling authenticate(new byte[0]).
     *
     * If the 'CookieAuthentication' option is true, Tor writes a "magic cookie"
     * file named "control_auth_cookie" into its data directory.  To authenticate,
     * the controller must send the contents of this file in <b>auth</b>.
     *
     * If the 'HashedControlPassword' option is set, <b>auth</b> must contain the salted
     * hash of a secret password.  The salted hash is computed according to the
     * S2K algorithm in RFC 2440 (OpenPGP), and prefixed with the s2k specifier.
     * This is then encoded in hexadecimal, prefixed by the indicator sequence
     * "16:".
     *
     * You can generate the salt of a password by calling
     *       'tor --hash-password <password>'
     * or by using the provided PasswordDigest class.
     * To authenticate under this scheme, the controller sends Tor the original
     * secret that was used to generate the password.
     *
     * 向服务端进行认证
     */
    public static void authenticate(TorControlConnection torControlConnection, File cookieFile){
        try {
            torControlConnection.authenticate(IoUtils.read(cookieFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *　SAVECONF spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n403
     *
     * 指示服务端将其配置选项写入torrc文件中
     */
    public static void saveConf(TorControlConnection torControlConnection){
        try {
            torControlConnection.saveConf();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * SIGNAL spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n423
     *
     * Signal = "RELOAD" / "SHUTDOWN" / "DUMP" / "DEBUG" / "HALT" /
     *          "HUP" / "INT" / "USR1" / "USR2" / "TERM" / "NEWNYM" /
     *          "CLEARDNSCACHE" / "HEARTBEAT"
     *
     * 向服务端发送控制信号
     */
    public static void signal(TorControlConnection torControlConnection, String signal){
        try {
            torControlConnection.signal(signal);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * MAPADDRESS spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n464
     *
     * 告知服务端，当未来有socket要连接到原始地址时，服务端需要使用指定地址替换原始地址进行连接
     */
    public static void mapAddress(TorControlConnection torControlConnection, String fromAddr, String toAddr){
        try {
            String newAddr = torControlConnection.mapAddress(fromAddr, toAddr);
            System.out.println(newAddr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * GETINFO spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n519
     *
     * 从服务端获取数据信息（和getConf不同，getInfo获取非配置信息）
     */
    public static void getInfo(TorControlConnection torControlConnection, String key){
        // GETINFO spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n519
        try {
            String info = torControlConnection.getInfo(key);
            System.out.println(info);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * EXTENDCIRCUIT spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1131
     *
     * An extendCircuit request takes one of two forms: either the <b>circID</b> is zero, in
     * which case it is a request for the server to build a new circuit according
     * to the specified path, or the <b>circID</b> is nonzero, in which case it is a
     * request for the server to extend an existing circuit with that ID according
     * to the specified <b>path</b>.
     *
     * If successful, returns the Circuit ID of the (maybe newly created) circuit.
     *
     * 新建链路或者使用指定路径扩展链路
     */
    public static void extendCircuit(TorControlConnection torControlConnection, String circID, String path){
        try {
            String circuitId = torControlConnection.extendCircuit(circID, path);
            System.out.println(circuitId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ATTACHSTREAM spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1176
     *
     * By default, Tor automatically attaches streams to
     * circuits itself, unless the configuration variable
     * "__LeaveStreamsUnattached" is set to "1".  Attempting to attach streams
     * via TC when "__LeaveStreamsUnattached" is false may cause a race between
     * Tor and the controller, as both attempt to attach streams to circuits.
     *
     * 告知服务端将指定的数据流关联到指定的链路中
     */
    public static void attachStream(TorControlConnection torControlConnection, String streamID, String circID){
        try {
            torControlConnection.attachStream(streamID, circID);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * POSTDESCRIPTOR spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1215
     *
     * The descriptor, when parsed, must contain a number of well-specified
     * fields, including fields for its nickname and identity.
     *
     * 通告服务端一个新的descriptor
     */
    public static void postDescriptor(TorControlConnection torControlConnection, String desc){
        try {
            String reply = torControlConnection.postDescriptor(desc);
            System.out.println(reply);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * REDIRECTSTREAM spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1239
     *
     * To be sure that the modified address will be used, this event must be sent
     * after a new stream event is received, and before attaching this stream to
     * a circuit.
     *
     * 告知服务端更换指定的数据流的出口节点
     */
    public static void redirectStream(TorControlConnection torControlConnection, String streamID, String address){
        try {
            torControlConnection.redirectStream(streamID, address);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * CLOSESTREAM spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1254
     *
     * 告知服务端关闭指定数据流
     */
    public static void closeStream(TorControlConnection torControlConnection, String streamID, byte reason){
        try {
            torControlConnection.closeStream(streamID, reason);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * CLOSECIRCUIT spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1268
     *
     * 告知服务器关闭制定链路
     */
    public static void closeCircuit(TorControlConnection torControlConnection, String circID, boolean ifUnused){
        try {
            torControlConnection.closeCircuit(circID, ifUnused);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * TAKEOWNERSHIP spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1428
     *
     * This command is intended to be used with the
     * __OwningControllerProcess configuration option.  A controller that
     * starts a Tor process which the user cannot easily control or stop
     * should 'own' that Tor process:
     *
     * When starting Tor, the controller should specify its PID in an
     * __OwningControllerProcess on Tor's command line.  This will
     * cause Tor to poll for the existence of a process with that PID,
     * and exit if it does not find such a process.  (This is not a
     * completely reliable way to detect whether the 'owning
     * controller' is still running, but it should work well enough in
     * most cases.)
     *
     * Once the controller has connected to Tor's control port, it
     * should send the TAKEOWNERSHIP command along its control
     * connection.  At this point, *both* the TAKEOWNERSHIP command and
     * the __OwningControllerProcess option are in effect: Tor will
     * exit when the control connection ends *and* Tor will exit if it
     * detects that there is no process with the PID specified in the
     * __OwningControllerProcess option.
     *
     * After the controller has sent the TAKEOWNERSHIP command, it
     * should send "RESETCONF __OwningControllerProcess" along its
     * control connection.  This will cause Tor to stop polling for the
     * existence of a process with its owning controller's PID; Tor
     * will still exit when the control connection ends.
     *
     * 告知服务端在当前control connection关闭时，关闭服务端
     */
    public static void takeOwnership(TorControlConnection torControlConnection){
        try {
            torControlConnection.takeOwnership();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ADDONION spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1568
     *
     * 告知服务端创建一个hidden service V2
     */
    public static void addOnion(TorControlConnection torControlConnection, Map<Integer,String> portLines){
        try {
            torControlConnection.addOnion(portLines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ADDONION spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1568
     *
     * 告知服务端创建一个hidden service V3
     */
    public static void addOnionV3(TorControlConnection torControlConnection, Map<Integer,String> portLines){
        try {
            torControlConnection.addOnionV3(portLines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * DELONION spec: https://gitweb.torproject.org/torspec.git/tree/control-spec.txt#n1732
     *
     * 告知服务器移除之前使用ADDONION命令建立的hidden service
     * 只允许移除当前control connection建立的或者不属于任何control connection的hidden service
     */
    public static void delOnion(TorControlConnection torControlConnection, String hostname){
        try {
            torControlConnection.delOnion(hostname);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
