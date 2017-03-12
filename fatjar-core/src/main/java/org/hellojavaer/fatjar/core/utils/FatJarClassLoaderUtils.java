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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/03/2017.
 */
public class FatJarClassLoaderUtils {

    private static final Logger logger = LoggerFactory.getLogger(FatJarClassLoaderUtils.class);

    public static FatJarClassLoader injectFatJarClassLoader() {
        return injectFatJarClassLoader(FatJarClassLoaderUtils.class.getClassLoader());
    }

    public static FatJarClassLoader injectFatJarClassLoader(ClassLoader targetClassLoader) {
        URL[] urls = null;
        if (targetClassLoader instanceof URLClassLoader) {
            urls = ((URLClassLoader) targetClassLoader).getURLs();
        } else {
            urls = new URL[] { getClassLocation(FatJarClassLoaderUtils.class) };
        }
        return injectFatJarClassLoader(targetClassLoader, urls, false);
    }

    public static FatJarClassLoader injectFatJarClassLoader(ClassLoader targetClassLoader, URL[] jatJarClassPaths,boolean delegateLoad) {
        try {
            ClassLoader parent = targetClassLoader.getParent();
            FatJarClassLoader fatJarClassLoader = new FatJarClassLoader(jatJarClassPaths, parent, delegateLoad);
            Class clazz = ClassLoader.class;
            Field nameField = clazz.getDeclaredField("parent");
            nameField.setAccessible(true);
            nameField.set(targetClassLoader, fatJarClassLoader);
            return fatJarClassLoader;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
