package com.example.mybatis.parameter;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DummyParameterGeneratorのテスト。
 */
class DummyParameterGeneratorTest {

    @Test
    void testIdParameter() {
        Object value = DummyParameterGenerator.generateDummyValue("userId");
        assertInstanceOf(Integer.class, value, "ID parameter should be Integer");
    }

    @Test
    void testListParameter() {
        Object value = DummyParameterGenerator.generateDummyValue("userList");
        assertInstanceOf(List.class, value, "List parameter should be a List");
        List<?> list = (List<?>) value;
        assertFalse(list.isEmpty(), "List should not be empty");
    }

    @Test
    void testIdsParameter() {
        Object value = DummyParameterGenerator.generateDummyValue("ids");
        assertInstanceOf(List.class, value, "ids parameter should be a List");
    }

    @Test
    void testBooleanParameter() {
        Object value = DummyParameterGenerator.generateDummyValue("isActive");
        assertInstanceOf(Boolean.class, value, "Boolean parameter should be Boolean");
        assertEquals(true, value);
    }

    @Test
    void testDateParameter() {
        Object value = DummyParameterGenerator.generateDummyValue("createdDate");
        assertInstanceOf(String.class, value, "Date parameter should be String");
        assertTrue(value.toString().contains("2024"), "Date should contain year");
    }

    @Test
    void testDefaultParameter() {
        Object value = DummyParameterGenerator.generateDummyValue("unknownParam");
        assertInstanceOf(String.class, value, "Unknown parameter should be String");
        assertTrue(value.toString().contains("dummy"), "Default value should contain 'dummy'");
    }

    @Test
    void testGenerateDummyMap() {
        Set<String> names = new HashSet<>(Arrays.asList("id", "name", "isActive", "itemList"));
        Map<String, Object> params = DummyParameterGenerator.generateDummyMap(names);

        assertEquals(4, params.size());
        assertNotNull(params.get("id"));
        assertNotNull(params.get("name"));
        assertNotNull(params.get("isActive"));
        assertNotNull(params.get("itemList"));
    }

    @Test
    void testEmptyParams() {
        Map<String, Object> params = DummyParameterGenerator.emptyParams();
        assertTrue(params.isEmpty());
    }

    @Test
    void testNumericParameters() {
        assertInstanceOf(Integer.class, DummyParameterGenerator.generateDummyValue("count"));
        assertInstanceOf(Integer.class, DummyParameterGenerator.generateDummyValue("amount"));
        assertInstanceOf(Integer.class, DummyParameterGenerator.generateDummyValue("pageSize"));
        assertInstanceOf(Integer.class, DummyParameterGenerator.generateDummyValue("offset"));
    }

    @Test
    void testCollectionParameter() {
        Object value = DummyParameterGenerator.generateDummyValue("collection");
        assertInstanceOf(List.class, value);
    }
}
