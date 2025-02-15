import App from './App'
import Socket from './common/js/ouyunc-message-sdk.js'

import './common/js/ouyunc-protobuf-message.js'
const socket = new Socket('ws://172.18.62.195:6003/ws', {
    clientType: 1,
    // 2-json序列化，6-protocolbuf 序列化，需要引入两个protobuf文件，且 属性 Message: proto.com.ouyunc.base.packet.message.Message,需要配置
	serializeAlgorithm: 6,
	deviceType: 1,
	Message: proto.com.ouyunc.base.packet.message.Message,
    heartbeat: {
        enable: true,
        interval: 5000
    }
});
socket.onopen = (e) => {
	let packet = socket.send({
            // 登录消息类型，
            messageType: 11,
            // 消息
            message: {
                // 消息发送方唯一标识，建议使用用户id
                from: '18888888888',
                // 消息接收方唯一标识，这里是登录，接受者是服务器，值可以为空串
                to: '',
                // 消息内容类型
                contentType: 10,
                // 消息内容content 是string 类型
                content: JSON.stringify({
                    identity: '18888888888',
                    appKey: 'ouyunc',
                    signatureAlgorithm: 1,//md5
                    signature: '123456',
                    createTime: new Date().getTime()
                })
            }
        });
	console.log(packet)
};
socket.onmessage = ({
                        messageId,
                        messageType,
                        message
                    }) => {
    console.log('messageId:', messageId);
	console.log('messageType:', messageType);
	console.log('message:', message);
};




// #ifndef VUE3
import Vue from 'vue'
import './uni.promisify.adaptor'
Vue.config.productionTip = false
App.mpType = 'app'
const app = new Vue({
  ...App
})
app.$mount()
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
export function createApp() {
  const app = createSSRApp(App)
  return {
    app
  }
}
// #endif