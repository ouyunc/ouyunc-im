// 导出TextEncoder / TextDecoder 的polyfill 解决在edge 以及ie 浏览器中去兜底
require('./text-decode.js') ;

var {
	Snowflake,
	bigInt
} = require("./snowflake-id.js");

/**
 * 封装通用websocket
 */
const Socket = /** @class */ (function(Snowflake, bigInt) {
	// =========================================定义全局变量===========================================

	/**
	 * 真实webSocket 对象
	 */
	let webSocket = null;
	/**
	 * 外部定义的socket 对象
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
	 * 文本编码器
	 */
	const ENCODER = new TextEncoder();

	/**
	 * 文本解码码器
	 */
	const DECODER = new TextDecoder();


	/**
	 * 默认配置参数,注意这里的超时等参数要与服务端相匹配
	 */
	let defaultConfig = {
		// 客户端类型： 1-web,2-uniapp，...，默认web
		clientType: 1,
		// protobuf 中的Message 对象
		Message: null,
		//==================== 客户端消息发送方设置信息===================
		// 消息发送方设备类型，
		deviceType: 0,
		// 网络类型，具体什么类型可以查看后端设备枚举类型
		networkType: 0,
		// 消息加密算法类型，具体什么类型可以查看后端设备枚举类型
		encryptType: 0,
		// 序列化算法类型，具体什么类型可以查看后端设备枚举类型，注意：如果使用protobuf 序列化，需要引入google-protobuf.js 以及ouyunc-message.js 并放在同一个目录下
		serializeAlgorithm: 6,

		//==================== 客户端连接的设置信息===================
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
		console.log("欢迎使用偶云客-IM v4.0.0 uniapp客户端sdk.")
		defaultConfig = config || defaultConfig
		if (defaultConfig.clientType !== 1 && defaultConfig.clientType !== 2) {
			throw "暂不支持该客户端类型！";
		}
		if (defaultConfig.clientType === 1) {
			window.WebSocket = window.WebSocket || window.MozWebSocket;
			if (!window.WebSocket || !window.WebSocket.prototype.send) {
				throw "当前浏览器或版本不支持WebSocket！请更换其他浏览器或版本";
			}
		}
		if (!webSocketUrl) {
			throw "非法参数 '" + url + "',请正确设置url,如: ws://127.0.0.1:8000/";
		}
		// 初始化全局变量
		url = webSocketUrl;
		socket = this;
		// 将Socket 实例传递过去
		init();
	}

	// =========================================定义方法==============================================
	// 初始化
	function init() {
		// 初始化
		// 创建原生websocket
		if (defaultConfig.clientType === 1) {
			// 创建原生websocket
			webSocket = new WebSocket(url);
			webSocket.onopen = onopen;
			webSocket.onmessage = onmessage;
			webSocket.onclose = onclose;
			webSocket.onerror = onerror;
		}
		if (defaultConfig.clientType === 2) {
			webSocket = uni.connectSocket({
				url: url,
				success: () => {}
			});
			webSocket.onOpen(onopen);
			webSocket.onMessage(onmessage);
			webSocket.onClose(onclose)
			webSocket.onError(onerror);

		}



	};

	// 连接打开
	function onopen(e) {
		if (socket.onopen instanceof Function) {
			socket.onopen(e);
		} else {
			console.log('ws 连接打开了');
		}
	}
	// 消息接收方法
	function onmessage(e) {
		// 如果开启心跳，则进行重置心跳
		if (defaultConfig.heartbeatEnable) {
			// 1当收到是登录成功的回复信息或其他消息后，进行清除延时数据，重新计时发送心跳
			heartCheck.reset().start();
		}
		// 处理好的消息在传递出去
		if (socket.onmessage instanceof Function) {
			parseMessage(e.data).then(function(packet) {
				// messageId是long类型，messageType是byte类型，
				socket.onmessage({
					messageId: packet.messageId,
					messageType: packet.messageType,
					message: packet.message
				});
			})
		} else {
			console.log('接收到消息了=>' + e);
		}
	}
	// 连接关闭方法
	function onclose(e) {
		if (socket.onclose instanceof Function) {
			socket.onclose(e);
		} else {
			console.log('ws 连接关闭了');
		}
		// 判断是否开启重连，并需要重连
		if (defaultConfig.reconnectEnable) {
			if (defaultConfig.heartbeatEnable) {
				// 重置心跳检测
				heartCheck.reset();
			}
			// 重连
			if (defaultConfig.reconnectTimes === -1 || reconnect.currentReconnectTimes < defaultConfig
				.reconnectTimes) {
				reconnect.start();
			}
		}
	}
	// 连接错误的方法
	function onerror(e) {
		if (socket.onerror instanceof Function) {
			socket.onerror(e);
		} else {
			console.log('ws 连接异常了');
		}
		if (defaultConfig.reconnectEnable) {
			if (defaultConfig.heartbeatEnable) {
				// 重置心跳检测
				heartCheck.reset();
			}
			// 重连
			if (defaultConfig.reconnectTimes === -1 || reconnect.currentReconnectTimes < defaultConfig
				.reconnectTimes) {
				reconnect.start();
			}
		}
	}
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
		clear: function() {
			this.reconnectTimeoutObj && clearTimeout(this.reconnectTimeoutObj);
			this.lockReconnect = false;
			this.reconnectTimeoutObj = null;
			this.currentReconnectTimes = 0;
		},
		/**
		 * 重连
		 */
		start: function() {
			let _self = this;
			if (this.lockReconnect) {
				return;
			}
			console.log("WebSocket:异常或已关闭,正在尝试重连...");
			this.lockReconnect = true;
			//没连接上会一直重连，设置延迟避免请求过多
			this.reconnectTimeoutObj && clearTimeout(this.reconnectTimeoutObj);
			this.reconnectTimeoutObj = setTimeout(function() {
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
		clear: function() {
			this.reset();
			this.intervalObj = null;
			this.serverTimeoutObj = null;
		},
		// 重置心跳时间
		reset: function() {
			this.intervalObj && clearInterval(this.intervalObj);
			this.serverTimeoutObj && clearTimeout(this.serverTimeoutObj);
			return this;
		},
		// 开始发起心跳
		start: function() {
			let _self = this;
			// 超时3次才关闭
			let _retry = defaultConfig.heartbeartMaxWait;
			// 将两个超时值清空
			this.intervalObj && clearInterval(this.intervalObj);
			this.serverTimeoutObj && clearTimeout(this.serverTimeoutObj);
			// 定时间隔发送心跳消息
			this.intervalObj = setInterval(function() {
				// onmessage拿到返回的心跳就说明连接正常，就要清除定时器
				let heartBeatMessage = {
					from: loginIdentity,
					to: '',
					contentType: 3,
					content: '',
					qos: 0,
					createTime: new Date().getTime()
				}

				let {
					packetDataView
				} = wrapMessage(heartBeatMessage, 1, defaultConfig.deviceType,
					defaultConfig.networkType,
					0, defaultConfig.serializeAlgorithm);
				// 发送心跳信息
				console.debug(new Date() + " 客户端: " + loginIdentity + ' 正在发送心跳...')
				// 判断是原生还是uniapp
				if (defaultConfig.clientType === 1) {
					webSocket.send(packetDataView.buffer);
				}
				if (defaultConfig.clientType === 2) {
					webSocket.send({
						data: packetDataView.buffer
					});
				}
				// 次数减一
				_retry--;
				// 如果超过一定时间还没重置，说明后端主动断开了
				_self.serverTimeoutObj = setTimeout(function() {
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
		console.log('开始清除socket...');
		reconnect.clear();
		heartCheck.clear();
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
			// 客户端类型： 1-web,2-uniapp，...，默认web
			clientType: 1,
			// protobuf 序列化的消息类
			Message: null,
			//==================== 客户端消息发送方设置信息===================
			// 消息发送方设备类型，
			deviceType: 0,
			// 网络类型，具体什么类型可以查看后端设备枚举类型
			networkType: 0,
			// 消息加密算法类型，具体什么类型可以查看后端设备枚举类型
			encryptType: 0,
			// 序列化算法类型，具体什么类型可以查看后端设备枚举类型，注意：如果使用protobuf 序列化，需要引入google-protobuf.js 以及ouyunc-message.js 并放在同一个目录下
			serializeAlgorithm: 6,
			// 是否开启心跳，默认true，开启心跳
			heartbeatEnable: true,
			// 心跳最大等待次数（等待3个心跳时间，如果没有收到消息，则关闭客户端）
			heartbeartMaxWait: 3,
			// 心跳间隔时间10秒（并不一定10秒内会发一个心跳，如果10秒内都消息接收过来，则不会进行发送）
			heartbeartIntervalTime: 10000,
			// 心跳读超时时间（客户端发送心跳后，最大等待服务器回复是5秒，超过5秒则进行计数）
			heartbeartReadIdleTimeout: 5000,
			// 是否开启重连，默认true，开启重连, 手动关闭后不进行重连
			reconnectEnable: false,
			// 如果开启重连，重连的次数，默认-1，一直重连，请设置大于0的整数
			reconnectTimes: -1,
			// 重连延迟时间，默认 2s
			reconnectDelayTime: 2000
		}

	}


	/**
	 * 两个32位的整数
	 * @param {Object} left （高32位）
	 * @param {Object} right （低32位）
	 */
	function getUint64(left, right) {
		// 将 64 位整数值分成两份 32 位整数值
		// const left = dataview.getUint32(byteOffset);
		// const right = dataview.getUint32(byteOffset + 4);
		// 合并两个 32 位整数值
		let heigh32 = parseInt(left).toString(2).padStart(32, "0");
		let low32 = parseInt(right).toString(2).padStart(32, "0");
		return bigInt(heigh32 + low32, 2).toString(10);
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
		return (dataView.getInt8(3) & 0xFF) | ((dataView.getInt8(2) << 8) & 0xFF00) | ((dataView.getInt8(1) <<
			16) & 0xFF0000) | ((dataView.getInt8(0) << 24) & 0xFF000000);
	}

	// 将信息进行包装,这里的message 可能是个proto 或json 
	function wrapMessage(message, messageType, deviceType, networkType, encryptType,
		serializeAlgorithm) {
		// 现在封装两个比较常用的序列化，一个2-JSON，一个6-protobuf
		if (serializeAlgorithm !== 6 && serializeAlgorithm !== 2) {
			throw '目前该sdk只支持 protobuf以及json 序列化，如需要其他序列化，请联系ouyunc'
		}
		// 定义消息数据二进制
		let messageDataBinary;
		if (serializeAlgorithm === 6) {
			if (!defaultConfig.Message) {
				throw '请先在配置信息中设置protobuf 的Message'
			}
			let messageProto = new defaultConfig.Message();
			messageProto.setFrom(message.from);
			messageProto.setTo(message.to);
			messageProto.setContentType(message.contentType);
			messageProto.setContent(message.content);
			messageProto.setCreateTime(new Date().getTime());
			messageProto.setExtra(message.extra);
			messageProto.setQos(message.qos)
			messageDataBinary = messageProto.serializeBinary();
		}
		if (serializeAlgorithm === 2) {
			let messageDataAb = ENCODER.encode(JSON.stringify(message)).buffer;
			messageDataBinary = new Int8Array(messageDataAb);
		}

		// 消息id
		let snowflake = new Snowflake(1, 1, 0);
		// 这里得到的雪花id是二进制的雪花id
		let packetId = snowflake.nextBinaryIdStr();
		// 构建packet协议数据包&发送出去
		//定制协议前部固定长度
		let header = 20;
		// 消息字节长度
		let byteLength = messageDataBinary.byteLength;
		//总字节长度
		let len = header + byteLength;
		// 初始化Byte的二进制数据缓冲区
		let arrBuffer = new ArrayBuffer(len);
		// 加载分配好的缓冲区, 注意默认大端序读写
		let dataView = new DataView(arrBuffer);
		// 设置数据，魔数magic                                         // offset
		dataView.setInt8(0, 102); //  0
		//包协议， 1个字节
		dataView.setInt8(1, 1); // 0+1
		//协议版本号，1个字节
		dataView.setInt8(2, 1); // 0+1+1
		//协议包id 8个字节, 雪花id,(由于需要兼容其他浏览器如edge,ie 在低版本不支持bigint,所以需要将packetId 转成两个32位进行写入)
		// 截取前32位
		var heigh32 = packetId.substring(0, 32);
		// 截取后32位
		var low32 = packetId.substring(32, 64);
		dataView.setInt32(3, parseInt(heigh32, 2)); //0+1+1+1
		dataView.setInt32(7, parseInt(low32, 2)); //0+1+1+1+4

		//设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
		dataView.setInt8(11, deviceType); // 0+1+1+1+8
		//网络类型 1个字节， 其他， wifi,5g,4g,3g,2g
		dataView.setInt8(12, networkType); // 0+1+1+1+8+1
		//发送方ipv4 4个字节，这里ip需要客户端来获取ipv4并转成4个字节的int值
		//dataView.setInt32(13, ip2Int(ip)); // 0+1+1+1+8+1+1
		//消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密;这里暂时不加密
		dataView.setInt8(13, encryptType); // 0+1+1+1+8+1+1
		//序列化算法 1 个字节，protoBUf，采用protoStuf 的加密算法
		dataView.setInt8(14, serializeAlgorithm); // 0+1+1+1+8+1+1+1
		//消息类型 1 个字节，这里是登录消息，如 RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
		dataView.setInt8(15, messageType); // 0+1+1+1+8+1+1+1+1
		//判断是否需要加密，何种算法加密,这里先不加密
		//加密后的消息长度.4个字节
		dataView.setInt32(16, byteLength); //0+1+1+1+8+1+1+1+1+1
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
				"packetId": bigInt(packetId, 2).toString(10),
				"deviceType": deviceType,
				"networkType": networkType,
				"encryptType": encryptType,
				"serializeAlgorithm": serializeAlgorithm,
				"messageType": messageType,
				"messageLength": byteLength,
				"message": message
			}
		};
	}

	// 解析包数据
	function parseMessage(packetDataBinary) {
		// 注意：这里只要new Promise 就会执行，不需要手动调用
		return new Promise(function(resolve, reject) {
			//做一些异步操作
			//定制协议前部固定长度
			let header = 20;
			let packetBuffer = new DataView(packetDataBinary);
			//跳过魔数 1个字节
			let magic = packetBuffer.getInt8(0); // 0
			//包协议， 1个字节
			let protocol = packetBuffer.getInt8(1); // 0+1
			//协议版本号，1个字节
			let protocolVersion = packetBuffer.getInt8(2); // 0+1+1
			//协议包id 8个字节，由于要兼容其他浏览器，比如IE edge 等有些不支持bigint 需要做拆分处理
			const left = packetBuffer.getUint32(3); // 0+1+1+1
			const right = packetBuffer.getUint32(7); // 0+1+1+1+4
			let packetId = getUint64(left, right);
			//设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
			let deviceType = packetBuffer.getInt8(11); // 0+1+1+1+8
			//网络类型 1个字节 wifi,5g,4g,3g,2g...
			let networkType = packetBuffer.getInt8(12); // 0+1+1+1+8+1
			// 发送端ip 4个字节
			//let ip = packetBuffer.getInt32(13); // 0+1+1+1+8+1+1
			//消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密
			let encryptType = packetBuffer.getInt8(13); // 0+1+1+1+8+1+1+4
			//序列化算法 1 个字节，json/jdk/hessian/kryo/protoStuff(protoBUf)
			let serializeAlgorithm = packetBuffer.getInt8(14); // 0+1+1+1+8+1+1+4+1
			// 现在封装两个比较常用的序列化，一个2-JSON，一个6-protobuf
			if (serializeAlgorithm !== 6 && serializeAlgorithm !== 2) {
				throw '目前该sdk只支持 protobuf以及json 序列化，如需要其他序列化，请联系ouyunc'
			}
			//消息类型,1个字节
			let messageType = packetBuffer.getInt8(15); // 0+1+1+1+8+1+1+4+1+1
			//加密后的消息长度.4个字节
			let messageLength = packetBuffer.getInt32(16); // 0+1+1+1+8+1+1+4+1+1+1

			let messageAb = new ArrayBuffer(messageLength);
			let messageDV = new DataView(messageAb);
			for (let i = 0; i < messageLength; i++) {
				messageDV.setInt8(i, packetBuffer.getInt8(header + i));
			}
			// 定义message
			let message;
			// 定义客户端需要处理的消息
			if (serializeAlgorithm === 2) {
				message = JSON.parse(DECODER.decode(messageAb));
			}
			if (serializeAlgorithm === 6) {
				if (!defaultConfig.Message) {
					throw '请先在配置信息中设置protobuf 的Message'
					return;
				}
				message = defaultConfig.Message.deserializeBinary(messageAb);
				// 将message 转成JSON
				message = {
					// string
					"from": message.getFrom(),
					// string
					"to": message.getTo(),
					// byte
					"contentType": message.getContentType(),
					// string
					"content": message.getContent(),
					// int
					"qos": message.getQos(),
					// string
					"extra": message.getExtra(),
					// long
					"createTime": message.getCreateTime()
				}
			}

			// 将message result 返回
			resolve({
				messageId: packetId,
				messageType: messageType,
				message: message
			})

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
	Socket.prototype.send = function(packet, callback = () => {}) {
		let {
			message,
			messageType,
			deviceType = defaultConfig.deviceType,
			networkType = defaultConfig.networkType,
			encryptType = defaultConfig.encryptType,
			serializeAlgorithm = defaultConfig.serializeAlgorithm
		} = packet;

		// 组装message
		let {
			packetDataView,
			packetJson
		} = wrapMessage(message, messageType, deviceType, networkType, encryptType, serializeAlgorithm);
		if (!webSocket) {
			if (this.onerror instanceof Function) {
				this.onerror(packetJson);
			}
			throw 'webSocket实例不能为空！';
		}
		if (defaultConfig.clientType === 1) {
			webSocket.send(packetDataView.buffer);
		}
		if (defaultConfig.clientType === 2) {
			webSocket.send({
				data: packetDataView.buffer
			});
		}
		// 如果发送登录消息
		if (messageType === 2) {
			// 将该登录值存储起来
			loginIdentity = '' + JSON.parse(message.content).identity;
			// 登录时的 设备类型设置为默认类型，以便发送心跳时使用
			defaultConfig.deviceType = deviceType;
			defaultConfig.networkType = networkType;
			defaultConfig.encryptType = encryptType;
			defaultConfig.serializeAlgorithm = serializeAlgorithm;
			console.log(new Date() + ' 客户端: ' + loginIdentity + " 登录成功")
			// 开始启动心跳检测
			heartCheck.reset().start();
		}
		callback(packetJson);
	}


	/**
	 * 关闭websocket
	 */
	Socket.prototype.close = function() {
		console.log("webSocket正在关闭...")
		if (webSocket) {
			webSocket.close();
			clear();
		}
	}
	return Socket;
})(Snowflake, bigInt);

export default Socket;
