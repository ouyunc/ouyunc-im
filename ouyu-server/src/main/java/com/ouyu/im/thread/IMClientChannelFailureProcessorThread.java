package com.ouyu.im.thread;

import com.ouyu.im.innerclient.processor.AbstractIMClientChannelProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: 具体处理逻辑的线程
 * @Version V1.0
 **/
public class IMClientChannelFailureProcessorThread extends AbstractIMClientChannelProcessor{
    private static Logger log = LoggerFactory.getLogger(IMClientChannelFailureProcessorThread.class);

    /**
     *  消息
     */
    private String protocolBuf;

    /**
     *  服务地址
     */
    private InetSocketAddress toSocketAddress;

    public IMClientChannelFailureProcessorThread(String protocolBuf, InetSocketAddress toSocketAddress) {
        this.protocolBuf = protocolBuf;
        this.toSocketAddress = toSocketAddress;
    }

    @Override
    public void process() {

    }
}
