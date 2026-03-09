package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlResultTest {

    @Test
    void testBranchPatternField() {
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1",
                Collections.emptyList(), BranchPattern.ALL_SET, Map.of("name", "dummy"));
        assertEquals(BranchPattern.ALL_SET, result.getBranchPattern());
    }

    @Test
    void testParameterValuesField() {
        Map<String, Object> paramValues = Map.of("name", "dummy", "age", 1);
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1",
                Collections.emptyList(), BranchPattern.ALL_NULL, paramValues);
        assertEquals(paramValues, result.getParameterValues());
    }

    @Test
    void testNullBranchPatternForBackwardCompat() {
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1", Collections.emptyList());
        assertNull(result.getBranchPattern());
        assertNull(result.getParameterValues());
    }

    @Test
    void testToStringWithBranchPattern() {
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1",
                Collections.emptyList(), BranchPattern.ALL_SET, Map.of());
        String text = result.toString();
        assertTrue(text.contains("[ALL_SET]"));
    }

    @Test
    void testToStringWithoutBranchPattern() {
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1", Collections.emptyList());
        String text = result.toString();
        assertFalse(text.contains("[ALL_SET]"));
        assertFalse(text.contains("[ALL_NULL]"));
    }
}
