/**
 * js 雪花算法
 * @type {Snowflake}
 */
const Snowflake = /** @class */ (function () {
    function Snowflake(_workerId, _dataCenterId, _sequence) {
        this.twepoch = 1288834974657n;
        //this.twepoch = 0n;
        this.workerIdBits = 5n;
        this.dataCenterIdBits = 5n;
        this.maxWrokerId = -1n ^ (-1n << this.workerIdBits); // 值为：31
        this.maxDataCenterId = -1n ^ (-1n << this.dataCenterIdBits); // 值为：31
        this.sequenceBits = 12n;
        this.workerIdShift = this.sequenceBits; // 值为：12
        this.dataCenterIdShift = this.sequenceBits + this.workerIdBits; // 值为：17
        this.timestampLeftShift = this.sequenceBits + this.workerIdBits + this.dataCenterIdBits; // 值为：22
        this.sequenceMask = -1n ^ (-1n << this.sequenceBits); // 值为：4095
        this.lastTimestamp = -1n;
        //设置默认值,从环境变量取
        this.workerId = 1n;
        this.dataCenterId = 1n;
        this.sequence = 0n;
        if (this.workerId > this.maxWrokerId || this.workerId < 0) {
            throw new Error('_workerId must max than 0 and small than maxWrokerId-[' + this.maxWrokerId + ']');
        }
        if (this.dataCenterId > this.maxDataCenterId || this.dataCenterId < 0) {
            throw new Error('_dataCenterId must max than 0 and small than maxDataCenterId-[' + this.maxDataCenterId + ']');
        }

        this.workerId = BigInt(_workerId);
        this.dataCenterId = BigInt(_dataCenterId);
        this.sequence = BigInt(_sequence);
    }

    Snowflake.prototype.tilNextMillis = function (lastTimestamp) {
        let timestamp = this.timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = this.timeGen();
        }
        return BigInt(timestamp);
    };
    Snowflake.prototype.timeGen = function () {
        return BigInt(Date.now());
    };
    Snowflake.prototype.nextId = function () {
        let timestamp = this.timeGen();
        if (timestamp < this.lastTimestamp) {
            throw new Error('Clock moved backwards. Refusing to generate id for ' +
                (this.lastTimestamp - timestamp));
        }
        if (this.lastTimestamp === timestamp) {
            this.sequence = (this.sequence + 1n) & this.sequenceMask;
            if (this.sequence === 0n) {
                timestamp = this.tilNextMillis(this.lastTimestamp);
            }
        } else {
            this.sequence = 0n;
        }
        this.lastTimestamp = timestamp;
        return ((timestamp - this.twepoch) << this.timestampLeftShift) |
            (this.dataCenterId << this.dataCenterIdShift) |
            (this.workerId << this.workerIdShift) |
            this.sequence;
    };
    return Snowflake;
}());

/**
 * 封装通用websocket
 */
const Socket = /** @class */ (function (Snowflake) {
	// =========================================定义全局变量===========================================

    /**
     * webSocket 对象
     */
    let webSocket = null;
    /**
     * socket 对象
     */
    let socket = null;
    /**
     * websocket url
     */
    let url = '';

    /**
     * 客户端登录唯一标识
     */
    let loginIdentity = '';

    /**
     * 默认配置参数,注意这里的超时等参数要与服务端相匹配
     */
    let defaultConfig = {
        // 是否开启心跳，默认true，开启心跳
        heartbeatEnable: true,
        // 心跳最大等待次数（等待3个心跳时间，如果没有收到消息，则关闭客户端）
        heartbeartMaxWait: 3,
        // 心跳间隔时间10秒（并不一定10秒内会发一个心跳，如果10秒内都消息接收过来，则不会进行发送）
        heartbeartIntervalTime: 10000,
        // 心跳读超时时间（客户端发送心跳后，最大等待服务器回复是5秒，超过5秒则进行计数）
        heartbeartReadIdleTimeout: 5000,
        // 是否开启重连，默认true，开启重连
        reconnectEnable: true,
        // 如果开启重连，重连的次数，默认-1，一直重连，请设置大于0的整数
        reconnectTimes: -1,
        // 重连延迟时间，默认 2s
        reconnectDelayTime: 2000
    };




    // =========================================定义构造方法===========================================
    /**
     * socket 的构造方法
     * @param webSocketUrl websocket url连接地址如：ws://
     * @param config websocket配置信息
     * @constructor
     */
    function Socket(webSocketUrl, config) {
        console.log("欢迎使用偶云客-IM v3.0.1 js客户端sdk.")
        window.WebSocket = window.WebSocket || window.MozWebSocket;
        if (!window.WebSocket || !window.WebSocket.prototype.send) {
            throw "当前浏览器或版本不支持WebSocket！请更换其他浏览器或版本";
            return;
        }
        if (!webSocketUrl) {
            throw "非法参数 '"+ url +"',请正确设置url,如: ws://127.0.0.1:8000/";
            return;
        }
        // 初始化全局变量
        url = webSocketUrl;
        defaultConfig = config || defaultConfig
        socket = this;
        // 将Socket 实例传递过去
        init();
    }

    // =========================================定义方法==============================================
    // 初始化
    function init() {
        // 初始化
        // 创建原生websocket
        webSocket = new WebSocket(url);


        webSocket.onopen = function (e) {
            if (socket.onopen instanceof Function){
                socket.onopen(e);
            }else {
                console.log('ws 连接打开了');
            }
        };


        webSocket.onmessage = function (e) {
            // 如果开启心跳，则进行重置心跳
            if (defaultConfig.heartbeatEnable) {
                // 1当收到是登录成功的回复信息或其他消息后，进行清除延时数据，重新计时发送心跳
                heartCheck.reset().start();
            }
            // 处理好的消息在传递出去
            if (socket.onmessage instanceof Function){
                parseMessage(e.data).then(function (packet) {
                    // messageId是long类型，messageType是byte类型，message 是proto.com.ouyunc.Message 类型

                    socket.onmessage({
                        messageId: packet.messageId,
                        messageType: packet.messageType,
                        message:{
                            // string 类型
                            "from": packet.message.getFrom(),
                            // string 类型
                            "to": packet.message.getTo(),
                            // byte 类型
                            "contentType": packet.message.getContentType(),
                            // json string 类型
                            "content": packet.message.getContent(),
                            // 消息发送时间， long 类型
                            "createTime": packet.message.getCreateTime()
                        }
                    });
                })
            }else {
                console.log('接收到消息了');
            }
        };


        webSocket.onclose = function (e) {
            if (socket.onclose instanceof Function){
                socket.onclose(e);
            }else {
                console.log('ws 连接关闭了');
            }
            // 判断是否开启重连
            if (defaultConfig.reconnectEnable) {
                if (defaultConfig.heartbeatEnable) {
                    // 重置心跳检测
                    heartCheck.reset();
                }
                // 重连
                if (defaultConfig.reconnectTimes === -1 || reconnect.currentReconnectTimes < defaultConfig.reconnectTimes) {
                    reconnect.start();
                }
            }
        };


        webSocket.onerror = function (e) {
            if (socket.onerror instanceof Function){
                socket.onerror(e);
            }else {
                console.log('ws 连接异常了');
            }
            if (defaultConfig.reconnectEnable) {
                if (defaultConfig.heartbeatEnable) {
                    // 重置心跳检测
                    heartCheck.reset();
                }
                // 重连
                if (defaultConfig.reconnectTimes === -1 || reconnect.currentReconnectTimes < defaultConfig.reconnectTimes) {
                    reconnect.start();
                }
            }

        }
    };

    /**
     * 定义重连
      */
    let reconnect = {

        /**
         * 避免ws重复连接
         */
        lockReconnect: false,

        /**
         * 重连延迟返回的标识
         */
        reconnectTimeoutObj: null,

        /**
         * 当前重连次数，默认0
         */
        currentReconnectTimes: 0,

        /**
         * 清空内容
         */
        clear: function () {
            this.lockReconnect = false;
            this.reconnectTimeoutObj = null;
            this.currentReconnectTimes = 0;
        },
        /**
         * 重连
         */
        start: function () {
            let _self = this;
            if (this.lockReconnect) {
                return;
            }
            console.log("WebSocket:异常或已关闭,正在尝试重连...");
            this.lockReconnect = true;
            //没连接上会一直重连，设置延迟避免请求过多
            this.reconnectTimeoutObj && clearTimeout(this.reconnectTimeoutObjreconnectTimeoutObj);
            this.reconnectTimeoutObjreconnectTimeoutObj = setTimeout(function () {
                init(socket);
                _self.currentReconnectTimes++;
                _self.lockReconnect = false;
            }, defaultConfig.reconnectDelayTime);
        }

    }

    /**
     * websocket心跳检测
     */
    let heartCheck = {
        // 延迟的返回值
        intervalObj: null,
        // 等待服务端返回的事件
        serverTimeoutObj: null,
        // 清空
        clear: function () {
            this.reset();
            this.intervalObj = null;
            this.serverTimeoutObj = null;
        },
        // 重置心跳时间
        reset: function () {
            this.intervalObj && clearInterval(this.intervalObj);
            this.serverTimeoutObj && clearTimeout(this.serverTimeoutObj);
            return this;
        },
        // 开始发起心跳
        start: function () {
            let _self = this;
            // 超时3次才关闭
            let _retry = defaultConfig.heartbeartMaxWait;
            // 将两个超时值清空
            this.intervalObj && clearInterval(this.intervalObj);
            this.serverTimeoutObj && clearTimeout(this.serverTimeoutObj);
            // 定时间隔发送心跳消息
            this.intervalObj = setInterval(function () {
                // onmessage拿到返回的心跳就说明连接正常，就要清除定时器
                let heartBeatMessage = new proto.com.ouyunc.im.Message();
                heartBeatMessage.setFrom(loginIdentity);
                heartBeatMessage.setContentType(3);
                heartBeatMessage.setCreateTime(new Date().getTime());
                let {packetDataView} = wrapMessage(heartBeatMessage, 1, '127.0.0.1', 0, 0, 0, 6);
                // 发送心跳信息
                console.debug(new Date() + " 客户端: " + loginIdentity + ' 正在发送心跳...')
                webSocket.send(packetDataView.buffer);
                // 次数减一
                _retry--;
                // 如果超过一定时间还没重置，说明后端主动断开了
                _self.serverTimeoutObj = setTimeout(function () {
                    //如果onclose会执行reconnect，我们执行 websocket.close()就行了.如果直接执行 reconnect 会触发onclose导致重连两次
                    //计算答复的超时次数
                    if (_retry === 0) {
                        // 等待发送三次心跳（也就是3个心跳间隔时间后），
                        // 服务端都没有响应就会关闭前端websocket，当关闭websocket时，就会触发相应的操作比如重连
                        webSocket.close();
                    }
                }, defaultConfig.heartbeartReadIdleTimeout)
            }, defaultConfig.heartbeartIntervalTime)
        }
    };


    /**
     * 清除
     */
    function clear() {
        if (webSocket) {
            webSocket = null;
        }
        if (url) {
            url = '';
        }
        if (loginIdentity) {
            loginIdentity = '';
        }
        defaultConfig = {
            // 是否开启心跳，默认true，开启心跳
            heartbeatEnable: true,
            // 心跳最大等待次数（等待3个心跳时间，如果没有收到消息，则关闭客户端）
            heartbeartMaxWait: 3,
            // 心跳间隔时间10秒（并不一定10秒内会发一个心跳，如果10秒内都消息接收过来，则不会进行发送）
            heartbeartIntervalTime: 10000,
            // 心跳读超时时间（客户端发送心跳后，最大等待服务器回复是5秒，超过5秒则进行计数）
            heartbeartReadIdleTimeout: 5000,
            // 是否开启重连，默认true，开启重连
            reconnectEnable: true,
            // 如果开启重连，重连的次数，默认-1，一直重连，请设置大于0的整数
            reconnectTimes: -1,
            // 重连延迟时间，默认 2s
            reconnectDelayTime: 2000
        }
        heartCheck.clear();
        reconnect.clear();
    }

    /**
     *  将字符串ip4 转int
     */
    function ip2Int(ip4Str) {
        let ipArr = ip4Str.split(".");
        let buffer = new ArrayBuffer(4);
        let dataView = new DataView(buffer);
        for (let i = 0; i < ipArr.length; i++) {
            dataView.setInt8(i, parseInt(ipArr[i]));
        }
        return (dataView.getInt8(3) & 0xFF) | ((dataView.getInt8(2) << 8) & 0xFF00) | ((dataView.getInt8(1) << 16) & 0xFF0000) | ((dataView.getInt8(0) << 24) & 0xFF000000);
    }

    // 包装消息 dataBinary 指的是Message.proto文件经过protobuf转成 ouyunc-message.js (在下面给出)后得到的消息序列化后的数据binary
    function wrapMessage(messageProto, messageType, ip, deviceType, networkType, encryptType, serializeAlgorithm) {
        let messageDataBinary = messageProto.serializeBinary();
        // 消息id
        let snowflake = new Snowflake(1n, 1n, 0n);
        let packetId = snowflake.nextId();
        // 构建packet协议数据包&发送出去
        //定制协议前部固定长度
        let header = 24;
        // 消息字节长度
        let byteLength = messageDataBinary.byteLength;
        //总字节长度
        let len = header + byteLength;
        // 初始化Byte的二进制数据缓冲区
        let arrBuffer = new ArrayBuffer(len);
        // 加载分配好的缓冲区, 注意默认大端序读写
        let dataView = new DataView(arrBuffer);
        // 设置数据，魔数magic                                         // offset
        dataView.setInt8(0, 102);//  0
        //包协议， 1个字节
        dataView.setInt8(1, 1); // 0+1
        //协议版本号，1个字节
        dataView.setInt8(2, 1); // 0+1+1
        //协议包id 8个字节, 雪花id
        dataView.setBigInt64(3, packetId); // 0+1+1+1
        //设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
        dataView.setInt8(11, deviceType || 0);// 0+1+1+1+8
        //网络类型 1个字节， 其他， wifi,5g,4g,3g,2g
        dataView.setInt8(12, networkType || 0);// 0+1+1+1+8+1
        //发送方ipv4 4个字节，这里ip需要客户端来获取ipv4并转成4个字节的int值
        dataView.setInt32(13, ip2Int(ip || '127.0.0.1'));// 0+1+1+1+8+1+1
        //消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密;这里暂时不加密
        dataView.setInt8(17, encryptType || 0);// 0+1+1+1+8+1+1+4
        //序列化算法 1 个字节，protoBUf，采用protoStuf 的加密算法
        dataView.setInt8(18, serializeAlgorithm || 6);// 0+1+1+1+8+1+1+4+1
        //消息类型 1 个字节，这里是登录消息，如 RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
        dataView.setInt8(19, messageType);// 0+1+1+1+8+1+1+4+1+1
        //判断是否需要加密，何种算法加密,这里先不加密
        //加密后的消息长度.4个字节
        dataView.setInt32(20, byteLength); //0+1+1+1+8+1+1+4+1+1+1
        //加密后的消息内容，n个字节, 不同的消息类型有可能是不同的数据内容
        for (let i = 0; i < byteLength; i++) {
            dataView.setInt8(header + i, messageDataBinary[i]);
        }
        return {
            packetDataView: dataView,
            packetJson: {
                "magic": 102,
                "protocol": 1,
                "protocolVersion": 1,
                "packetId": packetId,
                "deviceType": deviceType || 0,
                "networkType": networkType || 0,
                "ip": ip || '127.0.0.1',
                "encryptType": encryptType || 0,
                "serializeAlgorithm": serializeAlgorithm || 6,
                "messageType": messageType,
                "messageLength": byteLength,
                "message": {
                    "from": messageProto.getFrom(),
                    "to": messageProto.getTo(),
                    "contentType": messageProto.getContentType(),
                    "content" : messageProto.getContent(),
                    "createTime": messageProto.getCreateTime()
                }
            }
        };
    }

    // 解析包数据
    function parseMessage(packetDataBinary) {
        // 注意：这里只要new Promise 就会执行，不需要手动调用
        return new Promise(function (resolve, reject) {
            //做一些异步操作
            // 定义文件读取类
            let reader = new FileReader();
            // 将blob 二进制数据转file
            reader.readAsArrayBuffer(packetDataBinary);
            // 由于reader 是异步所以使用promise 包装
            reader.onload = function () {
                //定制协议前部固定长度
                let header = 24;
                let packetBuffer = new DataView(reader.result);
                //跳过魔数 1个字节
                let magic = packetBuffer.getInt8(0);// 0
                //包协议， 1个字节
                let protocol = packetBuffer.getInt8(1);// 0+1
                //协议版本号，1个字节
                let protocolVersion = packetBuffer.getInt8(2);// 0+1+1
                //协议包id 8个字节
                let packetId = packetBuffer.getBigInt64(3);// 0+1+1+1
                //设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
                let deviceType = packetBuffer.getInt8(11);// 0+1+1+1+8
                //网络类型 1个字节 wifi,5g,4g,3g,2g...
                let networkType = packetBuffer.getInt8(12);// 0+1+1+1+8+1
                // 发送端ip 4个字节
                let ip = packetBuffer.getInt32(13);// 0+1+1+1+8+1+1
                //消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密
                let encryptType = packetBuffer.getInt8(17);// 0+1+1+1+8+1+1+4
                //序列化算法 1 个字节，json/jdk/hessian/kryo/protoStuff(protoBUf)
                let serializeAlgorithm = packetBuffer.getInt8(18);// 0+1+1+1+8+1+1+4+1
                //消息类型,1个字节
                let messageType = packetBuffer.getInt8(19);// 0+1+1+1+8+1+1+4+1+1
                //加密后的消息长度.4个字节
                let messageLength = packetBuffer.getInt32(20);// 0+1+1+1+8+1+1+4+1+1+1

                let message = new ArrayBuffer(messageLength);
                let messageDV = new DataView(message);
                for (let i = 0; i < messageLength; i++) {
                    messageDV.setInt8(i, packetBuffer.getInt8(header + i));
                }
                // 定义客户端需要处理的消息
                let messageProto = proto.com.ouyunc.im.Message.deserializeBinary(message);
                // 将message result 返回
                resolve({
                    message: messageProto,
                    messageType: messageType,
                    messageId: packetId
                })
            }

        });
    }

    // =========================================挂载到Socket对象上==============================================
    /**
     * 发送消息,需要处理发送消息
     * message {
            // string 类型
            "from": “消息发送者唯一标识”,
            // string 类型
            "to": “消息接收者唯一标识”,
            // byte 类型
            "contentType": “消息内容类型”,
            // json string 类型
            "content" : “消息类容，根据不同的消息内容类型会有不同的消息内容格式”
      }

     * @param packet
     * @param callback 发送完成回调函数
     */
    Socket.prototype.send = function (packet, callback) {
        let {message, messageType, ip, deviceType, networkType, encryptType, serializeAlgorithm} = packet;
        let messageProto = new proto.com.ouyunc.im.Message();
        messageProto.setFrom(message.from);
        messageProto.setTo(message.to);
        messageProto.setContentType(message.contentType);
        messageProto.setContent(message.content);
        messageProto.setCreateTime(new Date().getTime())
        // 组装message
        let {packetDataView, packetJson} = wrapMessage(messageProto, messageType, ip, deviceType, networkType, encryptType, serializeAlgorithm);
        if (!webSocket) {
            if (this.onerror instanceof Function) {
                this.onerror(packetJson);
            }
            throw 'webSocket实例不能为空！';
        }
        webSocket.send(packetDataView.buffer);
        // 如果发送登录消息
        if (messageType === 2) {
            // 将该登录值存储起来
            loginIdentity = JSON.parse(message.content).identity;
            // 开始启动心跳检测
            heartCheck.reset().start();
        }
        // 回调出去
        if (callback instanceof Function) {
            callback(packetJson);
        }
    }


    /**
     * 关闭websocket
     */
    Socket.prototype.close = function () {
        console.log("webSocket正在关闭...")
        if (webSocket) {
            webSocket.close();
        }
    }
    return Socket;
})(Snowflake);


