/**
 * WebSocket 客户端实现
 * 支持自动重连、心跳检测、消息序列化等功能
 */
class Socket {
    // 静态常量定义
    static MAGIC = 'OUYUNC'; // 'OUYUNC'
    static MAGIC_NUMBER = [79, 85, 89, 85, 78, 67]; // 'OUYUNC'
    static HEADER_LENGTH = 26;
    static PROTOCOL = 1;
    static PROTOCOL_VERSION = 1;
    static VERSION = '6.5.2';
    static MESSAGE_TYPES = {
        HEARTBEAT: -1,
        LOGIN: -2,
        // 收到服务端接收到消息的回复
        QOS_S2C_ACK: -3,
        // 收到对方消息后，回复服务端
        QOS_C2S_ACK: -4,
    };
    static MESSAGE_CONTENT_TYPES = {
        HEARTBEAT: -1,
        TEXT_CONTENT: -128,
    };
    static QOS_LEVEL = {
        QOS_0: 0,
        QOS_1: 1,
        QOS_2: 2,
        QOS_3: 3,
    };

    // 文本编解码器
    static ENCODER = new TextEncoder();
    static DECODER = new TextDecoder();

    /**
     * 默认配置
     */
    static DEFAULT_CONFIG = {
        clientType: 1, // 1-web, 2-uniapp
        Message: null, // protobuf Message 对象
        deviceType: 0,
        networkType: 0,
        encryptType: 0,
        serializeAlgorithm: 6, // 6-protobuf, 2-json
        heartbeat: {
            enable: true,
            maxWait: 3,
            interval: 10000,
            timeout: 5000
        },
        reconnect: {
            enable: true,
            maxTimes: -1,
            delay: 2000
        }
    };

    /**
     * 构造函数
     */
    constructor(url, config = {}) {
        this._validateEnvironment(config);
        this._validateUrl(url);

        this.url = url;
        this.config = this._mergeConfig(config);
        this.loginIdentity = '';

        // 初始化状态
        this.connected = false;
        this.reconnecting = false;

        // 初始化心跳检测器和重连管理器
        this.heartbeatManager = new HeartbeatManager(this);
        this.reconnectManager = new ReconnectManager(this);
        this.snowflake = new Snowflake(9, 11);
        // 初始化WebSocket
        this._initWebSocket();
        console.log(`欢迎使用OUYUNC-IM客户端,如果需要帮助,请联系作者。`);
        console.log(`Initializing OuyuncIM v${Socket.VERSION}`);
    }

    /**
     * 发送消息
     */
    send(packet) {
        try {
            if (!this.webSocket) {
                throw new Error('WebSocket connection not established');
            }

            const {binaryPacketId, message, messageType, deviceType, networkType, encryptType, serializeAlgorithm} =
                this._validatePacket(packet);

            const wrappedMessage = this._wrapMessage({
                binaryPacketId,
                message,
                messageType,
                deviceType: deviceType || this.config.deviceType,
                networkType: networkType || this.config.networkType,
                encryptType: encryptType || this.config.encryptType,
                serializeAlgorithm: serializeAlgorithm || this.config.serializeAlgorithm
            });

            // 发送消息
            if (this.config.clientType === 1) {
                this.webSocket.send(wrappedMessage.buffer);
            } else {
                this.webSocket.send({data: wrappedMessage.buffer});
            }

            // 处理登录消息
            if (messageType === Socket.MESSAGE_TYPES.LOGIN) {
                this._handleLoginSuccess(message);
            }
            return wrappedMessage.packet;
        } catch (error) {
            console.error('Failed to send message:', error);
            throw error;
        }
    }

    /**
     * 关闭连接
     */
    close() {
        console.log('Closing WebSocket connection...');
        this.config.reconnect.enable = false; // 禁用重连
        this.heartbeatManager.stop();
        this.reconnectManager.stop();

        if (this.webSocket) {
            this.webSocket.close();
            this.webSocket = null;
        }

        this.connected = false;
        this.loginIdentity = '';
    }

    // =============== 私有方法 ===============

    /**
     * 验证运行环境
     */
    _validateEnvironment(config) {
        if (config.clientType === 1) {
            if (!window.WebSocket) {
                throw new Error('WebSocket is not supported in this browser');
            }
        }
    }

    /**
     * 验证WebSocket URL
     */
    _validateUrl(url) {
        if (!url || typeof url !== 'string') {
            throw new Error('Invalid WebSocket URL');
        }
        if (!url.startsWith('ws://') && !url.startsWith('wss://')) {
            throw new Error('WebSocket URL must start with ws:// or wss://');
        }
    }

    /**
     * 合并配置
     */
    _mergeConfig(config) {
        return {
            ...Socket.DEFAULT_CONFIG,
            ...config,
            heartbeat: {
                ...Socket.DEFAULT_CONFIG.heartbeat,
                ...(config.heartbeat || {})
            },
            reconnect: {
                ...Socket.DEFAULT_CONFIG.reconnect,
                ...(config.reconnect || {})
            }
        };
    }

    /**
     * 初始化WebSocket连接
     */
    _initWebSocket() {
        if (this.config.clientType === 1) {
            this.webSocket = new WebSocket(this.url);
            this._bindWebSocketEvents(this.webSocket);
        } else {
            this.webSocket = uni.connectSocket({
                url: this.url,
                success: () => console.log('UniApp WebSocket connecting...')
            });
            this._bindUniAppEvents(this.webSocket);
        }
    }

    /**
     * 绑定Web端WebSocket事件
     */
    _bindWebSocketEvents(ws) {
        ws.onopen = (e) => {
            console.log('WebSocket connection established');
            this.connected = true;
            this.reconnecting = false;
            this.onopen?.(e);
        };

        ws.onmessage = async (e) => {
            try {
                const message = await this._parseMessage(e.data);
                this._handleQosReceived(message);
                this.heartbeatManager.reset();
                this.onmessage?.(message);
            } catch (error) {
                console.error('Failed to parse message:', error);
            }
        };

        ws.onclose = (e) => {
            this.connected = false;
            this.onclose?.(e);
            this._handleConnectionClosed();
            console.log('WebSocket connection closed');
        };

        ws.onerror = (e) => {
            this.onerror?.(e);
            this._handleConnectionError();
            console.error('WebSocket error occurred');
        };
    }

    /**
     * 绑定UniApp端WebSocket事件
     */
    _bindUniAppEvents(ws) {
        ws.onOpen((e) => {
            console.log('WebSocket connection established');
            this.connected = true;
            this.reconnecting = false;
            this.onopen?.(e);
        });

        ws.onMessage(async (res) => {
            try {
                const message = await this._parseMessage(res.data);
                this._handleQosReceived(message);
                this.heartbeatManager.reset();
                this.onmessage?.(message);
            } catch (error) {
                console.error('Failed to parse message:', error);
            }
        });

        ws.onClose((e) => {
            this.connected = false;
            this.onclose?.(e);
            this._handleConnectionClosed();
        });

        ws.onError((e) => {
            this.onerror?.(e);
            this._handleConnectionError();
        });
    }

    /**
     * 回复qos 消息
     */
    _handleQosReceived(packet) {
        // 异常防护：校验核心字段是否存在
        if (!packet || !packet.packetId || !packet.message) {
            console.warn('Invalid packet for QoS ACK:', packet);
            return;
        }
        // 提取QoS等级（默认0，避免undefined）
        const qos = Number(packet.message.qos) || 0;
        if (qos > 0) {
            try {
                this.send({
                    // 登录消息类型，
                    messageType: Socket.MESSAGE_TYPES.QOS_C2S_ACK,
                    // 消息
                    message: {
                        id: this.snowflake.nextIdStr(),
                        from: this.loginIdentity,
                        to: '',
                        contentType: Socket.MESSAGE_CONTENT_TYPES.TEXT_CONTENT,
                        content: String(packet.packetId),
                        qos: Socket.QOS_LEVEL.QOS_1,
                        createTime: Date.now()
                    },
                });
            }catch (error) {
                console.error('Failed to send QoS ACK:', error);
            }
        }
    }
    /**
     * 处理连接关闭
     */
    _handleConnectionClosed() {
        this.connected = false;
        this.heartbeatManager.stop();

        if (this.config.reconnect.enable) {
            this.reconnectManager.start();
        }
    }

    /**
     * 处理连接错误
     */
    _handleConnectionError() {
        this.connected = false;
        this.heartbeatManager.stop();

        if (this.config.reconnect.enable) {
            this.reconnectManager.start();
        }
    }

    /**
     * 处理登录成功
     */
    _handleLoginSuccess(message) {
        const content = typeof message.content === 'string' ?
            JSON.parse(message.content) : message.content;

        this.loginIdentity = content.identity;
        console.log(`Client ${this.loginIdentity} logged in successfully`);

        if (this.config.heartbeat.enable) {
            this.heartbeatManager.start();
        }
    }


    /**
     * 验证消息包
     */
    _validatePacket(packet) {
        if (!packet || typeof packet !== 'object') {
            throw new Error('Invalid packet format');
        }

        const {message, messageType} = packet;
        if (!message || !messageType) {
            throw new Error('Message and messageType are required');
        }

        return packet;
    }

    /**
     * 包装消息
     */
    _wrapMessage({binaryPacketId, message, messageType, deviceType, networkType, encryptType, serializeAlgorithm}) {
        // 如果消息id为空则内部生成消息id；注意消息id为必须为数字类型，对应java 的long 类型，一般使用雪花id工具生成
        // let binaryMessageId;
        // if (binaryPacketId) {
        //     if (typeof binaryPacketId === 'string' && /^[01]{64}$/.test(binaryPacketId)) {
        //         binaryMessageId = binaryPacketId;
        //     } else {
        //         throw new Error('Invalid binaryPacketId format, 自定义的消息id必须为bigint类型的64位二进制字符串: ' + binaryPacketId);
        //     }
        // } else {
        //     // 生成64位二进制消息ID
        //     binaryMessageId = this._generateBinaryMessageId();
        // }
        // message 是否存在messageId,如果不存在则使用雪花id给定
        message.messageId = message.messageId || this.snowflake.nextIdStr();
        // 序列化消息内容
        const messageData = this._serializeMessage(message, serializeAlgorithm);
        const messageDataByteLength = messageData.byteLength;
        // 创建消息头
        const header = new ArrayBuffer(Socket.HEADER_LENGTH);
        const headerView = new DataView(header);

        // 写入魔数
        Socket.MAGIC_NUMBER.forEach((byte, index) => {
            headerView.setInt8(index, byte);
        });

        // 写入其他头部信息
        let offset = Socket.MAGIC_NUMBER.length;
        headerView.setInt8(offset++, 1); // 协议版本
        headerView.setInt8(offset++, 1); // 协议类型

        // 写入消息ID (8字节)
        //const idBytes = this._splitBinaryMessageId(binaryMessageId);
        //headerView.setUint32(offset, idBytes.high);
        //headerView.setUint32(offset + 4, idBytes.low);
        // 全写0 无效数据
        //headerView.setUint32(offset, 0);
        //headerView.setUint32(offset + 4, 0);
        // 跳过消息id，服务端维护
        offset += 8;

        // 写入其他字段
        headerView.setInt8(offset++, deviceType);
        headerView.setInt8(offset++, networkType);
        headerView.setInt8(offset++, encryptType);
        headerView.setInt8(offset++, serializeAlgorithm);
        headerView.setInt8(offset++, messageType);
        headerView.setInt8(offset++, 0); // 保留字段

        // 写入消息长度
        headerView.setUint32(offset, messageDataByteLength);

        // 合并头部和消息内容
        const finalBuffer = new ArrayBuffer(Socket.HEADER_LENGTH + messageDataByteLength);
        new Uint8Array(finalBuffer).set(new Uint8Array(header), 0);
        new Uint8Array(finalBuffer).set(new Uint8Array(messageData), Socket.HEADER_LENGTH);

        return {
            buffer: finalBuffer,
            packet: {
                magic: Socket.MAGIC,
                protocol: Socket.PROTOCOL,
                protocolVersion: Socket.PROTOCOL_VERSION,
                //packetId: Snowflake.binaryToDecimalStr(binaryMessageId),
                deviceType: deviceType,
                networkType: networkType,
                encryptType: encryptType,
                serializeAlgorithm: serializeAlgorithm,
                messageType: messageType,
                retain: 0,
                messageLength: messageDataByteLength,
                message: message
            }
        };
    }

    /**
     * 解析消息
     */
    _parseMessage(data) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => {
                try {
                    const buffer = reader.result;
                    const dataView = new DataView(buffer);

                    // 验证魔数
                    for (let i = 0; i < Socket.MAGIC_NUMBER.length; i++) {
                        if (dataView.getInt8(i) !== Socket.MAGIC_NUMBER[i]) {
                            throw new Error('Invalid message format');
                        }
                    }

                    let offset = Socket.MAGIC_NUMBER.length;

                    // 读取头部信息
                    const protocol = dataView.getInt8(offset++);
                    const version = dataView.getInt8(offset++);

                    // 读取消息ID
                    const packetIdHigh = dataView.getUint32(offset);
                    const packetIdLow = dataView.getUint32(offset + 4);
                    const packetId = this._combineMessageId(packetIdHigh, packetIdLow);
                    offset += 8;

                    // 读取其他字段
                    const deviceType = dataView.getInt8(offset++);
                    const networkType = dataView.getInt8(offset++);
                    const encryptType = dataView.getInt8(offset++);
                    const serializeAlgorithm = dataView.getInt8(offset++);
                    const messageType = dataView.getInt8(offset++);
                    const retain = dataView.getInt8(offset++);

                    // 读取消息长度
                    const messageLength = dataView.getUint32(offset);
                    offset += 4;

                    // 提取消息内容
                    const messageBuffer = buffer.slice(offset, offset + messageLength);
                    const message = this._deserializeMessage(messageBuffer, serializeAlgorithm);

                    resolve({
                        packetId: packetId,
                        protocol: protocol,
                        version: version,
                        deviceType: deviceType,
                        networkType: networkType,
                        encryptType: encryptType,
                        serializeAlgorithm: serializeAlgorithm,
                        messageType,
                        retain: retain,
                        message
                    });
                } catch (error) {
                    reject(error);
                }
            };

            reader.onerror = () => reject(new Error('Failed to read message data'));
            reader.readAsArrayBuffer(data instanceof Blob ? data : new Blob([data]));
        });
    }

    /**
     * 序列化消息
     */
    _serializeMessage(message, algorithm) {
        if (algorithm === 6) { // Protobuf
            if (!this.config.Message) {
                throw new Error('Protobuf Message class is not configured');
            }
            const protoMessage = new this.config.Message();
            Object.entries(message).forEach(([key, value]) => {
                const setterName = `set${key.charAt(0).toUpperCase()}${key.slice(1)}`;
                if (typeof protoMessage[setterName] === 'function') {
                    protoMessage[setterName](value);
                    // 进行特殊处理
                } else if (typeof protoMessage[`${setterName}List`] === 'function') {
                    protoMessage[`${setterName}List`](value);
                }
            });
            return protoMessage.serializeBinary();
        } else if (algorithm === 2) { // JSON
            return Socket.ENCODER.encode(JSON.stringify(message));
        } else {
            throw new Error('Unsupported serialization algorithm');
        }
    }

    /**
     * 反序列化消息
     */
    _deserializeMessage(buffer, algorithm) {
        if (algorithm === 6) { // Protobuf
            if (!this.config.Message) {
                throw new Error('Protobuf Message class is not configured');
            }
            const message = this.config.Message.deserializeBinary(buffer);
            return {
                id: message.getId(),
                from: message.getFrom(),
                to: message.getTo(),
                contentType: message.getContentType(),
                content: message.getContent(),
                at: message.getAtList(),
                ref: message.getRefList(),
                qos: message.getQos(),
                extra: message.getExtra(),
                createTime: message.getCreateTime()
            };
        } else if (algorithm === 2) { // JSON
            return JSON.parse(Socket.DECODER.decode(buffer), function (key, value) {
                if (key === "metadata") {
                    // 对于 "metadata" 字段，返回 undefined 以跳过反序列化
                    return undefined;
                }
                return value;
            });
        } else {
            throw new Error('Unsupported serialization algorithm');
        }
    }


    /**
     * 生成64位二进制消息ID
     */
    _generateBinaryMessageId() {
        return this.snowflake.nextBinaryId();
    }

    /**
     * 分割消息ID为高32位和低32位
     */
    _splitBinaryMessageId(binaryMessageId) {
        return {
            high: parseInt(binaryMessageId.substring(0, 32), 2),
            low: parseInt(binaryMessageId.substring(32), 2)
        };
    }

    /**
     * 合并高32位和低32位为消息ID
     */
    _combineMessageId(high, low) {
        const highBits = high.toString(2).padStart(32, '0');
        const lowBits = low.toString(2).padStart(32, '0');
        return Snowflake.binaryToDecimalStr(`${highBits}${lowBits}`);
    }
}

/**
 * 心跳管理器
 */
class HeartbeatManager {
    constructor(socket) {
        this.socket = socket;
        this.intervalId = null;
        this.timeoutId = null;
        this.retryCount = 0;
    }

    /**
     * 开始心跳检测
     */
    start() {
        if (!this.socket.config.heartbeat.enable) return;

        this.stop();
        this._scheduleHeartbeat();
    }

    /**
     * 停止心跳检测
     */
    stop() {
        clearInterval(this.intervalId);
        clearTimeout(this.timeoutId);
        this.retryCount = 0;
    }

    /**
     * 重置心跳检测
     */
    reset() {
        if (this.socket.config.heartbeat.enable) {
            this.stop();
            this._scheduleHeartbeat();
        }
    }

    /**
     * 调度心跳消息
     */
    _scheduleHeartbeat() {
        this.intervalId = setInterval(() => {
            this._sendHeartbeat();
        }, this.socket.config.heartbeat.interval);
    }

    /**
     * 发送心跳消息
     */
    _sendHeartbeat() {
        try {
            this.socket.send({
                message: {
                    id: this.socket.snowflake.nextIdStr(),
                    from: this.socket.loginIdentity,
                    to: '',
                    contentType: Socket.MESSAGE_CONTENT_TYPES.HEARTBEAT,
                    content: '',
                    qos: 0,
                    createTime: Date.now()
                },
                messageType: Socket.MESSAGE_TYPES.HEARTBEAT
            });

            this._waitForResponse();
        } catch (error) {
            console.error('Failed to send heartbeat:', error);
        }
    }

    /**
     * 等待心跳响应
     */
    _waitForResponse() {
        clearTimeout(this.timeoutId);
        this.timeoutId = setTimeout(() => {
            this.retryCount++;
            if (this.retryCount >= this.socket.config.heartbeat.maxWait) {
                console.log('Heartbeat timeout, closing connection...');
                this.socket.close();
            }
        }, this.socket.config.heartbeat.timeout);
    }
}

/**
 * 重连管理器
 */
class ReconnectManager {
    constructor(socket) {
        this.socket = socket;
        this.timeoutId = null;
        this.attempts = 0;
        this.locked = false;
    }

    /**
     * 开始重连
     */
    start() {
        if (this.locked || !this.socket.config.reconnect.enable) return;

        this.locked = true;
        this._attemptReconnect();
    }

    /**
     * 停止重连
     */
    stop() {
        clearTimeout(this.timeoutId);
        this.attempts = 0;
        this.locked = false;
    }

    /**
     * 尝试重连
     */
    _attemptReconnect() {
        const {maxTimes, delay} = this.socket.config.reconnect;

        if (maxTimes !== -1 && this.attempts >= maxTimes) {
            console.log('Max reconnection attempts reached');
            this.stop();
            return;
        }

        console.log(`Attempting to reconnect... (${this.attempts + 1}${maxTimes === -1 ? '' : '/' + maxTimes})`);

        this.timeoutId = setTimeout(() => {
            this.attempts++;
            this.socket._initWebSocket();
            this.locked = false;
        }, delay);
    }
}

/**
 * 雪花ID生成器
 * 支持浏览器环境，兼容不支持 BigInt 的浏览器
 */
class Snowflake {
    constructor(workerId = 1, dataCenterId = 1, sequence = 0) {
        // 开始时间戳 (2024-01-01)
        this.twepoch = 1704067200000;

        // 机器id所占的位数
        this.workerIdBits = 5;
        // 数据标识id所占的位数
        this.dataCenterIdBits = 5;
        // 序列在id中占的位数
        this.sequenceBits = 12;

        // 支持的最大值
        this.maxWorkerId = -1 ^ (-1 << this.workerIdBits); // 31
        this.maxDataCenterId = -1 ^ (-1 << this.dataCenterIdBits); // 31
        this.sequenceMask = -1 ^ (-1 << this.sequenceBits); // 4095

        // 机器ID向左移12位
        this.workerIdShift = this.sequenceBits;
        // 数据标识id向左移17位(12+5)
        this.dataCenterIdShift = this.sequenceBits + this.workerIdBits;
        // 时间戳向左移22位(5+5+12)
        this.timestampLeftShift = this.sequenceBits + this.workerIdBits + this.dataCenterIdBits;

        // 参数校验
        if (workerId > this.maxWorkerId || workerId < 0) {
            throw new Error(`workerId can't be greater than ${this.maxWorkerId} or less than 0`);
        }
        if (dataCenterId > this.maxDataCenterId || dataCenterId < 0) {
            throw new Error(`dataCenterId can't be greater than ${this.maxDataCenterId} or less than 0`);
        }

        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
        this.sequence = sequence;
        this.lastTimestamp = -1;

        // 检查是否支持 BigInt
        this.hasBigInt = typeof BigInt !== 'undefined';
    }

    /**
     * 获取当前时间戳
     */
    timeGen() {
        return Date.now();
    }

    /**
     * 阻塞到下一个毫秒
     */
    tilNextMillis(lastTimestamp) {
        let timestamp = this.timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = this.timeGen();
        }
        return timestamp;
    }

    /**
     * 生成下一个ID
     */
    nextIdStr() {
        let timestamp = this.timeGen();

        // 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过
        if (timestamp < this.lastTimestamp) {
            throw new Error('Clock moved backwards. Refusing to generate id');
        }

        // 如果是同一时间生成的，则进行毫秒内序列
        if (timestamp === this.lastTimestamp) {
            this.sequence = (this.sequence + 1) & this.sequenceMask;
            // 毫秒内序列溢出
            if (this.sequence === 0) {
                // 阻塞到下一个毫秒,获得新的时间戳
                timestamp = this.tilNextMillis(this.lastTimestamp);
            }
        } else {
            // 时间戳改变，毫秒内序列重置
            this.sequence = 0;
        }

        // 上次生成ID的时间戳
        this.lastTimestamp = timestamp;

        // 移位并通过或运算拼到一起组成64位的ID
        const diff = timestamp - this.twepoch;

        if (this.hasBigInt) {
            // 使用 BigInt 版本
            return this._generateWithBigInt(diff, timestamp).toString();
        } else {
            // 使用降级版本
            return Snowflake.binaryToDecimalStr(this._generateWithoutBigInt(diff, timestamp));
        }
    }

    /**
     * 使用 BigInt 生成ID
     */
    _generateWithBigInt(diff, timestamp) {
        return (BigInt(diff) << BigInt(this.timestampLeftShift)) |
            (BigInt(this.dataCenterId) << BigInt(this.dataCenterIdShift)) |
            (BigInt(this.workerId) << BigInt(this.workerIdShift)) |
            BigInt(this.sequence);
    }

    /**
     * 不使用 BigInt 生成ID (降级方案)
     */
    _generateWithoutBigInt(diff, timestamp) {
        // 使用字符串拼接方式
        const timestampBinary = diff.toString(2).padStart(41, '0');
        const dataCenterBinary = this.dataCenterId.toString(2).padStart(5, '0');
        const workerBinary = this.workerId.toString(2).padStart(5, '0');
        const sequenceBinary = this.sequence.toString(2).padStart(12, '0');

        return timestampBinary + dataCenterBinary + workerBinary + sequenceBinary;
    }

    /**
     * 生成二进制格式的ID字符串
     */
    nextBinaryId() {
        let timestamp = this.timeGen();

        // 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过
        if (timestamp < this.lastTimestamp) {
            throw new Error('Clock moved backwards. Refusing to generate id');
        }

        // 如果是同一时间生成的，则进行毫秒内序列
        if (timestamp === this.lastTimestamp) {
            this.sequence = (this.sequence + 1) & this.sequenceMask;
            // 毫秒内序列溢出
            if (this.sequence === 0) {
                // 阻塞到下一个毫秒,获得新的时间戳
                timestamp = this.tilNextMillis(this.lastTimestamp);
            }
        } else {
            // 时间戳改变，毫秒内序列重置
            this.sequence = 0;
        }

        // 上次生成ID的时间戳
        this.lastTimestamp = timestamp;

        // 移位并通过或运算拼到一起组成64位的ID
        const diff = timestamp - this.twepoch;

        return this._generateWithoutBigInt(diff, timestamp)
    }


    /**
     * 解析ID
     */
    parseId(id) {
        let binaryStr;
        if (this.hasBigInt) {
            if (typeof id === 'string') {
                id = BigInt(id);
            }
            binaryStr = id.toString(2).padStart(64, '0');
        } else {
            binaryStr = id;
        }

        const timestamp = parseInt(binaryStr.substring(0, 41), 2) + this.twepoch;
        const dataCenterId = parseInt(binaryStr.substring(41, 46), 2);
        const workerId = parseInt(binaryStr.substring(46, 51), 2);
        const sequence = parseInt(binaryStr.substring(51), 2);

        return {
            timestamp,
            dataCenterId,
            workerId,
            sequence,
            time: new Date(timestamp).toISOString()
        };
    }

    /**
     * 将二进制字符串转换为十进制字符串
     * @param {string} binaryStr - 二进制字符串
     * @returns {string} 十进制字符串
     */
    static binaryToDecimalStr(binaryStr) {
        if (typeof BigInt !== 'undefined') {
            // 支持 BigInt 的浏览器
            return BigInt(`0b${binaryStr}`).toString(10);
        } else {
            // 不支持 BigInt 的浏览器，使用自定义进制转换
            return Snowflake.customBinaryToDecimalStr(binaryStr);
        }
    }

    /**
     * 自定义二进制转十进制算法（用于不支持 BigInt 的浏览器）
     * @param {string} binaryStr - 二进制字符串
     * @returns {string} 十进制字符串
     */
    static customBinaryToDecimalStr(binaryStr) {
        let result = '0';
        const add = (x, y) => {
            let sum = '';
            let carry = 0;
            let i = x.length - 1;
            let j = y.length - 1;

            while (i >= 0 || j >= 0 || carry > 0) {
                const digit1 = i >= 0 ? parseInt(x[i]) : 0;
                const digit2 = j >= 0 ? parseInt(y[j]) : 0;
                const sum_digits = digit1 + digit2 + carry;
                sum = (sum_digits % 10) + sum;
                carry = Math.floor(sum_digits / 10);
                i--;
                j--;
            }

            return sum;
        };

        const multiply = (x, y) => {
            if (x === '0' || y === '0') return '0';

            const product = Array(x.length + y.length).fill(0);

            for (let i = x.length - 1; i >= 0; i--) {
                for (let j = y.length - 1; j >= 0; j--) {
                    const digit1 = parseInt(x[i]);
                    const digit2 = parseInt(y[j]);
                    const currentPos = i + j + 1;
                    const carry = i + j;

                    const mul = digit1 * digit2;
                    const sum = mul + product[currentPos];

                    product[currentPos] = sum % 10;
                    product[carry] += Math.floor(sum / 10);
                }
            }

            while (product[0] === 0) {
                product.shift();
            }

            return product.length ? product.join('') : '0';
        };

        for (let i = 0; i < binaryStr.length; i++) {
            if (binaryStr[i] === '1') {
                const power = binaryStr.length - 1 - i;
                let base = '1';
                for (let j = 0; j < power; j++) {
                    base = multiply(base, '2');
                }
                result = add(result, base);
            }
        }

        return result;
    }
}


// 导出
export default Socket;

