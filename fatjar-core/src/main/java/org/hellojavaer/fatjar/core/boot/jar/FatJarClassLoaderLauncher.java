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
package org.hellojavaer.fatjar.core.boot.jar;

import org.hellojavaer.fatjar.core.FatJarClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 11/03/2017.
 */
public class FatJarClassLoaderLauncher {

    private static String START_CLASS_KEY = "Start-Class";

    public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException,
                                          InstantiationException, NoSuchMethodException, InvocationTargetException,
                                          IOException {
        URL url = getClassLocation(FatJarClassLoaderLauncher.class);
        File fatJarFile = new File(url.getPath());
        JarFile jar = new JarFile(fatJarFile);
        Manifest manifest = jar.getManifest();
        Attributes attributes = manifest.getMainAttributes();
        String startClass = attributes.getValue(START_CLASS_KEY);
        if (startClass == null || startClass.length() == 0) {
            throw new IllegalArgumentException(START_CLASS_KEY + " is missing");
        }
        FatJarClassLoader fatJarClassLoader = new FatJarClassLoader(null);
        Class<?> mainClazz = Class.forName(startClass, true, fatJarClassLoader);
        Object instance = mainClazz.newInstance();
        Method mainMethod = mainClazz.getMethod("main", String[].class);
        mainMethod.invoke(instance, args);
    }

    private static URL getClassLocation(Class clazz) {
        if (clazz == null) {
            return null;
        }
        String classPath = clazz.getName().replace('.', '/') + ".class";
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL locationURL = codeSource.getLocation();
            String location = locationURL.getPath();
            if (location.endsWith(classPath)) {
                try {
                    return new URL(location.substring(0, location.length() - classPath.length()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                return locationURL;
            }
        } else {
            return null;
        }
    }

}
