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
        if (logger.isDebugEnabled()) {
            logger.debug("FatJarClassLoaderUtils is loaded by " + FatJarClassLoaderUtils.class.getClassLoader());
        }
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
                fatJarClassPaths = new URL[] { getBasePath(FatJarClassLoaderUtils.class) };
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

    public static void registerUrlProtocolHandler() {
        if (!registeredURLHandler) {
            synchronized (URL.class) {
                if (!registeredURLHandler) {
                    try {
                        URL.setURLStreamHandlerFactory(new FarJarURLStreamHandlerFactory());
                    } catch (final Error e) {
                        try {
                            Field factoryField = URL.class.getDeclaredField("factory");
                            factoryField.setAccessible(true);
                            URLStreamHandlerFactory old = (URLStreamHandlerFactory) factoryField.get(null);
                            factoryField.set(null, new FarJarURLStreamHandlerFactory(old));
                        } catch (IllegalAccessException e1) {
                            throw new RuntimeException(e1);
                        } catch (NoSuchFieldException e1) {
                            throw new RuntimeException(e1);
                        }
                    }
                    if (logger.isInfoEnabled()) {
                        logger.info("[registerUrlProtocolHandler] register success");
                        if (logger.isTraceEnabled()) {
                            printStackTrace(Thread.currentThread().getStackTrace());
                        }
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
