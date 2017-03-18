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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * contains form direct jar, find from local, get from global
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/03/2017.
 */
public class FatJarClassLoaderProxy extends URLClassLoader {

    static final boolean            DEFAULT_DELEGATE           = true;
    static final boolean            DEFAULT_NESTED_DELEGATE    = true;

    private boolean                 delegate                   = DEFAULT_DELEGATE;
    private boolean                 nestedDelegate             = DEFAULT_NESTED_DELEGATE;
    private ClassLoader             child                      = null;

    private List<FatJarClassLoader> internalFatJarClassLoaders = new ArrayList<>();

    public FatJarClassLoaderProxy(URL[] urls, ClassLoader parent, ClassLoader child, boolean delegate,
                                  boolean nestedDelegate) {
        super(urls, parent);
        useSystemConfig();
        this.child = child;
        this.delegate = delegate;
        this.nestedDelegate = nestedDelegate;
        init();
    }

    public FatJarClassLoaderProxy(URL[] urls, ClassLoader parent, ClassLoader child, boolean delegate) {
        super(urls, parent);
        useSystemConfig();
        this.child = child;
        this.delegate = delegate;
        init();
    }

    public FatJarClassLoaderProxy(URL[] urls, ClassLoader parent, ClassLoader child) {
        super(urls, parent);
        useSystemConfig();
        this.child = child;
        init();
    }

    public FatJarClassLoaderProxy(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        useSystemConfig();
        init();
    }

    public FatJarClassLoaderProxy(URL[] urls) {
        super(urls);
        useSystemConfig();
        init();
    }

    private void useSystemConfig() {
        Boolean deleaget = FatJarSystemConfig.loadDelegate();
        if (deleaget != null) {
            this.delegate = deleaget;
        }
        Boolean nestedDelegate = FatJarSystemConfig.nestedLoadDelegate();
        if (nestedDelegate != null) {
            this.nestedDelegate = nestedDelegate;
        }
    }

    protected void init() {
        for (URL url : getURLs()) {
            initOneURL(url);
        }
    }

    protected void initOneURL(URL url) {
        List<File> jarFiles = listJarFiles(url);
        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                try {
                    JarFile jar = new JarFile(jarFile);
                    Manifest manifest = jar.getManifest();
                    if (isFatJar(manifest)) {
                        URL filePath = jarFile.getCanonicalFile().toURI().toURL();
                        internalFatJarClassLoaders.add(new FatJarClassLoader(jar, getParent(), child, nestedDelegate,
                                                                             filePath.toString()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private List<File> listJarFiles(URL url) {
        List<File> jarFiles = new ArrayList<>();
        File filePath = new File(url.getPath());
        listJarFiles0(jarFiles, filePath);
        return jarFiles;
    }

    private void listJarFiles0(List<File> jars, File file) {
        if (!file.exists() || !file.canRead()) {
            return;
        }
        if (file.isDirectory()) {
            if (file.getName().startsWith(".")) {
                return;
            } else {
                File[] list = file.listFiles();
                if (list != null) {
                    for (File item : list) {
                        listJarFiles0(jars, item);
                    }
                }
            }
        } else {
            if (file.getName().endsWith(".jar")) {
                jars.add(file);
            }
        }
    }

    protected ClassLoader getChild() {
        return child;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[child:");
        sb.append(child == null ? "null" : child.getClass().getName());
        sb.append("]->[");
        sb.append("own:");
        sb.append(super.getClass().getName());
        sb.append("]->[");
        sb.append("parent:");
        sb.append(getParent() == null ? "null" : getParent().getClass().getName());
        sb.append("]");
        return sb.toString();
    }

    @Override
    public URL findResource(String name) {
        for (FatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
            if (internalFatJarClassLoader.containsResource(name)) {
                URL url = internalFatJarClassLoader.findResource(name);
                if (url != null) {
                    return url;
                }
            }
        }
        return null;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        LinkedHashSet<URL> result = new LinkedHashSet<URL>();
        for (FatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
            if (internalFatJarClassLoader.containsResource(name)) {
                Enumeration<URL> enumeration = internalFatJarClassLoader.findResources(name);
                if (enumeration != null) {
                    while (enumeration.hasMoreElements()) {
                        result.add(enumeration.nextElement());
                    }
                }
            }
        }
        return Collections.enumeration(result);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (FatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
            if (internalFatJarClassLoader.containsResource(name)) {
                Class<?> clazz = internalFatJarClassLoader.findClass(name);
                if (clazz != null) {
                    return clazz;
                }
            }
        }
        return null;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = null;
        // 1. parent delegate
        if (delegate && getParent() != null) {
            try {
                clazz = Class.forName(name, false, getParent());
                if (clazz != null) {
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        // 2.
        for (FatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
            if (internalFatJarClassLoader.containsClass(name)) {
                try {
                    clazz = Class.forName(name, false, internalFatJarClassLoader);
                    if (clazz != null) {
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                    // ignore
                }
            }
        }
        // 3. parent delegate
        if (!delegate && getParent() != null) {
            try {
                clazz = Class.forName(name, false, getParent());
                if (clazz != null) {
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        //
        return null;
    }

    @Override
    public URL getResource(String name) {
        if (delegate && getParent() != null) {
            URL url = getParent().getResource(name);
            if (url != null) {
                return url;
            }
        }
        for (FatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
            if (internalFatJarClassLoader.containsResource(name)) {
                URL url = internalFatJarClassLoader.getResource(name);
                if (url != null) {
                    return url;
                }
            }
        }
        if (!delegate && getParent() != null) {
            URL url = getParent().getResource(name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        LinkedHashSet<URL> result = new LinkedHashSet<URL>();
        if (delegate && getParent() != null) {
            Enumeration<URL> enumeration = getParent().getResources(name);
            if (enumeration != null) {
                while (enumeration.hasMoreElements()) {
                    result.add(enumeration.nextElement());
                }
            }
        }
        for (FatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
            if (internalFatJarClassLoader.containsResource(name)) {
                Enumeration<URL> enumeration = internalFatJarClassLoader.findResources(name);
                if (enumeration != null) {
                    while (enumeration.hasMoreElements()) {
                        result.add(enumeration.nextElement());
                    }
                }
            }
        }
        if (!delegate && getParent() != null) {
            Enumeration<URL> enumeration = getParent().getResources(name);
            if (enumeration != null) {
                while (enumeration.hasMoreElements()) {
                    result.add(enumeration.nextElement());
                }
            }
        }
        return Collections.enumeration(result);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if (delegate && getParent() != null) {
            InputStream inputStream = getParent().getResourceAsStream(name);
            if (inputStream != null) {
                return inputStream;
            }
        }
        for (FatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
            if (internalFatJarClassLoader.containsResource(name)) {
                InputStream inputStream = internalFatJarClassLoader.getResourceAsStream(name);
                if (inputStream != null) {
                    return inputStream;
                }
            }
        }
        if (!delegate && getParent() != null) {
            InputStream inputStream = getParent().getResourceAsStream(name);
            if (inputStream != null) {
                return inputStream;
            }
        }
        return null;
    }

    @Override
    protected void addURL(URL url) {
        super.addURL(url);
        initOneURL(url);
    }

    static boolean isFatJar(Manifest manifest) {
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            String fatJarVersion = attributes.getValue("Fat-Jar-Version");
            if (fatJarVersion != null) {
                return true;
            }
        }
        return false;
    }
}
