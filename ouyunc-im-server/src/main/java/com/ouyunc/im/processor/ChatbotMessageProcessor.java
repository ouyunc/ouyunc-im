package com.ouyunc.im.processor;

import com.ouyunc.im.packet.Packet;

public interface ChatbotMessageProcessor {



    default boolean match(Packet packet) {
        return true;
    }



    void process(Packet packet);
}
