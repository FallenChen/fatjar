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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.security.CodeSource;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/03/2017.
 */
public class FatJarClassLoaderUtils {

    private static Logger  logger               = new Logger();

    private static boolean registeredURLHandler = false;

    static {
        FatJarTempFileManager.initTempFileDir();
    }

    public static ClassLoader injectFatJarClassLoaderProxy() {
        return injectFatJarClassLoaderProxy(FatJarClassLoaderUtils.class.getClassLoader());
    }

    public static ClassLoader injectFatJarClassLoaderProxy(ClassLoader targetClassLoader) {
        synchronized (targetClassLoader) {
            if (targetClassLoader instanceof FatJarClassLoaderProxy || targetClassLoader instanceof FatJarClassLoader) {
                if (logger.isWarnEnabled()) {
                    logger.warn("[FatJar] " + targetClassLoader + " can't be inject");
                }
                return null;
            } else {
                ClassLoader parent0 = targetClassLoader.getParent();
                if (parent0 != null
                    && (parent0 instanceof FatJarClassLoaderProxy || parent0 instanceof FatJarClassLoader)) {
                    if (logger.isWarnEnabled()) {
                        // StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
                        logger.warn("[FatJar] targetClassLoader:" + targetClassLoader + " has been injected:"
                                    + parent0.toString() + " .It can't been injected again.");
                    }
                    return parent0;
                }
            }
            //
            URL[] fatJarClassPaths = null;
            if (targetClassLoader instanceof URLClassLoader) {
                fatJarClassPaths = ((URLClassLoader) targetClassLoader).getURLs();
            } else {
                fatJarClassPaths = new URL[] { getBasePath(FatJarClassLoaderUtils.class) };
            }
            Boolean delegate = FatJarSystemConfig.loadDelegate();
            if (delegate == null) {
                delegate = FatJarClassLoaderProxy.DEFAULT_DELEGATE;
            }
            Boolean nestedDelegate = FatJarSystemConfig.nestedLoadDelegate();
            if (nestedDelegate == null) {
                nestedDelegate = FatJarClassLoaderProxy.DEFAULT_NESTED_DELEGATE;
            }
            //
            Boolean childDelegate = getDelegate(targetClassLoader);
            if (childDelegate != null) {
                nestedDelegate = childDelegate;
                if (logger.isInfoEnabled()) {
                    logger.info(String.format("[FatJar] use [nestedDelegate:%s] from %s", nestedDelegate,
                                              targetClassLoader.getClass()));
                }
                delegate = nestedDelegate;
            }
            FatJarClassLoaderProxy fatJarClassLoaderProxy = new FatJarClassLoaderProxy(fatJarClassPaths,
                                                                                       targetClassLoader.getParent(),
                                                                                       targetClassLoader, delegate,
                                                                                       nestedDelegate);
            replaceParent(targetClassLoader, fatJarClassLoaderProxy);
            if (logger.isInfoEnabled()) {
                logger.info(String.format("[FatJar] inject FatJarClassLoaderProxy child:%s,parent:%s,delegate:%s,nestedDelegate:%s} ]",
                                          targetClassLoader, targetClassLoader, delegate, nestedDelegate));
            }
            return fatJarClassLoaderProxy;
        }
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

    public static ClassLoader injectFatJarClassLoaderProxy(ClassLoader targetClassLoader, FatJarClassLoaderProxy parent) {
        loopCheck(targetClassLoader, parent);
        synchronized (targetClassLoader) {
            if (targetClassLoader instanceof FatJarClassLoaderProxy || targetClassLoader instanceof FatJarClassLoader) {
                if (logger.isWarnEnabled()) {
                    logger.warn("[FatJar] " + targetClassLoader + " can't be inject");
                }
                return null;
            } else {
                ClassLoader parent0 = targetClassLoader.getParent();
                if (parent0 != null
                    && (parent0 instanceof FatJarClassLoaderProxy || parent0 instanceof FatJarClassLoader)) {
                    if (logger.isWarnEnabled()) {
                        // StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
                        logger.warn("[FatJar] targetClassLoader:" + targetClassLoader + " has been injected:"
                                    + parent0.toString() + " .It can't been injected again.");
                    }
                    return parent0;
                }
            }
            //
            replaceParent(targetClassLoader, parent);
            return parent;
        }
    }

    public static ClassLoader injectFatJarClassLoader(ClassLoader targetClassLoader, FatJarClassLoader parent) {
        loopCheck(targetClassLoader, parent);
        synchronized (targetClassLoader) {
            if (targetClassLoader instanceof FatJarClassLoaderProxy || targetClassLoader instanceof FatJarClassLoader) {
                if (logger.isWarnEnabled()) {
                    logger.warn("[FatJar] " + targetClassLoader + " can't be inject");
                }
                return null;
            } else {
                ClassLoader parent0 = targetClassLoader.getParent();
                if (parent0 != null
                    && (parent0 instanceof FatJarClassLoaderProxy || parent0 instanceof FatJarClassLoader)) {
                    if (logger.isWarnEnabled()) {
                        // StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
                        logger.warn("[FatJar] targetClassLoader:" + targetClassLoader + " has been injected:"
                                    + parent0.toString() + " .It can't been injected again.");
                    }
                    return parent0;
                }
            }
            replaceParent(targetClassLoader, parent);
            return parent;
        }
    }

    private static void loopCheck(ClassLoader targetClassLoader, ClassLoader parent) {
        if (targetClassLoader != null && parent != null) {
            ClassLoader temp = parent;
            StringBuilder sb = new StringBuilder();
            while (temp != null) {
                sb.append(temp.getClass().getName());
                sb.append("->");
                if (temp == targetClassLoader) {
                    throw new IllegalArgumentException("parent list loop:" + sb + " ...");
                } else {
                    temp = temp.getParent();
                }
            }
        }
    }

    private static void replaceParent(ClassLoader targetClassLoader, ClassLoader newParent) {
        Class<?> clazz = targetClassLoader.getClass();
        while (clazz != null) {
            try {
                Field nameField = clazz.getDeclaredField("parent");
                nameField.setAccessible(true);
                nameField.set(targetClassLoader, newParent);
                return;
            } catch (Exception e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new IllegalStateException("Can't replace the parent of ClassLoader:"
                                        + targetClassLoader.getClass().getName());
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
                        logger.info("[FarJar] register UrlProtocolHandler success");
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
