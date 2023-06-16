package com.ouyunc.im.serialize;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;

import java.lang.reflect.Type;

/**
 * 自定义fastjson2 long字段值转string
 */
public class Long2StringSerializer implements ObjectWriter<Long> {


    @Override
    public void write(JSONWriter jsonWriter, Object fieldValue, Object fieldName, Type fieldType, long features) {
        if(fieldValue==null){
            jsonWriter.writeNull();
            return;
        }
        jsonWriter.writeString(String.valueOf(fieldValue));
    }
}
