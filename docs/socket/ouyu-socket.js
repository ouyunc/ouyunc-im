// 导入外部js

/**
 * 封装通用websocket
 */
const Socket = /** @class */ (function (window) {

    // websocket 对象
    let websocket = null;
    //避免ws重复连接
    let lockReconnect = false;
    // 重连延迟时间
    let reconnectDelayTime = 2000;
    // 重连延迟返回的标识
    let reconnectTimeoutObj;
    // websocket url
    let url = '';
    // 最终的配置
    let config = {};

    /**
     * websocket心跳检测
     */
    let heartCheck = {
        // 重试次数
        retry: 3,
        // 每隔10秒发送心跳，单位毫秒
        timeout: 10000,
        // 服务端超时时间,最多等待5秒服务端的响应，单位毫秒，期望在该时间内收到服务端响应的信息
        severTimeout: 5000,

        // 延迟的返回值
        timeoutObj: null,
        //
        serverTimeoutObj: null,
        // 重置心跳时间
        reset: function () {
            // 重置次数
            this.retry = 3;
            clearTimeout(this.timeoutObj);
            clearTimeout(this.serverTimeoutObj);
            return this;
        },
        // 开始发起心跳
        start: function () {
            let _self = this;
            // 超时3次才关闭
            let _retry = this.retry;
            // 将两个超时值清空
            this.timeoutObj && clearTimeout(this.timeoutObj);
            this.serverTimeoutObj && clearTimeout(this.serverTimeoutObj);
            this.timeoutObj = setTimeout(function () {
                //这里发送一个心跳，后端收到后，返回一个心跳消息，
                //onmessage拿到返回的心跳就说明连接正常
                let heartBeatMessage = new proto.com.ouyu.im.Message();
                heartBeatMessage.setFrom(config.identity);
                // 服务端ip,这里没有可以不设置
                heartBeatMessage.setTo("");
                heartBeatMessage.setContentType(SOCKET_CONSTANT.PACKET.MESSAGE_CONTENT_TYPE.TEXT_CONTENT);
                heartBeatMessage.setContent(SOCKET_CONSTANT.PING);
                heartBeatMessage.setCreateTime(new Date().getTime());
                let messageDataBinary = heartBeatMessage.serializeBinary();
                let packetDataView = wrapMessage(messageDataBinary, SOCKET_CONSTANT.PACKET.MESSAGE_TYPE.IM_PING_PONG);
                // 发送心跳信息
                websocket.send(packetDataView.buffer);
                // 次数减一
                _retry--;
                // 如果超过一定时间还没重置，说明后端主动断开了
                _self.serverTimeoutObj = setTimeout(function () {
                    //如果onclose会执行reconnect，我们执行 websocket.close()就行了.如果直接执行 reconnect 会触发onclose导致重连两次
                    //计算答复的超时次数
                    if (_retry === 0) {
                        // 等待发送三次心跳（也就是30秒过后），
                        // 服务端都没有响应就会关闭前端websocket，当关闭websocket时，就会触发相应的操作比如重连
                        websocket.close();
                    }
                }, _self.severTimeout)
            }, this.timeout)
        }
    };


    // 重连
    function reconnect() {
        if (lockReconnect) {
            return;
        }
        lockReconnect = true;
        //没连接上会一直重连，设置延迟避免请求过多
        reconnectTimeoutObj && clearTimeout(reconnectTimeoutObj);
        reconnectTimeoutObj = setTimeout(function () {
            init();
            lockReconnect = false;
        }, reconnectDelayTime);
    }

    // 应答服务器一个收到信息的ack
    function ackServer(messageObj, packetId) {
        // 应答给服务端，让服务端知道自己已经收到消息
        let acknowledgeMessage = new proto.com.ouyu.im.Message();
        acknowledgeMessage.setFrom(messageObj.getTo());
        acknowledgeMessage.setFromServerAddress(messageObj.getToServerAddress())
        acknowledgeMessage.setTo(messageObj.getFrom());
        acknowledgeMessage.setToServerAddress(messageObj.getFromServerAddress());
        acknowledgeMessage.setContentType(SOCKET_CONSTANT.PACKET.MESSAGE_CONTENT_TYPE.TEXT_CONTENT);
        // 需要转成字符串
        acknowledgeMessage.setContent(packetId.toString());
        acknowledgeMessage.setCreateTime(new Date().getTime());
        Socket.prototype.send(acknowledgeMessage, SOCKET_CONSTANT.PACKET.MESSAGE_TYPE.IM_ACKNOWLEDGE);
    }

    // 实现对象的深拷贝
    function deepClone(srcObj, destObj) {
        let obj = destObj || {};
        for (let i in srcObj) {
            let prop = srcObj[i];
            if (prop === obj) {
                continue;
            }
            if (typeof prop === 'object') {
                obj[i] = (prop.constructor === Array) ? [] : Object.create(prop);
            } else {
                obj[i] = prop;
            }
        }
        return obj;
    }

    // 处理默认配置与用户配置的属性
    function handleConfig(defaultConfig, socketConfig) {
        if (!socketConfig) {
            return defaultConfig;
        }
        // 将对象中的key 以数组的形式取出
        let keys = Object.keys(socketConfig);
        let config = deepClone(socketConfig, {});
        // 循环对象属性
        for (let key in defaultConfig) {
            //此处hasOwnProperty是判断自有属性，使用 for in 循环遍历对象的属性时，原型链上的所有属性都将被访问会避免原型对象扩展带来的干扰
            if (defaultConfig.hasOwnProperty(key)) {
                // 从默认配置中取出每个属性值
                let objProperty = defaultConfig[key];
                // es语法，判断默认配置的每个属性是否在用户自定义的配置中
                if (!keys.includes(key)) {
                    // 判断没有在用户配置中的默认配置中的属性是什么类型
                    switch (typeof objProperty) {
                        case "string":
                            config[key] = objProperty;
                            break;
                        case "number":
                            config[key] = objProperty;
                            break;
                        case "boolean":
                            config[key] = objProperty;
                            break;
                        case "function":
                            config[key] = objProperty;
                            break;
                        case "bigint":
                            config[key] = objProperty;
                            break;
                        case "symbol":
                            config[key] = objProperty;
                            break;
                        case "object":
                            config[key] = deepClone(objProperty, {});
                            break;
                        default:
                            break;
                    }
                }

            }
        }
        return config;
    }


    /**
     * 通过构造方法构建一个Websocket对象；注意：一个函数一个一个对象
     * @param websocketConfig
     *      url         string  //链接的url，如:ws://127.0.0.1:8000/path 或 wss://127.0.0.1:8000/path
     *      onopen: function () {}, //连接成功后触发发
     *      onmessage: function () {},  //接收到消息触发
     *      onclose: function () {},    //关闭后触发
     *      onerror: function () {},    //发生错误后触发
     */
    function Socket(webSocketUrl, socketConfig) {
        window.WebSocket = window.WebSocket || window.MozWebSocket;
        if (!window.WebSocket) {
            throw "当前浏览器或版本不支持WebSocket！请更换其他浏览器或版本"
            return;
        }
        if (!webSocketUrl) {
            throw "非法参数，请正确设置url,如：ws://127.0.0.1:8000/";
            return;
        }
        //定义默认配置参数
        let defaultConfig = {
            // 客户端唯一标识，可以是手机号，身份证号，邮箱等
            identity: '',
            // 服务端分配的key
            appKey: '',
            // 服务端分配的秘钥
            appSecret: '',
            // ========================下面是回调方法，提供用户回调=======================
            // 连接开启
            onopen: function (e) {
            },
            // 接收消息
            onmessage: function (message, messageType) {
            },
            // 连接关闭
            onclose: function (e) {
            },
            // 连接异常
            onerror: function (e) {
            },
        };
        // 给全局变量赋值
        url = webSocketUrl;
        // 将用户自定义配置与默认配置进行整合（如果用户自定义配置就是用用户设置的，否则使用默认值）
        // 将默认config 赋值给this.config
        config = handleConfig(defaultConfig, socketConfig);
        // 初始化原生的websocket
        init();
    }

    // 初始化
    function init() {
        // 初始化
        // 创建原生websocket
        let socket = new WebSocket(url);
        socket.onopen = function (e) {
            onopen(e);
        };
        socket.onmessage = function (e) {
            onmessage(e);
        };
        socket.onclose = function (e) {
            onclose(e);
        };
        socket.onerror = function (e) {
            onerror(e);
        }
        // 给全局变量赋值
        websocket = socket;
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
                let header = SOCKET_CONSTANT.PACKET.HEADER_LENGTH;
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
                let messageObj = proto.com.ouyu.im.Message.deserializeBinary(message);
                //new Uint8Array(message)
                // 判断是什么消息类型就转成什么消息返回给前端
                switch (messageType) {
                    case 0:
                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    case 3:
                        break;
                    case 4:
                        break;
                    case 5:
                        // im_login
                        break;
                    case 6:
                        // ping-pong
                        break;
                    case 7:
                        // IM_P_CHAT
                        // 应答给服务端，让服务端知道自己已经收到消息
                        ackServer(messageObj, packetId);
                        break;
                    case 8:
                        // IM_G_CHAT
                        break;
                    case 9:
                        // IM_BROADCAST
                        break;
                    case 10:
                        // IM_ACK_KNOWLEDGE
                        break;
                    case SOCKET_CONSTANT.PACKET.MESSAGE_TYPE.IM_FRIEND_ADD_REQ:
                        // IM_PUSH_FRIEND
                        // 应答给服务端，让服务端知道自己已经收到消息
                        ackServer(messageObj, packetId);
                        break;
                    case 12:
                        // IM_PUSH_GROUP
                        break;
                    default:
                        throw "暂不支持该消息类型！";
                }
                // 将result 返回
                resolve({
                    message: messageObj,
                    messageType: messageType,
                    messageId: packetId,
                })
            }

        });
    }

    // 包装消息
    function wrapMessage(dataBinary, messageType) {
        let snowflake = new Snowflake(SOCKET_CONSTANT.SNOWFLAKE.WORKER_ID, SOCKET_CONSTANT.SNOWFLAKE.DATA_CENTER_ID, SOCKET_CONSTANT.SNOWFLAKE.SEQUENCE);
        // 构建packet协议数据包&发送出去
        //定制协议前部固定长度
        let header = SOCKET_CONSTANT.PACKET.HEADER_LENGTH;
        // 消息字节长度
        let byteLength = dataBinary.byteLength;
        //总字节长度
        let len = header + byteLength;
        // 初始化Byte的二进制数据缓冲区
        let arrBuffer = new ArrayBuffer(len);
        // 加载分配好的缓冲区, 注意默认大端序读写
        let dataView = new DataView(arrBuffer);
        // 设置数据，魔数magic                                         // offset
        dataView.setInt8(0, SOCKET_CONSTANT.PACKET.MAGIC);//  0
        //包协议， 1个字节
        dataView.setInt8(1, SOCKET_CONSTANT.PACKET.PROTOCOL); // 0+1
        //协议版本号，1个字节
        dataView.setInt8(2, SOCKET_CONSTANT.PACKET.PROTOCOL_VERSION); // 0+1+1
        //协议包id 8个字节, 雪花id
        dataView.setBigInt64(3, snowflake.nextId()); // 0+1+1+1
        //设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
        dataView.setInt8(11, deviceType());// 0+1+1+1+8
        //网络类型 1个字节， 其他， wifi,5g,4g,3g,2g
        dataView.setInt8(12, networkType());// 0+1+1+1+8+1
        //发送方ip 4个字节
        dataView.setInt32(13, ip2Int(getIp()));// 0+1+1+1+8+1+1
        //消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密;这里暂时不加密
        dataView.setInt8(17, SOCKET_CONSTANT.PACKET.SYMMETRY_ENCRYPT.NONE);// 0+1+1+1+8+1+1+4
        //序列化算法 1 个字节，protoBUf，采用protoStuf 的加密算法
        dataView.setInt8(18, SOCKET_CONSTANT.PACKET.SERIALIZER.PROTO_STUFF);// 0+1+1+1+8+1+1+4+1
        //消息类型 1 个字节，这里是登录消息，如 RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
        dataView.setInt8(19, messageType);// 0+1+1+1+8+1+1+4+1+1
        //判断是否需要加密，何种算法加密,这里先不加密
        //加密后的消息长度.4个字节
        dataView.setInt32(20, byteLength); //0+1+1+1+8+1+1+4+1+1+1
        //加密后的消息内容，n个字节, 不同的消息类型有可能是不同的数据内容
        for (let i = 0; i < byteLength; i++) {
            dataView.setInt8(header + i, dataBinary[i]);
        }
        return dataView;
    }


    //连接成功
    function onopen(e) {
        // 当连接成功的时候，发送登录认证信息
        console.log("1 连接打开,正在发送登录信息...")
        let loginMessage = new proto.com.ouyu.im.Message()
        const timestamp = new Date().getTime();
        if (!config.identity || !config.appKey || !config.appSecret) {
            throw "请先设置连接后的的登录信息"
        }
        loginMessage.setFrom(config.identity);
        // 服务端ip,这里没有可以不设置
        loginMessage.setTo("");
        loginMessage.setContentType(SOCKET_CONSTANT.PACKET.MESSAGE_CONTENT_TYPE.LOGIN_CONTENT);
        let content = {
            "identity": config.identity,
            "appKey": config.appKey,
            "createTime": timestamp,
            "signatureAlgorithm": SOCKET_CONSTANT.PACKET.ASYMMETRIC_ENCRYPT.MD5,
            "signature": signature(config.appKey, config.appSecret, config.identity, timestamp)
        };
        loginMessage.setContent(JSON.stringify(content));
        loginMessage.setCreateTime(timestamp);
        let messageDataBinary = loginMessage.serializeBinary();
        let packetDataView = wrapMessage(messageDataBinary, SOCKET_CONSTANT.PACKET.MESSAGE_TYPE.IM_LOGIN);
        // 发送登录消息
        websocket.send(packetDataView.buffer);
        // 延迟10秒后开始发送心跳检测
        heartCheck.reset().start();
        // 回调自定义的开启事件
        config.onopen(e);
    }

    //接收消息回调
    function onmessage(e) {
        // 1当收到是登录成功的回复信息或其他消息后，进行清除延时数据，重新计时发送心跳
        heartCheck.reset().start();
        // 2进行消息处理，采用promise, 此时的packet就是resolve返回的自定义封装的对象
        parseMessage(e.data).then(function (packet) {
            // 3然后回调用户自定义的接收信息
            config.onmessage(packet.message, packet.messageType, packet.messageId)
        });

    }

    //关闭回调
    function onclose(e) {
        console.log("WebSocket:已关闭");
        heartCheck.reset();//心跳检测
        reconnect();
        // 回到用户自定义
        config.onclose(e);
    }

    //异常回调
    function onerror(e) {
        console.log("WebSocket:发生错误，开始进行重连...");
        if (websocket) {
            websocket.close();
        }
        // 重置心跳检测
        heartCheck.reset();
        reconnect();
        // 重连然后回调
        config.onerror(e);
    }

    //发送消息,需要处理发送消息
    Socket.prototype.send = function (message, messageType) {
        let messageDataBinary = message.serializeBinary();
        let packetDataView = wrapMessage(messageDataBinary, messageType);
        // 发送登录消息
        websocket.send(packetDataView.buffer);
    }


    //监听窗口关闭事件，当窗口关闭时，主动去关闭websocket连接，防止连接还没断开就关闭窗口，server端会抛异常。
    window.onbeforeunload = function () {
        console.log("即将关闭窗口")
        if (websocket) {
            websocket.close();
        }
    };

    return Socket;
})(window);
