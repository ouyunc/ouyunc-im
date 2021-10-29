
// 导入外部js
window.document.write("<script type = 'text/javascript' src='https://pv.sohu.com/cityjson?ie=utf-8'></script>");

// ================================定义常量类==========================
const SOCKET_CONSTANT = {
    // 客户端与服务端约定的心跳指令
    PING: "PING",

    SNOWFLAKE: {
        // 机器id
        WORKER_ID: 1n,
        // 数据中心id
        DATA_CENTER_ID: 1n,
        // 序列号
        SEQUENCE: 0n
    },
    PACKET: {
        // 协议魔数
        MAGIC: 102,
        // 协议头长度
        HEADER_LENGTH: 24,
        // 协议类型，目前只支持ws/wss，先固定写死
        PROTOCOL: 1,
        // 协议对应的版本，目前版本为1，先固定写死
        PROTOCOL_VERSION: 1,
        // 消息类型，这里需要与后台服务约定好
        MESSAGE_TYPE: {
            // 登录消息类型
            IM_LOGIN: 5,
            // im 心跳类型，最终会找到心跳处理类来处理
            IM_PING_PONG: 6,
            // im 外部客户端与服务端的应答ack处理消息类型
            IM_ACK: 10,
            // 好友添加请求
            IM_FRIEND_ADD_REQ: 11,
            // 私聊
            IM_P_CHAT: 7,
            // 同意添加好友
            IM_FRIEND_ADD_RESOLVE: 12,
            // 广播消息
            IM_BROADCAST: 9,
        },
        // 设备类型，需要与后端约定好
        DEVICE_TYPE: {
            // 其他
            OTHER:0,

            // 移动端其他系统
            M_OTHER:1,
            //移动端安卓系统
            M_ANDROID: 2,
            //移动端ios(苹果)系统
            M_IOS: 3,
            //移动端windows操作系统
            M_WINDOWS: 4,
            //移动端Palm webOS是一个嵌入式操作系统
            M_WEBOS: 5,
            //移动端MeeGo是一种基于Linux的自由及开放源代码的便携设备操作系统
            M_MEEGO: 6,

            // ipad 其他系统
            IPAD_OTHER:7,
            // ipad 安卓系统
            IPAD_ANDROID:8,
            // ipad 苹果系统
            IPAD_IOS:9,
            // ipad windows系统
            IPAD_WINDOWS:10,



            // 电脑端其他系统
            PC_OTHER:11,
            //电脑端苹果系统mac
            PC_MAC: 12,
            //电脑端windows系统
            PC_WINDOWS: 13,
            //电脑端linux系统
            PC_LINUX: 14,
            //电脑端华为鸿蒙系统
            PC_HARMONYOS: 15,
        },
        NETWORK_TYPE:{
            //其他网络
            OTHER:0,
            //wifi
            NET_WIFI:1,
            NET_2G:2,
            NET_3G:3,
            NET_4G:4,
            NET_5G:5,
        },
        // 消息体对称加密算法
        SYMMETRY_ENCRYPT: {
            // 没有加密
            NONE: 0,
            DES: 1,
            DES_3: 2,
            AES: 3,
            SM1: 4,
            SMS4: 5,
            PBE: 6,
            RC2: 7,
            RC4: 8,
            RC5: 9,
        },
        // 非对称加密
        ASYMMETRIC_ENCRYPT:{
            NONE:0,
            MD5:1,
            SM3:2,
        },
        // 消息体序列化算法
        SERIALIZER: {
            JDK: 1,
            JSON: 2,
            HESSIAN: 3,
            HESSIAN2: 4,
            KRYO: 5,
            PROTO_STUFF: 6,
            THRIFT: 7,
            FST: 8,
        },

        // 消息内容类型，
        MESSAGE_CONTENT_TYPE: {
            //字符串
            TEXT_CONTENT: 0,
            //登录消息内容
            LOGIN_CONTENT: 1,
            //聊天消息内容
            CHAT_CONTENT: 2,



        },
    },
}


// ==============================自定义相关工具方法==========================

// 将字符串ip 转int
function ip2Int(strIp) {
    let ipArr = strIp.split(".");
    let buffer = new ArrayBuffer(4);
    let dataView = new DataView(buffer);
    for (let i = 0; i < ipArr.length; i++) {
        dataView.setInt8(i, parseInt(ipArr[i]));
    }
    return (dataView.getInt8(3) & 0xFF) | ((dataView.getInt8(2) << 8) & 0xFF00) | ((dataView.getInt8(1) << 16) & 0xFF0000) | ((dataView.getInt8(0) << 24) & 0xFF000000);
}

// 将int 转ip
function int2Ip(intIp) {
    return (((intIp & 0xFF000000) >> 24) & 0xFF) + "." + ((intIp & 0xFF0000) >> 16) + "." + ((intIp & 0xFF00) >> 8) + "." + (intIp & 0xFF);
}
// 获取ip
function getIp() {
    return returnCitySN["cip"];
}

// 返回设备类型
function deviceType() {
    var uA = navigator.userAgent.toLowerCase();
    var ipad = uA.match(/ipad/i) == "ipad";
    var iphone = uA.match(/iphone os/i) == "iphone os";
    var midp = uA.match(/midp/i) == "midp";
    var uc7 = uA.match(/rv:1.2.3.4/i) == "rv:1.2.3.4";
    var uc = uA.match(/ucweb/i) == "ucweb";
    var android = uA.match(/android/i) == "android";
    var windowsce = uA.match(/windows ce/i) == "windows ce";
    var windowsmd = uA.match(/windows mobile/i) == "windows mobile";

    if (iphone || midp || uc7 || uc || android || windowsce || windowsmd) {
        // 移动端
        if (/android|adr/gi.test(uA)) {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.M_ANDROID;
        } else if (/\(i[^;]+;( U;)? CPU.+Mac OS X/gi.test(uA)) {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.M_IOS;
        } else if(/windows|adr/gi.test(uA)){
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.M_WINDOWS;
        } else {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.M_OTHER;
        }
    } else if (ipad) {
        // pad 端
        if (/android|adr/gi.test(uA)) {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.IPAD_ANDROID;
        } else if (/\(i[^;]+;( U;)? CPU.+Mac OS X/gi.test(uA)) {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.IPAD_IOS;
        } else if(/windows|adr/gi.test(uA)){
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.IPAD_WINDOWS;
        } else {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.IPAD_OTHER;
        }
    } else {
        // PC 端
        if (/windows|adr/gi.test(uA)) {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.PC_WINDOWS;
        } else if (/\(i[^;]+;( U;)? CPU.+Mac OS X/gi.test(uA)) {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.PC_MAC;
        } else {
            return SOCKET_CONSTANT.PACKET.DEVICE_TYPE.PC_OTHER;
        }

    }
}

// 返回网络类型 注意只在移动端有效
function networkType() {
    var ua = navigator.userAgent;
    var networkStr = ua.match(/NetType\/\w+/) ? ua.match(/NetType\/\w+/)[0] : 'NetType/other';
    networkStr = networkStr.toLowerCase().replace('nettype/', '');
    var networkType;
    switch (networkStr) {
        case 'wifi':
            networkType = 'wifi';
            break;
        case '5g':
            networkType = SOCKET_CONSTANT.PACKET.NETWORK_TYPE.NET_5G;
            break;
        case '4g':
            networkType = SOCKET_CONSTANT.PACKET.NETWORK_TYPE.NET_4G;
            break;
        case '3g':
            networkType = SOCKET_CONSTANT.PACKET.NETWORK_TYPE.NET_3G;
            break;
        case '3gnet':
            networkType = SOCKET_CONSTANT.PACKET.NETWORK_TYPE.NET_3G;
            break;
        case '2g':
            networkType = SOCKET_CONSTANT.PACKET.NETWORK_TYPE.NET_2G;
            break;
        default:
            networkType = SOCKET_CONSTANT.PACKET.NETWORK_TYPE.OTHER;
    }
    return networkType;
}

// 生成签名
function signature(appKey, appSecret, identity, timestamp) {
    return md5(appKey + "&" + identity + "&" + timestamp + "_" + appSecret);
}