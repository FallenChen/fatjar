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
package org.hellojavaer.fatjar.core.boot;

import org.hellojavaer.fatjar.core.FatJarClassLoader;
import org.hellojavaer.fatjar.core.FatJarClassLoaderUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 11/03/2017.
 */
public class Main {

    private static String START_CLASS_KEY = "Start-Class";

    static {
        System.out.println("INFO: [FatJar] -| Main is loaded by " + Main.class.getClassLoader());
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException,
                                          InvocationTargetException, IllegalAccessException {
        URL url = FatJarClassLoaderUtils.getLocatoin(Main.class);
        File fatJarFile = new File(url.getFile());
        JarFile jar = new JarFile(fatJarFile);
        Manifest manifest = jar.getManifest();
        Attributes attributes = manifest.getMainAttributes();
        String startClass = attributes.getValue(START_CLASS_KEY);
        if (startClass == null || startClass.length() == 0) {
            throw new IllegalArgumentException(START_CLASS_KEY + " is missing");
        }
        ClassLoader classLoader = Main.class.getClassLoader();
        FatJarClassLoader fatJarClassLoader = new FatJarClassLoader(jar, url, classLoader.getParent(), classLoader,
                                                                    false, true);
        ClassLoader classLoader1 = FatJarClassLoaderUtils.injectFatJarClassLoader(classLoader, fatJarClassLoader);
        Class<?> mainClazz = Class.forName(startClass, true, classLoader1);
        Method invokeMethod = mainClazz.getMethod("main", String[].class);
        invokeMethod.invoke(null, (Object) args);
    }
}
