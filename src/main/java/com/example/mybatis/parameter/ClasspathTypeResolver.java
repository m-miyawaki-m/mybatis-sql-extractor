package com.example.mybatis.parameter;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;

public class ClasspathTypeResolver {

    private ClassLoader classLoader;

    public ClasspathTypeResolver() {
        this.classLoader = getClass().getClassLoader();
    }

    public void setClasspath(List<String> classpathEntries) {
        try {
            URL[] urls = new URL[classpathEntries.size()];
            for (int i = 0; i < classpathEntries.size(); i++) {
                urls[i] = Path.of(classpathEntries.get(i)).toUri().toURL();
            }
            this.classLoader = new URLClassLoader(urls, getClass().getClassLoader());
        } catch (Exception e) {
            System.err.println("Warning: Failed to set classpath: " + e.getMessage());
        }
    }

    public Class<?> loadClass(String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public Map<String, Class<?>> resolveFields(Class<?> clazz) {
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        for (Method method : clazz.getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 0) continue;
            if (method.getDeclaringClass() == Object.class) continue;

            String propertyName = null;
            if (name.startsWith("get") && name.length() > 3) {
                propertyName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            } else if (name.startsWith("is") && name.length() > 2
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                propertyName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            }

            if (propertyName != null) {
                fields.put(propertyName, method.getReturnType());
            }
        }
        return fields;
    }

    public Map<String, Object> generateAllSetParams(Class<?> clazz) {
        Map<String, Class<?>> fields = resolveFields(clazz);
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> entry : fields.entrySet()) {
            params.put(entry.getKey(), dummyValueForType(entry.getValue()));
        }
        return params;
    }

    public static Object dummyValueForType(Class<?> type) {
        if (type == int.class || type == Integer.class) return 1;
        if (type == long.class || type == Long.class) return 1L;
        if (type == double.class || type == Double.class) return 1.0;
        if (type == float.class || type == Float.class) return 1.0f;
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == short.class || type == Short.class) return (short) 1;
        if (type == byte.class || type == Byte.class) return (byte) 1;
        if (type == String.class) return "dummy";
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return Arrays.asList(1, 2, 3);
        }
        if (type.isArray()) return new Object[]{1, 2, 3};
        return "dummy";
    }
}
