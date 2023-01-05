package com.ouyunc.im;


import cn.hutool.json.JSONUtil;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;

/**
 * @Author fangzhenxun
 * @Description: 偶遇im的启动类总入口
 * @Version V3.0
 **/
public class StartIMServer {

    public static void main(String[] args) {
        IMServer imServer = new StandardIMServer();
        imServer.start();
    }



}
