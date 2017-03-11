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
package org.hellojavaer.fatjar.core.utils;

import org.hellojavaer.fatjar.core.FatJarClassLoader;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/03/2017.
 */
public class FatJarClassLoaderUtils {

    private static final ConcurrentHashMap<ClassLoader, FatJarClassLoader> injectRecords = new ConcurrentHashMap<>();

    public static void injectFatJarClassLoader() {
        injectFatJarClassLoader(FatJarClassLoaderUtils.class.getClassLoader());
    }

    public static void injectFatJarClassLoader(ClassLoader targetClassLoader) {
        try {
            ClassLoader parent = targetClassLoader.getParent();
            FatJarClassLoader fatJarClassLoader = new FatJarClassLoader(null, parent);
            if (injectRecords.putIfAbsent(targetClassLoader, fatJarClassLoader) == null) {
                Class clazz = ClassLoader.class;
                Field nameField = clazz.getDeclaredField("parent");
                nameField.setAccessible(true);
                nameField.set(targetClassLoader, fatJarClassLoader);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
