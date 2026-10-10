package com.ouyunc.config.nacos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NacosOpenApiTest {

    @Test
    void normalizesAddressAndContextPath() {
        assertEquals("http://127.0.0.1:8848", NacosOpenApi.normalizeBase("127.0.0.1:8848/"));
        assertEquals("https://nacos.local", NacosOpenApi.normalizeBase("https://nacos.local"));
        assertEquals("/nacos", NacosOpenApi.contextPath(null));
        assertEquals("", NacosOpenApi.contextPath("/"));
        assertEquals("/custom", NacosOpenApi.contextPath("custom/"));
        List<String> addresses = NacosOpenApi.splitAddresses("127.0.0.1:8848, 10.0.0.2:8848");
        assertEquals(List.of("http://127.0.0.1:8848", "http://10.0.0.2:8848"), addresses);
        assertEquals("http://nacos/v1/cs/configs?dataId=a&accessToken=***",
                NacosOpenApi.redact("http://nacos/v1/cs/configs?dataId=a&accessToken=secret"));
    }
}
