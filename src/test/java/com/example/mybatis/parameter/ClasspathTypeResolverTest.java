package com.example.mybatis.parameter;

import com.example.mybatis.testmodel.UserSearchParam;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClasspathTypeResolverTest {

    @Test
    void testResolveFieldsFromClass() {
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Map<String, Class<?>> fields = resolver.resolveFields(UserSearchParam.class);

        assertEquals(String.class, fields.get("name"));
        assertEquals(Integer.class, fields.get("age"));
        assertEquals(List.class, fields.get("ids"));
        assertEquals(boolean.class, fields.get("active"));
    }

    @Test
    void testGenerateTypedDummyValuesAllSet() {
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Map<String, Object> values = resolver.generateAllSetParams(UserSearchParam.class);

        assertNotNull(values.get("name"));
        assertInstanceOf(String.class, values.get("name"));
        assertNotNull(values.get("age"));
        assertInstanceOf(Integer.class, values.get("age"));
        assertNotNull(values.get("ids"));
        assertInstanceOf(List.class, values.get("ids"));
        assertNotNull(values.get("active"));
    }

    @Test
    void testGenerateDummyValueForType() {
        assertEquals(1, ClasspathTypeResolver.dummyValueForType(int.class));
        assertEquals(1, ClasspathTypeResolver.dummyValueForType(Integer.class));
        assertEquals(1L, ClasspathTypeResolver.dummyValueForType(Long.class));
        assertEquals(1L, ClasspathTypeResolver.dummyValueForType(long.class));
        assertInstanceOf(String.class, ClasspathTypeResolver.dummyValueForType(String.class));
        assertEquals(true, ClasspathTypeResolver.dummyValueForType(boolean.class));
        assertEquals(true, ClasspathTypeResolver.dummyValueForType(Boolean.class));
        assertInstanceOf(List.class, ClasspathTypeResolver.dummyValueForType(List.class));
    }

    @Test
    void testLoadClassFromClasspath() throws Exception {
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Class<?> clazz = resolver.loadClass("com.example.mybatis.testmodel.UserSearchParam");
        assertNotNull(clazz);
        assertEquals("UserSearchParam", clazz.getSimpleName());
    }

    @Test
    void testLoadClassFromExternalClasspath() throws Exception {
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Class<?> clazz = resolver.loadClass("java.lang.String");
        assertEquals(String.class, clazz);
    }

    @Test
    void testLoadClassNotFoundReturnsNull() {
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Class<?> clazz = resolver.loadClass("com.nonexistent.FakeClass");
        assertNull(clazz);
    }
}
