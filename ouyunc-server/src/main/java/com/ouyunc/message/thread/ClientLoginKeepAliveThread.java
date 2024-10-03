package com.ouyunc.message.thread;


import com.ouyunc.base.utils.TimeUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端登录保活线程，消费者
 */
public class ClientLoginKeepAliveThread implements Runnable{

    private static final ExecutorService loginKeepAliveexEcutorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 处理客户端登录保活
     */
    @Override
    public void run() {
        long lastBatchSubmitTimeMillis = TimeUtil.currentTimeMillis();
//        while (true) {
//            if (MessageServerContext.clientKeepAliveQueue.size() >= 1000 || (lastBatchSubmitTimeMillis >= lastBatchSubmitTimeMillis + 1000)) {
//                List<LoginContent> loginContentList = new ArrayList<>();
//                // 取出一千，有可能取不到，时间到了
//                for (int i = 0; i < 1000; i++) {
//                    LoginContent loginContent = MessageServerContext.clientKeepAliveQueue.poll();
//                }
//                // 多线程去执行操作redis
//                loginKeepAliveexEcutorService.submit(new Runnable() {
//                    @Override
//                    public void run() {
//                        MessageServerContext.remoteLoginClientInfoCache.batchExpire();
//                    }
//                });
//            }
        //}
    }
}
