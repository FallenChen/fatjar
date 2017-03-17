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
import java.lang.reflect.Method;
import java.net.MalformedURLException;
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

    public static void boot() {
        logger.info("|=========================================|\n|========== Fat-Jar is booting ===========|\n|=========================================|");
        injectFatJarClassLoader();
        registerUrlProtocolHandler();
        logger.info("|=========================================|\n|========== Fat-Jar boot success =========|\n|=========================================|");
    }

    public static FatJarClassLoader injectFatJarClassLoader() {
        return injectFatJarClassLoader(FatJarClassLoaderUtils.class.getClassLoader());
    }

    public static FatJarClassLoader injectFatJarClassLoader(ClassLoader targetClassLoader) {
        URL[] fatJarClassPaths = null;
        if (targetClassLoader instanceof URLClassLoader) {
            fatJarClassPaths = ((URLClassLoader) targetClassLoader).getURLs();
        } else {
            fatJarClassPaths = new URL[] { getBasePath(FatJarClassLoaderUtils.class) };
        }
        Boolean delegate = FatJarSystemConfig.loadDelegate();
        if (delegate == null) {
            delegate = FatJarClassLoader.DEFAULT_DELEGATE;
        }
        Boolean nestedDelegate = FatJarSystemConfig.nestedLoadDelegate();
        if (nestedDelegate == null) {
            nestedDelegate = FatJarClassLoader.DEFAULT_NESTED_DELEGATE;
        }
        //
        Boolean childDelegate = getDelegate(targetClassLoader);
        if (childDelegate != null) {
            nestedDelegate = childDelegate;
            if (logger.isInfoEnabled()) {
                logger.info("[FatJar] use [nestedDelegate:{}] from {}", nestedDelegate, targetClassLoader.getClass());
            }
            return injectFatJarClassLoader(targetClassLoader, fatJarClassPaths, nestedDelegate, nestedDelegate);
        } else {
            return injectFatJarClassLoader(targetClassLoader, fatJarClassPaths, delegate, nestedDelegate);
        }
    }

    public static FatJarClassLoader injectFatJarClassLoader(ClassLoader targetClassLoader, URL[] fatJarClassPaths,
                                                            boolean delegate) {
        Boolean nestedDelegate = FatJarSystemConfig.nestedLoadDelegate();
        if (nestedDelegate == null) {
            nestedDelegate = FatJarClassLoader.DEFAULT_NESTED_DELEGATE;
        }
        //
        Boolean childDelegate = getDelegate(targetClassLoader);
        if (childDelegate != null) {
            nestedDelegate = childDelegate;
            if (logger.isInfoEnabled()) {
                logger.info("[FatJar] use [nestedDelegate:{}] from {}", nestedDelegate, targetClassLoader.getClass());
            }
        }
        return injectFatJarClassLoader(targetClassLoader, fatJarClassPaths, delegate, nestedDelegate);
    }

    private static Boolean getDelegate(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        Class clazz = classLoader.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField("delegate");
                if (field != null) {
                    field.setAccessible(true);
                    boolean b = field.getBoolean(classLoader);
                    if (b == false) {
                        field.setBoolean(classLoader, true);
                    }
                    return b;
                }
            } catch (Exception e) {
                // ignore
            }
            try {
                Method method = clazz.getDeclaredMethod("getDelegate");
                method.setAccessible(true);
                Boolean b = (Boolean) method.invoke(classLoader);
                if (b == Boolean.FALSE) {
                    method.invoke(classLoader, Boolean.TRUE);
                }
                return b;
            } catch (Exception e) {
                // ignore
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    public static synchronized FatJarClassLoader injectFatJarClassLoader(ClassLoader targetClassLoader,
                                                                         URL[] fatJarClassPaths, boolean delegate,
                                                                         boolean nestedDelegate) {
        FatJarTempFileManager.initTempFileDir();
        FatJarClassLoader fatJarClassLoader = classLoaderMap.get(targetClassLoader);
        if (fatJarClassLoader != null) {
            return fatJarClassLoader;
        } else {
            try {
                fatJarClassLoader = new FatJarClassLoader(fatJarClassPaths, targetClassLoader.getParent(),
                                                          targetClassLoader, delegate, nestedDelegate);
                // replace parent
                Class<?> clazz = targetClassLoader.getClass();
                while (clazz != null) {
                    try {
                        Field nameField = clazz.getDeclaredField("parent");
                        nameField.setAccessible(true);
                        nameField.set(targetClassLoader, fatJarClassLoader);
                        break;
                    } catch (Exception e) {
                        clazz = clazz.getSuperclass();
                    }
                }
                classLoaderMap.put(targetClassLoader, fatJarClassLoader);
                if (logger.isInfoEnabled()) {
                    logger.info("[FatJar] FatJarClassLoaderUtils.injectFatJarClassLoader child:{},parent:{},delegate:{},nestedDelegate:{}} ]",
                                targetClassLoader.toString(), targetClassLoader.toString(), delegate, nestedDelegate);
                }
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
                    if (logger.isInfoEnabled()) {
                        logger.info("[FarJar] FatJarClassLoaderUtils.registerUrlProtocolHandler success");
                    }
                    registeredURLHandler = true;
                }
            }
        }
    }

    public static URL getBasePath(Class clazz) {
        if (clazz == null) {
            return null;
        }
        String classPath = clazz.getName().replace('.', '/') + ".class";
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL locationURL = codeSource.getLocation();
            String location = locationURL.toString();
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

    public static URL getBaseDirectry(Class clazz) {
        if (clazz == null) {
            return null;
        }
        String classPath = clazz.getName().replace('.', '/') + ".class";
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL locationURL = codeSource.getLocation();
            String location = locationURL.toString();
            int i = location.indexOf("!/");
            if (i == -1) {
                if (location.endsWith(classPath)) {
                    try {
                        return new URL(location.substring(0, location.length() - classPath.length()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    if (location.endsWith(".jar")) {
                        try {
                            return new URL(location.substring(0, location.lastIndexOf("/")));
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        return locationURL;
                    }
                }
            } else {
                try {
                    return new URL(location.substring(0, location.lastIndexOf("/", i - 1)));
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            return null;
        }
    }

}
