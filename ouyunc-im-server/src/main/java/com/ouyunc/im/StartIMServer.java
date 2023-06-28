package com.ouyunc.im;

import com.ouyunc.im.processor.MessageProcessor;
import com.ouyunc.im.utils.ClassScanner;

/**
 * @Author fangzhenxun
 * @Description: 偶遇im的启动类总入口
 **/
public class StartIMServer {

    public static void main(String[] args) {
        IMServer imServer = new StandardIMServer();
        try {
            ClassScanner.scanPackageBySuper(MessageProcessor.class.getPackage().getName(), MessageProcessor.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        imServer.start();
    }



}
