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

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandler;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/03/2017.
 */
public class FatJarClassLoaderUtils {

    private static Logger  logger                           = new Logger();

    private static boolean injectedFatJarUrlProtocolHandler = false;

    private static URL[]   systemClassPaths;

    static {
        if (logger.isDebugEnabled()) {
            logger.debug("FatJarClassLoaderUtils is loaded by " + FatJarClassLoaderUtils.class.getClassLoader());
        }
        //
        String classPath = System.getProperty("java.class.path");
        if (classPath != null) {
            String pathSeparator = System.getProperty("path.separator");
            String[] classPathArray = classPath.split(pathSeparator);
            List<URL> urls = new ArrayList<>();
            for (String item : classPathArray) {
                String filePath = item;
                if (item.equals(".")) {
                    filePath = System.getProperty("user.dir");
                }
                try {
                    File file = new File(filePath);
                    if (file.exists()) {
                        urls.add(file.toURI().toURL());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            systemClassPaths = urls.toArray(new URL[urls.size()]);
        }
        //
        FatJarTempFileManager.initTempFileDir();
    }

    public static ClassLoader injectFatJarClassLoaderProxy() {
        return injectFatJarClassLoaderProxy(FatJarClassLoaderUtils.class.getClassLoader());
    }

    public static ClassLoader injectFatJarClassLoaderProxy(ClassLoader targetClassLoader) {
        synchronized (targetClassLoader) {
            try {
                ClassLoader parent0 = securitCheck(targetClassLoader);
                if (parent0 != null) {
                    return parent0;
                }
            } catch (Exception e) {
                return null;
            }
            //
            URL[] fatJarClassPaths = null;
            if (targetClassLoader instanceof URLClassLoader) {
                fatJarClassPaths = ((URLClassLoader) targetClassLoader).getURLs();
            } else {
                // use system classpath
                fatJarClassPaths = systemClassPaths;
            }

            boolean delegate = false;
            boolean nestedDelegate = false;
            Boolean childDelegate = getAndResetDelegate(targetClassLoader);
            if (childDelegate != null) {
                nestedDelegate = childDelegate;
                delegate = childDelegate;
            }
            FatJarClassLoaderProxy fatJarClassLoaderProxy = new FatJarClassLoaderProxy(fatJarClassPaths,
                                                                                       targetClassLoader.getParent(),
                                                                                       targetClassLoader, delegate,
                                                                                       nestedDelegate);
            replaceParent(targetClassLoader, fatJarClassLoaderProxy);
            return fatJarClassLoaderProxy;
        }
    }

    private static Boolean getAndResetDelegate(ClassLoader classLoader) {
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
                    if (logger.isInfoEnabled() && b == false) {
                        logger.info("[getAndResetDelegate] In ClassLoader:[" + classLoader
                                    + "], the value of field delegate is false and has been set true");
                        if (logger.isTraceEnabled()) {
                            printStackTrace(Thread.currentThread().getStackTrace());
                        }
                    }
                    return b;
                }
            } catch (Exception e) {
                // ignore
            }
            try {
                Method getter = clazz.getDeclaredMethod("getDelegate");
                getter.setAccessible(true);
                Boolean b = (Boolean) getter.invoke(classLoader);
                if (b == Boolean.FALSE) {
                    Method setter = clazz.getDeclaredMethod("setDelegate");
                    setter.setAccessible(true);
                    setter.invoke(classLoader, Boolean.TRUE);
                }
                if (logger.isInfoEnabled() && b == false) {
                    logger.info("[getAndResetDelegate] In ClassLoader:[" + classLoader
                                + "], the value of method getDelegate is false and has been set true");
                    if (logger.isTraceEnabled()) {
                        printStackTrace(Thread.currentThread().getStackTrace());
                    }
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
            try {
                ClassLoader parent0 = securitCheck(targetClassLoader);
                if (parent0 != null) {
                    return parent0;
                }
            } catch (Exception e) {
                return null;
            }
            //
            replaceParent(targetClassLoader, parent);
            return parent;
        }
    }

    public static ClassLoader injectFatJarClassLoader(ClassLoader targetClassLoader, FatJarClassLoader parent) {
        loopCheck(targetClassLoader, parent);
        synchronized (targetClassLoader) {
            try {
                ClassLoader parent0 = securitCheck(targetClassLoader);
                if (parent0 != null) {
                    return parent0;
                }
            } catch (Exception e) {
                return null;
            }
            //
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

    private static ClassLoader securitCheck(ClassLoader targetClassLoader) {
        if (targetClassLoader instanceof FatJarClassLoaderProxy || targetClassLoader instanceof FatJarClassLoader) {
            if (logger.isInfoEnabled()) {
                logger.info("targetClassLoader:[" + targetClassLoader.getClass().getName() + "] can't be inject");
                if (logger.isTraceEnabled()) {
                    printStackTrace(Thread.currentThread().getStackTrace());
                }
            }
            throw new IllegalStateException(targetClassLoader + " can't be inject");
        } else {
            ClassLoader parent0 = targetClassLoader.getParent();
            if (parent0 != null && (parent0 instanceof FatJarClassLoaderProxy || parent0 instanceof FatJarClassLoader)) {
                if (logger.isInfoEnabled()) {
                    logger.info("targetClassLoader:[" + targetClassLoader + "] has been injected ClassLoader:["
                                + parent0.toString() + "]. It can't been injected again.");
                    if (logger.isTraceEnabled()) {
                        printStackTrace(Thread.currentThread().getStackTrace());
                    }
                }
                return parent0;
            }
        }
        return null;
    }

    private static void printStackTrace(StackTraceElement[] stackTraceElements) {
        System.out.println("[FatJar] stack trace:");
        if (stackTraceElements != null) {
            for (StackTraceElement stackTraceElement : stackTraceElements) {
                System.out.println(stackTraceElement);
            }
        }
        System.out.println();
    }

    private static void replaceParent(ClassLoader targetClassLoader, ClassLoader newParent) {
        Class<?> clazz = targetClassLoader.getClass();
        while (clazz != null) {
            try {
                ClassLoader oldParent = targetClassLoader.getParent();
                Field nameField = clazz.getDeclaredField("parent");
                nameField.setAccessible(true);
                nameField.set(targetClassLoader, newParent);
                if (logger.isInfoEnabled()) {
                    logger.info("[replaceParent] ClassLoader:[" + targetClassLoader + "]'s parent:[" + oldParent
                                + "] has been replaced with [" + newParent + "]");
                    if (logger.isTraceEnabled()) {
                        printStackTrace(Thread.currentThread().getStackTrace());
                    }
                }
                return;
            } catch (Exception e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new IllegalStateException("Can't replace the parent of ClassLoader:"
                                        + targetClassLoader.getClass().getName());
    }

    public static void injectFatJarUrlProtocolHandler() {
        if (!injectedFatJarUrlProtocolHandler) {
            synchronized (FatJarClassLoaderUtils.class) {
                if (!injectedFatJarUrlProtocolHandler) {
                    try {
                        Field fieldOfHandlers = URL.class.getDeclaredField("handlers");// 1
                        fieldOfHandlers.setAccessible(true);
                        Map handlers = (Map) fieldOfHandlers.get(URL.class);

                        Field fieldOfStreamHandlerLock = URL.class.getDeclaredField("streamHandlerLock");// 2
                        fieldOfStreamHandlerLock.setAccessible(true);
                        final Object lock = fieldOfStreamHandlerLock.get(URL.class);

                        synchronized (lock) {
                            URLStreamHandler olderUrlStreamHandler = (URLStreamHandler) handlers.get("jar");
                            FatJarURLStreamHandler fatJarURLStreamHandler = null;
                            if (olderUrlStreamHandler != null) {
                                if (olderUrlStreamHandler instanceof FatJarURLStreamHandler) {
                                    logger.info("[injectFatJarUrlProtocolHandler] FatJarUrlProtocolHandler already injected. Can't reapeted inject");
                                    if (logger.isTraceEnabled()) {
                                        printStackTrace(Thread.currentThread().getStackTrace());
                                    }
                                    return;
                                } else {
                                    fatJarURLStreamHandler = new FatJarURLStreamHandler(olderUrlStreamHandler);
                                }
                            } else {
                                Method methodOfGetURLStreamHandler = URL.class.getDeclaredMethod("getURLStreamHandler",
                                                                                                 String.class);// 3
                                methodOfGetURLStreamHandler.setAccessible(true);
                                URLStreamHandler urlStreamHandler = (URLStreamHandler) methodOfGetURLStreamHandler.invoke(URL.class,
                                                                                                                          "jar");
                                if (urlStreamHandler instanceof FatJarURLStreamHandler) {
                                    logger.info("[injectFatJarUrlProtocolHandler] FatJarUrlProtocolHandler already injected. Can't reapeted inject");
                                    if (logger.isTraceEnabled()) {
                                        printStackTrace(Thread.currentThread().getStackTrace());
                                    }
                                    return;
                                } else {
                                    fatJarURLStreamHandler = new FatJarURLStreamHandler(urlStreamHandler);
                                }
                            }
                            handlers.put("jar", fatJarURLStreamHandler);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("inject FatJarUrlProtocolHandler failed", e);
                    }
                    if (logger.isInfoEnabled()) {
                        logger.info("[injectFatJarUrlProtocolHandler] inject success");
                        if (logger.isTraceEnabled()) {
                            printStackTrace(Thread.currentThread().getStackTrace());
                        }
                    }
                    injectedFatJarUrlProtocolHandler = true;
                }
            }
        }
    }

    public static URL getLocatoin(Class clazz) {
        if (clazz == null) {
            return null;
        }
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            return codeSource.getLocation();
        } else {
            return null;
        }
    }

    public static URL getBaseLocatoin(Class clazz) {
        if (clazz == null) {
            return null;
        }
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL locationURL = codeSource.getLocation();
            String location = locationURL.toString();
            int i = location.indexOf("!/");
            if (i == -1) {
                return locationURL;
            } else {
                try {
                    return new URL(location.substring(0, i));
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            return null;
        }
    }

    public static URL getBaseDirecotry(Class clazz) {
        if (clazz == null) {
            return null;
        }
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL locationURL = codeSource.getLocation();
            String location = locationURL.toString();
            int i = location.indexOf("!/");
            if (i == -1) {
                if (location.toLowerCase().endsWith(".jar")) {
                    File file = new File(locationURL.getPath());
                    if (file.isFile()) {
                        try {
                            return new URL(location.substring(0, location.lastIndexOf("/")));
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        return locationURL;
                    }
                } else {
                    return locationURL;
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
