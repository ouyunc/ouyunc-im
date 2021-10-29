package com.ouyu.im;


import com.ouyu.im.processor.MessageProcessor;
import com.ouyu.im.utils.ClassUtil;

import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 偶遇im的启动类总入口
 * @Version V1.0
 **/
public class StartIMServer {

    public static void main(String[] args) {
        IMServer imServer = new StandardIMServer();
        new Thread(()-> {
            imServer.start();
        }).start();
    }


}
