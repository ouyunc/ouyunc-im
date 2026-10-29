package com.ouyunc.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZkConfigFormatTest {

    @Test
    void blankDefaultsToYaml() {
        assertEquals(ZkConfigFormat.YAML, ZkConfigFormat.from(null));
        assertEquals(ZkConfigFormat.YAML, ZkConfigFormat.from(""));
        assertEquals(ZkConfigFormat.YAML, ZkConfigFormat.from("yaml"));
        assertEquals(ZkConfigFormat.YAML, ZkConfigFormat.from("YML"));
    }

    @Test
    void propertiesAliases() {
        assertEquals(ZkConfigFormat.PROPERTIES, ZkConfigFormat.from("properties"));
        assertEquals(ZkConfigFormat.PROPERTIES, ZkConfigFormat.from("props"));
        assertEquals(ZkConfigFormat.PROPERTIES, ZkConfigFormat.from("kv"));
    }

    @Test
    void unknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> ZkConfigFormat.from("json"));
    }
}
