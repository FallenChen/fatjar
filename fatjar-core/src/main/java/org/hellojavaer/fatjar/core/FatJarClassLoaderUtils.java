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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/03/2017.
 */
public class FatJarClassLoaderUtils {

    private static final Map<ClassLoader, FatJarClassLoader> classLoaderMap       = new HashMap<>();

    private static Logger                                    logger               = LoggerFactory.getLogger(FatJarClassLoaderUtils.class);

    private static boolean                                   registeredURLHandler = false;

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
        Boolean delegate = FatJarSystemConfig.isLoadDelegate();
        if (delegate == null) {
            delegate = false;
        }
        return injectFatJarClassLoader(targetClassLoader, urls, delegate);
    }

    public static synchronized FatJarClassLoader injectFatJarClassLoader(ClassLoader targetClassLoader,
                                                                         URL[] fatJarClassPaths, boolean delegate) {
        FatJarClassLoader fatJarClassLoader = classLoaderMap.get(targetClassLoader);
        if (fatJarClassLoader != null) {
            return fatJarClassLoader;
        } else {
            try {
                logger.info(new StringBuilder(
                                              "[FatJar] FatJarClassLoaderUtils.injectFatJarClassLoader targetClassLoader:{class:")//
                .append(targetClassLoader.getClass())//
                .append("},fatJarClassLoader:{delegateLoad:").append(delegate)//
                .append("} ]")//
                .toString());
                ClassLoader parent = targetClassLoader.getParent();
                fatJarClassLoader = new FatJarClassLoader(fatJarClassPaths, parent, targetClassLoader, delegate);
                Class clazz = ClassLoader.class;
                Field nameField = clazz.getDeclaredField("parent");
                nameField.setAccessible(true);
                nameField.set(targetClassLoader, fatJarClassLoader);
                return fatJarClassLoader;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void registerUrlProtocolHandler() {

        if (!registeredURLHandler) {
            synchronized (FatJarClassLoaderUtils.class) {
                if (!registeredURLHandler) {
                    try {
                        URL.setURLStreamHandlerFactory(new FarJarURLStreamHandlerFactory());
                    } catch (final Error e) {
                        try {
                            Field factoryField = URL.class.getDeclaredField("factory");
                            factoryField.setAccessible(true);
                            URLStreamHandlerFactory old = (URLStreamHandlerFactory) factoryField.get(null);
                            factoryField.set(null, new FarJarURLStreamHandlerFactory(old));
                        } catch (NoSuchFieldException e0) {
                            throw new Error("Could not access factory field on URL class", e0);
                        } catch (IllegalAccessException e1) {
                            throw new Error("Could not access factory field on URL class", e1);
                        }
                    }
                    logger.info("[FarJar] FatJarClassLoaderUtils.registerUrlProtocolHandler success");
                    registeredURLHandler = true;
                }
            }
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
