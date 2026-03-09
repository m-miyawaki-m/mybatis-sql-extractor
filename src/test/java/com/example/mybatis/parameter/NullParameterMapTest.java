package com.example.mybatis.parameter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NullParameterMapTest {

    @Test
    void testGetReturnsNull() {
        NullParameterMap map = new NullParameterMap();
        assertNull(map.get("name"));
        assertNull(map.get("anything"));
    }

    @Test
    void testContainsKeyReturnsTrue() {
        NullParameterMap map = new NullParameterMap();
        assertTrue(map.containsKey("name"));
        assertTrue(map.containsKey("nonexistent"));
    }

    @Test
    void testExplicitPutValueIsReturned() {
        NullParameterMap map = new NullParameterMap();
        map.put("key", "value");
        assertEquals("value", map.get("key"));
    }
}
