import App from './App'

// #ifndef VUE3
import Vue from 'vue'
import uView from '@/uni_modules/uview-ui'
import store from './store/index.js'
import request from '@/utils/request.js'


// #ifdef H5
// 提交前需要注释  本地调试使用
const vconsole = require('vconsole')
Vue.prototype.$vconsole = new vconsole() // 使用vconsole
// #endif

// 如果是H5引入录音组件
// #ifdef H5
import '@/common/js/record/voice-record.js';
import axios from 'axios'

Vue.prototype.$axios = axios;
// #endif




// 引入uview
Vue.use(uView)
// 将request 绑定到vue实例上
Vue.prototype.$request = request;
// 將socket绑定到实例上
Vue.prototype.$Socket = Socket;


// 引入消息message
import {Message} from '@/common/js/ouyunc-message.js';
// 引入 socket
import Socket from '@/common/js/ouyunc-im-client-uniapp-sdk.js';

import md5 from '@/common/js/md5.js';


// 绑定socket 的配置信息
Vue.prototype.$SocketConfig = {
    // socket 实例
    socket: null,
    // 连接url
    url: 'ws://10.30.1.49:9091',
    // 配置信息
    config: {
        //=========客户端类型设置===========
        // 客户端类型： 1-web,2-uniapp，...，默认web
        clientType: 2,
        // protobuf 中的Message 对象
        Message: Message,
        // ============公共消息发送方的设置==============
        // 消息发送方设备类型，可以根据不同的编译平台条件编译不同的设备类型
        deviceType: 22,
        // 消息发送方ip(也就是客户端ip)
        ip: '127.0.0.1',
        // 网络类型，具体什么类型可以查看后端设备枚举类型
        networkType: 0,
        // 消息加密算法类型，具体什么类型可以查看后端设备枚举类型
        encryptType: 0,
        // 序列化算法类型，具体什么类型可以查看序列化枚举类型，注意：如果使用protobuf 序列化，需要引入google-protobuf.js 以及ouyunc-message.js 并放在同一个目录下
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
    },
    onopen(e) {
        let currentLoginUser = uni.getStorageSync('current_login_user').userDetail;
        let appKey = 'ouyunc';
        let appSecret = '123456'
        let identity = currentLoginUser.id;
        let createTime = new Date().getTime();
        // 这里的this 指向socket
        // 登录
        this.send({
            // 登录消息类型，
            messageType: 2,
            // 消息
            message: {
                // 消息发送方唯一标识，建议使用用户id
                from: currentLoginUser.id,
                // 消息接收方唯一标识，这里是登录，接受者是服务器，值可以为空串
                to: '',
                // 消息内容类型
                contentType: 5,
                // 消息内容content 是string 类型
                content: JSON.stringify({
                    identity: identity,
                    appKey: appKey,
                    signatureAlgorithm: 1,//md5
                    signature: md5.hex_md5(appKey + '&' + identity + '&' + createTime + '_' + appSecret),
                    createTime: createTime
                })
            },
            // 下面的非必传，如果不传使用公共配置的配置参数
            // 消息发送方ip(也就是客户端ip)
            // ip:'127.0.0.1',
            // // 消息发送方设备类型
            // deviceType: 1,
            // // 网络类型
            // networkType: 0,
            // // 消息加密算法类型
            // encryptType: 0,
            // // 序列化算法类型
            // serializeAlgorithm: 2
        });
    },
    onmessage(e) {
        console.log(e)

        let {messageId, messageType, message} = e;
        // 远程登录登录
        if (messageType === 124 && message.contentType === 12) {
            // 关闭socket 并弹出弹框
            this.close();
            uni.showModal({
                title: '警告',
                confirmText: '去冻结',
                content: JSON.parse(message.content).notify,
                success: function (res) {
                    if (res.confirm) {
                        console.log('用户点击确定');
                        uni.reLaunch({
                            url: '/pages/public/login'
                        })
                    } else if (res.cancel) {
                        console.log('用户点击取消');
                        uni.reLaunch({
                            url: '/pages/public/login'
                        })
                    }
                }
            });
            return;
        }
        // 将
        uni.$emit('onmessage', e);
    },
    onerror(e) {
        console.log('mainjs' + e)

    },
    onclose(e) {
        console.log('mainjs' + e)

    }
}


Vue.config.productionTip = false
App.mpType = 'app'


try {
    function isPromise(obj) {
        return (
            !!obj &&
            (typeof obj === "object" || typeof obj === "function") &&
            typeof obj.then === "function"
        );
    }

    // 统一 vue2 API Promise 化返回格式与 vue3 保持一致
    uni.addInterceptor({
        returnValue(res) {
            if (!isPromise(res)) {
                return res;
            }
            return new Promise((resolve, reject) => {
                res.then((res) => {
                    if (res[0]) {
                        reject(res[0]);
                    } else {
                        resolve(res[1]);
                    }
                });
            });
        },
    });
} catch (error) {
}

const app = new Vue({
    store,
    ...App
})
app.$mount()
// #endif

// #ifdef VUE3
import {createSSRApp} from 'vue'

export function createApp() {
    const app = createSSRApp(App)
    return {
        app
    }
}

// #endif
