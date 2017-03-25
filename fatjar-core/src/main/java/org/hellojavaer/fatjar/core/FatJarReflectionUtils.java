/*
 * Copyright 2017-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hellojavaer.fatjar.core;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 25/03/2017.
 */
class FatJarReflectionUtils {

    private static final Logger                          logger    = new Logger();

    private static final Map<Class, Map<String, Method>> methodMap = new HashMap<>();

    static {
        if (logger.isDebugEnabled()) {
            logger.debug("FatJarReflectionUtils is loaded by " + FatJarReflectionUtils.class.getClassLoader());
        }
    }

    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) {
            return null;
        }
        Map<String, Method> methods = methodMap.get(clazz);
        if (methods == null) {
            synchronized (methodMap) {
                methods = methodMap.get(clazz);
                if (methods == null) {
                    methods = new HashMap<>();
                    methodMap.put(clazz, methods);
                }
            }
        }
        Method method = methods.get(methodName);
        if (method == null) {
            synchronized (methods) {
                method = methods.get(methodName);
                if (method == null) {
                    Class<?> clazz0 = clazz;
                    while (clazz0 != null) {
                        Method temp = null;
                        try {
                            temp = clazz0.getDeclaredMethod(methodName, parameterTypes);
                            temp.setAccessible(true);
                            methods.put(methodName, temp);
                            method = temp;
                            break;
                        } catch (Exception e) {
                            clazz0 = clazz0.getSuperclass();
                            continue;
                        }
                    }
                }
            }
        }
        method.setAccessible(true);
        return method;
    }
}
