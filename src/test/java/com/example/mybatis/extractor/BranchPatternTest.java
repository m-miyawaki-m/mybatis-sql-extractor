package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BranchPatternTest {

    @Test
    void testEnumValues() {
        assertEquals(2, BranchPattern.values().length);
        assertNotNull(BranchPattern.ALL_SET);
        assertNotNull(BranchPattern.ALL_NULL);
    }

    @Test
    void testLabel() {
        assertEquals("ALL_SET", BranchPattern.ALL_SET.name());
        assertEquals("ALL_NULL", BranchPattern.ALL_NULL.name());
    }
}
