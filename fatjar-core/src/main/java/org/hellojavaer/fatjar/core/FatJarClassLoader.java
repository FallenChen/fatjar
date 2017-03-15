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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.security.CodeSource;
import java.security.Policy;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * contains form direct jar, find from local, get from global
 * 
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/03/2017.
 */
public class FatJarClassLoader extends URLClassLoader {

    private static Logger                   logger                     = LoggerFactory.getLogger(FatJarClassLoader.class);

    private static ClassLoader              j2seClassLoader            = null;
    private static SecurityManager          securityManager            = null;
    private static Method                   FIND_CLASS_METHOD          = null;
    private static Method                   FIND_RESOURCE_METHOD       = null;

    private ClassLoader                     child                      = null;

    static final boolean                    DEFAULT_DELEGATE           = true;
    static final boolean                    DEFAULT_NESTED_DELEGATE    = true;

    private boolean                         delegate                   = DEFAULT_DELEGATE;
    private boolean                         nestedDelegate             = DEFAULT_NESTED_DELEGATE;

    private List<InternalFatJarClassLoader> internalFatJarClassLoaders = new ArrayList<>();

    static {
        //
        ClassLoader cl = String.class.getClassLoader();
        if (cl == null) {
            cl = getSystemClassLoader();
            while (cl.getParent() != null) {
                cl = cl.getParent();
            }
        }
        j2seClassLoader = cl;
        //
        securityManager = System.getSecurityManager();
        if (securityManager != null) {
            try {
                Policy policy = Policy.getPolicy();
                policy.refresh();
            } catch (AccessControlException e) {
                // ignore
            }
        }
        //
        try {
            FIND_CLASS_METHOD = ClassLoader.class.getDeclaredMethod("findClass", String.class);
            FIND_CLASS_METHOD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //
        try {
            FIND_RESOURCE_METHOD = ClassLoader.class.getDeclaredMethod("findResource", String.class);
            FIND_RESOURCE_METHOD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public FatJarClassLoader(URL[] urls, ClassLoader parent, ClassLoader child, boolean delegate, boolean nestedDelegate) {
        super(urls, parent);
        useSystemConfig();
        this.child = child;
        this.delegate = delegate;
        this.nestedDelegate = nestedDelegate;
        init();
    }

    public FatJarClassLoader(URL[] urls, ClassLoader parent, ClassLoader child, boolean delegate) {
        super(urls, parent);
        useSystemConfig();
        this.child = child;
        this.delegate = delegate;
        init();
    }

    public FatJarClassLoader(URL[] urls, ClassLoader parent, ClassLoader child) {
        super(urls, parent);
        useSystemConfig();
        this.child = child;
        init();
    }

    public FatJarClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        useSystemConfig();
        init();
    }

    public FatJarClassLoader(URL[] urls) {
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
                        internalFatJarClassLoaders.add(new InternalFatJarClassLoader(jar, getParent(), child,
                                                                                     nestedDelegate,
                                                                                     filePath.toString()));
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
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

    protected boolean isFatJar(Manifest manifest) {
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            String fatJarVersion = attributes.getValue("Fat-Jar-Version");
            if (fatJarVersion != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public URL findResource(String name) {
        for (InternalFatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
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
        for (InternalFatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
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
        for (InternalFatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
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
        for (InternalFatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
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
        for (InternalFatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
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
        for (InternalFatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
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
        for (InternalFatJarClassLoader internalFatJarClassLoader : internalFatJarClassLoaders) {
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

    class InternalFatJarClassLoader extends URLClassLoader {

        private static final String               JAR_PROTOCOL      = "jar:";
        private static final String               CLASSS_SUBFIX     = ".class";
        private static final String               SEPARATOR         = "!/";

        private boolean                           delegate          = true;

        private JarFile                           fatJar;
        private Map<String, JarFile>              dependencyJars    = new LinkedHashMap<>();

        private List<InternalFatJarClassLoader>   subClassLoaders   = new ArrayList<>();

        private Map<String, ResourceEntry>        loadedResources   = new HashMap<>();
        private Set<String>                       notFoundResources = new HashSet<>();

        private String                            basePath          = "";

        private ConcurrentHashMap<String, Object> lockMap           = new ConcurrentHashMap<>();

        private URL                               fatJarURL;

        private InternalFatJarClassLoader(JarFile fatJar, ClassLoader parent, ClassLoader child, boolean delegate,
                                          String basePath) {
            super(new URL[0], parent);
            this.fatJar = fatJar;
            this.basePath = basePath;
            this.delegate = delegate;
            try {
                this.fatJarURL = new URL(basePath);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            Enumeration<JarEntry> jarEntries = fatJar.entries();
            if (jarEntries != null) {
                while (jarEntries.hasMoreElements()) {
                    JarEntry jarEntry = jarEntries.nextElement();
                    if (isDependencyLib(jarEntry)) {
                        try {
                            InputStream inputStream = fatJar.getInputStream(jarEntry);
                            String nextPrefix = basePath + SEPARATOR + jarEntry.getName();
                            JarFile subJarFile = FatJarTempFileManager.buildJarFile(nextPrefix, jarEntry.getName(),
                                                                                    inputStream);
                            Manifest manifest = subJarFile.getManifest();
                            if (isFatJar(manifest)) {
                                subClassLoaders.add(new InternalFatJarClassLoader(subJarFile, parent, child, delegate,
                                                                                  nextPrefix));
                            } else {
                                dependencyJars.put(jarEntry.getName(), subJarFile);
                            }
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }// else ignore
                }
            }
        }

        private boolean isDependencyLib(JarEntry jarEntry) {
            String name = jarEntry.getName();
            if (!jarEntry.isDirectory() && name.endsWith(".jar")) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            return findClassInternal(name);
        }

        @Override
        public URL findResource(String name) {
            synchronized (getLockObject(name)) {
                ResourceEntry resource = findResourceInternal(name, name);
                return resource.getUrl();
            }
        }

        @Override
        public Enumeration<URL> findResources(String name) throws IOException {
            return super.findResources(name);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return loadClass(name, false);
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> clazz = null;
            synchronized (getLockObject(name)) {
                // 0. find in local cache
                ResourceEntry resource = loadedResources.get(name);
                if (resource != null) {
                    clazz = resource.getClazz();
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }

                // 1. load by j2se
                try {
                    clazz = j2seClassLoader.loadClass(name);
                    if (clazz != null) {
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                    // ignore;
                }

                // 2.0 load from local resources
                try {
                    clazz = findClassInternal(name);
                    if (clazz != null) {
                        if (resolve) {
                            resolveClass(clazz);
                        }
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                    // ignore
                }
                // 2.1 load by sub-classload which will recursive find in fat-jar
                if (subClassLoaders != null) {
                    for (InternalFatJarClassLoader fatJarClassLoader : subClassLoaders) {
                        if (fatJarClassLoader.containsClass(name)) {
                            try {
                                clazz = Class.forName(name, false, fatJarClassLoader);
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
                }

                // 3.0
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
                // 3.1
                if (child != null) {
                    try {
                        clazz = (Class<?>) FIND_CLASS_METHOD.invoke(child, name);
                        if (clazz != null) {
                            if (resolve) {
                                resolveClass(clazz);
                            }
                            return clazz;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                // 3.2
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
        }

        //
        @Override
        public synchronized URL getResource(String name) {
            synchronized (getLockObject(name)) {
                // 0. find in local cache
                ResourceEntry resource = loadedResources.get(name);
                if (resource != null) {
                    return resource.getUrl();
                }

                // 1. load by j2se
                URL url = j2seClassLoader.getResource(name);
                if (url != null) {
                    return url;
                }

                // 2.
                ResourceEntry resourceEntry = findResourceInternal(name, name);
                if (resourceEntry != null) {
                    return resourceEntry.getUrl();
                }

                // 3.0 parent delegate
                if (delegate && getParent() != null) {
                    url = getParent().getResource(name);
                    if (url != null) {
                        return url;
                    }
                }
                // 3.1
                if (child != null) {
                    try {
                        url = (URL) child.getResource(name);
                        if (url != null) {
                            return url;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                // 3.2
                if (!delegate && getParent() != null) {
                    url = getParent().getResource(name);
                    if (url != null) {
                        return url;
                    }
                }
                //
                return null;
            }
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            LinkedHashSet result = new LinkedHashSet();
            synchronized (getLockObject(name)) {
                // 1. load by j2se
                URL url = j2seClassLoader.getResource(name);
                if (url != null) {
                    result.add(url);
                }

                // 2.
                ResourceEntry resourceEntry = findResourceInternal(name, name);
                if (resourceEntry != null) {
                    result.add(resourceEntry.getUrl());
                }

                // 3.0 parent delegate
                if (delegate && getParent() != null) {
                    Enumeration<URL> enumeration = getParent().getResources(name);
                    if (enumeration != null) {
                        while (enumeration.hasMoreElements()) {
                            result.add(enumeration.nextElement());
                        }
                    }
                }
                // 3.1
                if (child != null) {
                    try {
                        Enumeration<URL> enumeration = child.getResources(name);
                        if (enumeration != null) {
                            while (enumeration.hasMoreElements()) {
                                result.add(enumeration.nextElement());
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                // 3.2
                if (!delegate && getParent() != null) {
                    Enumeration<URL> enumeration = getParent().getResources(name);
                    if (enumeration != null) {
                        while (enumeration.hasMoreElements()) {
                            result.add(enumeration.nextElement());
                        }
                    }
                }
                //
                return Collections.enumeration(result);
            }
        }

        @Override
        public synchronized InputStream getResourceAsStream(String name) {
            synchronized (getLockObject(name)) {
                // 0. find in local cache
                ResourceEntry resource = loadedResources.get(name);
                if (resource != null) {
                    return new ByteArrayInputStream(resource.getBytes());
                }

                // 1. load by j2se
                InputStream inputStream = j2seClassLoader.getResourceAsStream(name);
                if (inputStream != null) {
                    return inputStream;
                }

                // 2.
                ResourceEntry resourceEntry = findResourceInternal(name, name);
                if (resourceEntry != null) {
                    return new ByteArrayInputStream(resource.getBytes());
                }

                // 3.0 parent delegate
                if (delegate && getParent() != null) {
                    inputStream = getParent().getResourceAsStream(name);
                    if (inputStream != null) {
                        return inputStream;
                    }
                }

                // 3.1
                if (child != null) {
                    try {
                        URL url = (URL) FIND_RESOURCE_METHOD.invoke(child, name);
                        if (url != null) {
                            inputStream = url.openStream();
                            if (inputStream != null) {
                                return inputStream;
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                // 3.2 parent delegate
                if (!delegate && getParent() != null) {
                    inputStream = getParent().getResourceAsStream(name);
                    if (inputStream != null) {
                        return inputStream;
                    }
                }
                //
                return null;
            }
        }

        @Override
        protected void addURL(URL url) {
            throw new UnsupportedOperationException("addURL");
        }

        protected boolean containsClass(String name) {
            String path = name.replace('.', '/') + CLASSS_SUBFIX;
            return containsResource(path);
        }

        protected boolean containsResource(String name) {
            if (fatJar != null && fatJar.getJarEntry(name) != null) {
                return true;
            } else {
                return false;
            }
        }

        protected synchronized Class<?> findClassInternal(String name) throws ClassNotFoundException {
            ResourceEntry resource = loadedResources.get(name);
            if (resource != null) {
                return resource.getClazz();
            }
            String path = name.replace('.', '/') + CLASSS_SUBFIX;
            resource = findResourceInternal(name, path);
            if (resource == null) {
                return null;
            } else {
                String packageName = null;
                int pos = name.lastIndexOf('.');
                if (pos >= 0) {
                    packageName = name.substring(0, pos);
                    if (securityManager != null) {
                        try {
                            securityManager.checkPackageAccess(packageName);
                        } catch (SecurityException se) {
                            String error = "Security Violation, attempt to use Restricted Class: " + name;
                            throw new ClassNotFoundException(error, se);
                        }
                    }
                }
                Package pkg = null;
                if (packageName != null) {
                    pkg = getPackage(packageName);
                    if (pkg == null) {
                        try {
                            if (resource.getManifest() == null) {
                                definePackage(packageName, null, null, null, null, null, null, null);
                            } else {
                                definePackage(packageName, resource.getManifest(), resource.getUrl());
                            }
                        } catch (IllegalArgumentException e) {
                        }
                        pkg = getPackage(packageName);
                    }
                }
                CodeSource codeSource = null;
                if (resource.getNestedJarEntryName() == null) {
                    codeSource = new CodeSource(fatJarURL, resource.getCertificates());
                } else {
                    try {
                        codeSource = new CodeSource(new URL(fatJarURL.toString() + SEPARATOR
                                                            + resource.getNestedJarEntryName()),
                                                    resource.getCertificates());
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }
                Class clazz = defineClass(name, resource.getBytes(), 0, resource.getBytes().length, codeSource);
                resource.setClazz(clazz);
                return clazz;
            }
        }

        protected ResourceEntry findResourceInternal(String name, String path) {
            if (notFoundResources.contains(name)) {
                return null;
            }
            if (this.fatJar != null) {
                ResourceEntry resource = findResourceInternal0(this.fatJar, name, path, null);
                if (resource != null) {
                    return resource;
                }
            }
            if (this.dependencyJars != null) {
                for (Map.Entry<String, JarFile> entry : this.dependencyJars.entrySet()) {
                    ResourceEntry resource = findResourceInternal0(entry.getValue(), name, path, entry.getKey());
                    if (resource != null) {
                        return resource;
                    }
                }
            }
            notFoundResources.add(name);
            return null;
        }

        protected ResourceEntry findResourceInternal0(JarFile jarFile, String name, String path, String nestedJar) {
            JarEntry jarEntry = jarFile.getJarEntry(path);
            if (jarEntry == null) {
                return null;
            } else {
                ResourceEntry resource = new ResourceEntry();
                InputStream inputStream = null;
                try {
                    inputStream = jarFile.getInputStream(jarEntry);
                    int conentLength = (int) jarEntry.getSize();
                    byte[] bytes = new byte[(int) conentLength];
                    int pos = 0;
                    while (true) {
                        int next = inputStream.read(bytes, pos, bytes.length - pos);
                        if (next <= next) {
                            break;
                        }
                        pos += next;
                    }
                    resource.setBytes(bytes);
                    resource.setManifest(jarFile.getManifest());
                    resource.setCertificates(jarEntry.getCertificates());
                    if (nestedJar == null) {
                        resource.setUrl(new URL(JAR_PROTOCOL + basePath + SEPARATOR + path));
                        resource.setNestedJarEntryName(null);
                    } else {
                        resource.setUrl(new URL(JAR_PROTOCOL + basePath + SEPARATOR + nestedJar + SEPARATOR + path));
                        resource.setNestedJarEntryName(nestedJar);
                    }
                } catch (IOException e) {
                    // ignore
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
                loadedResources.put(name, resource);
                return resource;
            }
        }

        private Object getLockObject(String className) {
            Object newLock = new Object();
            Object lock = lockMap.putIfAbsent(className, newLock);
            if (lock == null) {
                return newLock;
            } else {
                return lock;
            }
        }

        private class ResourceEntry {

            private byte[]       bytes;
            private URL          url;
            private Class<?>     clazz;
            private Manifest     manifest;
            public Certificate[] certificates;
            private String       nestedJarEntryName;

            public byte[] getBytes() {
                return bytes;
            }

            public void setBytes(byte[] bytes) {
                this.bytes = bytes;
            }

            public URL getUrl() {
                return url;
            }

            public void setUrl(URL url) {
                this.url = url;
            }

            public Class<?> getClazz() {
                return clazz;
            }

            public void setClazz(Class<?> clazz) {
                this.clazz = clazz;
            }

            public Manifest getManifest() {
                return manifest;
            }

            public void setManifest(Manifest manifest) {
                this.manifest = manifest;
            }

            public Certificate[] getCertificates() {
                return certificates;
            }

            public void setCertificates(Certificate[] certificates) {
                this.certificates = certificates;
            }

            public String getNestedJarEntryName() {
                return nestedJarEntryName;
            }

            public void setNestedJarEntryName(String nestedJarEntryName) {
                this.nestedJarEntryName = nestedJarEntryName;
            }
        }
    }
}
