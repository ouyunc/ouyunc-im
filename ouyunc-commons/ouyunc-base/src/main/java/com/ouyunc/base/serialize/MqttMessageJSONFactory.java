package com.ouyunc.base.serialize;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.reader.ObjectReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.alibaba.fastjson2.writer.ObjectWriter;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;
import io.netty.handler.codec.mqtt.*;

import java.lang.reflect.Type;

/**
 * mqttMessage json 工厂
 */
public class MqttMessageJSONFactory {

    public static final JSONWriter.Context writeContext;

    public static final JSONReader.Context readerContext;

    // 初始化
    static {
        // write
        ObjectWriterProvider riterProvider = new ObjectWriterProvider();
        MqttMessageObjectWriter objectWriter = new MqttMessageObjectWriter();
        riterProvider.register(MqttMessage.class, objectWriter);
        riterProvider.register(MqttConnectMessage.class, objectWriter);
        riterProvider.register(MqttConnAckMessage.class, objectWriter);
        riterProvider.register(MqttPubAckMessage.class, objectWriter);
        riterProvider.register(MqttPublishMessage.class, objectWriter);
        riterProvider.register(MqttSubAckMessage.class, objectWriter);
        riterProvider.register(MqttSubscribeMessage.class, objectWriter);
        riterProvider.register(MqttUnsubAckMessage.class, objectWriter);
        riterProvider.register(MqttUnsubscribeMessage.class, objectWriter);
        writeContext = JSONFactory.createWriteContext(riterProvider);

        // reader
        ObjectReaderProvider readerProvider = new ObjectReaderProvider();
        MqttMessageObjectReader objectReader = new MqttMessageObjectReader();
        readerProvider.register(MqttMessage.class, objectReader);
        readerProvider.register(MqttConnectMessage.class, objectReader);
        readerProvider.register(MqttConnAckMessage.class, objectReader);
        readerProvider.register(MqttPubAckMessage.class, objectReader);
        readerProvider.register(MqttPublishMessage.class, objectReader);
        readerProvider.register(MqttSubAckMessage.class, objectReader);
        readerProvider.register(MqttSubscribeMessage.class, objectReader);
        readerProvider.register(MqttUnsubAckMessage.class, objectReader);
        readerProvider.register(MqttUnsubscribeMessage.class, objectReader);
        readerContext = JSONFactory.createReadContext(readerProvider);
    }


    static class MqttMessageObjectReader implements ObjectReader<MqttMessage> {
        /**
         * @ todo 反序列化MqttMessage
         */
        @Override
        public MqttMessage readObject(JSONReader jsonReader, Type fieldType, Object fieldName, long features) {
            if (jsonReader.nextIfNull()) {
                return null;
            }
            System.out.println(11);
            return null;
        }

    }

    static class MqttMessageObjectWriter implements ObjectWriter<MqttMessage> {

        /**
         * @ todo 序列化 MqttMessage
         */
        @Override
        public void write(JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
            if (object == null) {
                jsonWriter.writeNull();
                return;
            }
            System.out.println("write mqtt message");
        }
    }

}
