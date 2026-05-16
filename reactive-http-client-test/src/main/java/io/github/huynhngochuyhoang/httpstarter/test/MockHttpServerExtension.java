package io.github.huynhngochuyhoang.httpstarter.test;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * JUnit 5 extension that injects fresh {@link MockReactiveHttpClient} instances
 * into fields annotated with {@link MockHttpServer}.
 */
public final class MockHttpServerExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        Object testInstance = context.getRequiredTestInstance();
        for (Field field : annotatedFields(testInstance.getClass())) {
            inject(testInstance, field);
        }
    }

    private static List<Field> annotatedFields(Class<?> testClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = testClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(MockHttpServer.class)) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static void inject(Object testInstance, Field field) {
        if (!MockReactiveHttpClient.class.equals(field.getType())) {
            throw new ExtensionConfigurationException("@MockHttpServer fields must have type MockReactiveHttpClient<T>: "
                    + field);
        }

        Class<?> clientInterface = resolveClientInterface(field);
        MockReactiveHttpClient<?> mock = MockReactiveHttpClient.forClient(clientInterface).build();
        boolean accessible = field.canAccess(testInstance);
        try {
            field.setAccessible(true);
            field.set(testInstance, mock);
        } catch (IllegalAccessException ex) {
            throw new ExtensionConfigurationException("Failed to inject @MockHttpServer field: " + field, ex);
        } finally {
            field.setAccessible(accessible);
        }
    }

    private static Class<?> resolveClientInterface(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length == 1
                && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> clientInterface) {
            if (!clientInterface.isInterface()) {
                throw new ExtensionConfigurationException("@MockHttpServer generic type must be a client interface: "
                        + field);
            }
            return clientInterface;
        }
        throw new ExtensionConfigurationException("@MockHttpServer fields must declare MockReactiveHttpClient<T>: "
                + field);
    }
}
