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

import java.io.ByteArrayInputStream;
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
 * The implement of this class referenced {@link org.apache.catalina.loader.WebappClassLoaderBase}
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 18/03/2017.
 */
public class FatJarClassLoader extends URLClassLoader {

    private static final String               FAT_JAR_BUILDING_TOOL_ID_KEY = "Fat-Jar-Building-Tool-Id";

    private static final Logger               logger                       = new Logger();
    private static final String               JAR_PROTOCOL                 = "jar:";
    private static final String               CLASS_SUFFIX                 = ".class";
    private static final String               SEPARATOR                    = "!/";

    private static ClassLoader                j2seClassLoader              = null;
    private static SecurityManager            securityManager              = null;

    private boolean                           delegate                     = true;

    private JarFile                           fatJar                       = null;
    private Map<String, JarFile>              dependencyJars               = new LinkedHashMap<>();
    private List<FatJarClassLoader>           subClassLoaders              = new ArrayList<>();

    private Map<String, ResourceEntry>        loadedResources              = new HashMap<>();
    private Set<String>                       notFoundResources            = new HashSet<>();

    private ConcurrentHashMap<String, Object> lockMap                      = new ConcurrentHashMap<>();

    private boolean                           initedNestedJars             = false;

    private ClassLoader                       child                        = null;

    private boolean                           useSelfAsChildrensParent     = false;

    private int                               fatJarClassLoaderLevel       = 1;
    private FatJarClassLoader                 fatJarClassLoaderParent      = null;

    static {
        //
        if (logger.isDebugEnabled()) {
            logger.debug("FatJarClassLoader is loaded by " + FatJarClassLoader.class.getClassLoader());
        }
        // 0. force the classload which loaded FatJarClassLoader to load the following directly dependency classes
        Class<?> temp = ResourceEntry.class;
        temp = FatJarReflectionUtils.class;
        temp = FatJarSystemConfig.class;
        temp = FatJarTempFileManager.class;
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
    }

    public FatJarClassLoader(JarFile fatJar, URL url, ClassLoader parent, ClassLoader child, boolean delegate) {
        super(new URL[] { url }, parent);
        this.fatJar = fatJar;
        this.child = child;
        this.delegate = delegate;
    }

    public FatJarClassLoader(JarFile fatJar, URL url, ClassLoader parent, ClassLoader child, boolean delegate,
                             boolean useSelfAsChildrensParent) {
        super(new URL[] { url }, parent);
        this.fatJar = fatJar;
        this.child = child;
        this.delegate = delegate;
        this.useSelfAsChildrensParent = useSelfAsChildrensParent;
    }

    protected List<FatJarClassLoader> getSubClassLoaders() {
        initNestedJars();
        if (this.subClassLoaders == null) {
            return Collections.emptyList();
        } else {
            return this.subClassLoaders;
        }
    }

    private URL getURL() {
        return getURLs()[0];
    }

    protected void initNestedJars() {
        if (initedNestedJars == false) {
            synchronized (this) {
                if (initedNestedJars == false) {
                    Enumeration<JarEntry> jarEntries = fatJar.entries();
                    if (jarEntries != null) {
                        while (jarEntries.hasMoreElements()) {
                            JarEntry jarEntry = jarEntries.nextElement();
                            if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(".jar")) {
                                try {
                                    URL nestedJarURL = new URL(getURL().toString() + SEPARATOR + jarEntry.getName());
                                    JarFile nestedJarFile = FatJarTempFileManager.buildJarFile(nestedJarURL.getFile(),
                                                                                               jarEntry.getTime(),
                                                                                               fatJar.getInputStream(jarEntry));
                                    Manifest manifest = nestedJarFile.getManifest();
                                    if (isFatJar(manifest)) {
                                        if (useSelfAsChildrensParent) {
                                            FatJarClassLoader subClassLoader = new FatJarClassLoader(nestedJarFile,
                                                                                                     nestedJarURL,
                                                                                                     this, child,
                                                                                                     delegate, false);
                                            subClassLoader.fatJarClassLoaderLevel = this.fatJarClassLoaderLevel + 1;
                                            subClassLoader.fatJarClassLoaderParent = this;
                                            subClassLoaders.add(subClassLoader);
                                        } else {
                                            FatJarClassLoader subClassLoader = new FatJarClassLoader(nestedJarFile,
                                                                                                     nestedJarURL,
                                                                                                     getParent(),
                                                                                                     child, delegate,
                                                                                                     false);
                                            subClassLoader.fatJarClassLoaderLevel = this.fatJarClassLoaderLevel + 1;
                                            subClassLoader.fatJarClassLoaderParent = this;
                                            subClassLoaders.add(subClassLoader);
                                        }
                                    } else {
                                        dependencyJars.put(jarEntry.getName(), nestedJarFile);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }// else ignore
                        }
                    }
                    initedNestedJars = true;
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[{child:");
        sb.append(child == null ? "null" : child.getClass().getName());
        sb.append("}->{");
        sb.append("own:");
        sb.append(toSimpleString());
        sb.append(",delegate:");
        sb.append(delegate);
        sb.append(",useSelfAsChildrensParent:");
        sb.append(useSelfAsChildrensParent);
        sb.append("}->{");
        sb.append("parent:");
        sb.append(getParent() == null ? "null" : getParent().getClass().getName());
        sb.append("}");
        sb.append(",jarFile:");
        sb.append(getURL());
        sb.append("]");
        return sb.toString();
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
            for (FatJarClassLoader subClassLoader : getSubClassLoaders()) {
                if (subClassLoader.containsClass(name)) {
                    try {
                        clazz = subClassLoader.loadClass(name, resolve);
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

            // 3.0
            if (delegate && getParent() != null) {
                clazz = invokeLoadClass(getParent(), name, resolve);
                if (clazz != null) {
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }
            }
            // 3.1
            if (child != null) {
                clazz = invokeFindClass(child, name);
                if (clazz != null) {
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }
            }
            // 3.2
            if (!delegate && getParent() != null) {
                clazz = invokeLoadClass(getParent(), name, resolve);
                if (clazz != null) {
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }
            }
            //
            return null;
        }
    }

    //
    @Override
    public URL getResource(String name) {
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

            // 2.0
            resource = findResourceInternal(name, name);
            if (resource != null) {
                return resource.getUrl();
            }
            // 2.1
            for (FatJarClassLoader subClassLoader : getSubClassLoaders()) {
                if (subClassLoader.containsResource(name)) {
                    url = subClassLoader.getResource(name);
                    if (url != null) {
                        return url;
                    }
                }
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
                url = invokeFindResource(child, name);
                if (url != null) {
                    return url;
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
        synchronized (getLockObject(name)) {
            LinkedHashSet result = new LinkedHashSet();
            // 1. load by j2se
            URL url = j2seClassLoader.getResource(name);
            if (url != null) {
                result.add(url);
            }

            // 2.0
            ResourceEntry resource = findResourceInternal(name, name);
            if (resource != null) {
                result.add(resource.getUrl());
            }
            // 2.1
            for (FatJarClassLoader subClassLoader : getSubClassLoaders()) {
                if (subClassLoader.containsResource(name)) {
                    url = subClassLoader.getResource(name);
                    if (url != null) {
                        result.add(url);
                    }
                }
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
                Enumeration<URL> enumeration = invokeFindResources(child, name);
                if (enumeration != null) {
                    while (enumeration.hasMoreElements()) {
                        result.add(enumeration.nextElement());
                    }
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
    public InputStream getResourceAsStream(String name) {
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

            // 2.0
            resource = findResourceInternal(name, name);
            if (resource != null) {
                return new ByteArrayInputStream(resource.getBytes());
            }
            // 2.1
            for (FatJarClassLoader subClassLoader : getSubClassLoaders()) {
                if (subClassLoader.containsResource(name)) {
                    inputStream = subClassLoader.getResourceAsStream(name);
                    if (inputStream != null) {
                        return inputStream;
                    }
                }
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
                URL url = invokeFindResource(child, name);
                if (url != null) {
                    try {
                        inputStream = url.openStream();
                        if (inputStream != null) {
                            return inputStream;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
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
        String path = name.replace('.', '/') + CLASS_SUFFIX;
        return containsResource(path);
    }

    protected boolean containsResource(String name) {
        if (filterResource(name)) {
            return false;
        }
        if (fatJar != null && fatJar.getJarEntry(name) != null) {
            return true;
        } else {
            return false;
        }
    }

    protected boolean filterResource(String name) {
        if (name.startsWith("org/hellojavaer/fatjar/core/")) {
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
        String path = name.replace('.', '/') + CLASS_SUFFIX;
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
                codeSource = new CodeSource(getURL(), resource.getCertificates());
            } else {
                try {
                    codeSource = new CodeSource(new URL(getURL().toString() + SEPARATOR
                                                        + resource.getNestedJarEntryName()), resource.getCertificates());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            Class clazz = defineClass(name, resource.getBytes(), 0, resource.getBytes().length, codeSource);
            resource.setClazz(clazz);
            if (logger.isDebugEnabled()) {
                logger.debug((this.toSimpleString() + "["//
                              + (fatJarClassLoaderParent == null ? "" : fatJarClassLoaderParent.toSimpleString()) //
                              + "]"//
                              + " loaded class " + clazz.getName() + " from " + codeSource.getLocation()));
            }
            return clazz;
        }
    }

    private String toSimpleString() {
        return fatJarClassLoaderLevel + "-" + getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    }

    protected synchronized ResourceEntry findResourceInternal(String name, String path) {
        if (filterResource(path)) {
            return null;
        }
        if (notFoundResources.contains(name)) {
            return null;
        }
        if (this.fatJar != null) {
            ResourceEntry resource = findResourceInternal0(this.fatJar, name, path, null);
            if (resource != null) {
                return resource;
            }
        }
        initNestedJars();
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
                    if (next <= 0) {
                        break;
                    }
                    pos += next;
                }
                resource.setBytes(bytes);
                resource.setManifest(jarFile.getManifest());
                resource.setCertificates(jarEntry.getCertificates());
                if (nestedJar == null) {
                    resource.setUrl(new URL(JAR_PROTOCOL + getURL().toString() + SEPARATOR + path));
                    resource.setNestedJarEntryName(null);
                } else {
                    resource.setUrl(new URL(JAR_PROTOCOL + getURL().toString() + SEPARATOR + nestedJar + SEPARATOR
                                            + path));
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

    protected ClassLoader getChild() {
        return child;
    }

    protected boolean isDelegate() {
        return delegate;
    }

    protected boolean isUseSelfAsChildrensParent() {
        return useSelfAsChildrensParent;
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

    static Class<?> invokeLoadClass(ClassLoader classLoader, String name, boolean resolve) {
        if (classLoader == null) {
            return null;
        }
        Method method = FatJarReflectionUtils.getMethod(classLoader.getClass(), "loadClass", String.class,
                                                        boolean.class);
        try {
            return (Class<?>) method.invoke(classLoader, name, resolve);
        } catch (Exception e) {
            Throwable temp = e;
            while (temp != null) {
                if (temp instanceof ClassNotFoundException) {
                    return null;
                } else {
                    temp = temp.getCause();
                }
            }
            throw new RuntimeException(e);
        }
    }

    static Class<?> invokeFindClass(ClassLoader classLoader, String name) throws ClassNotFoundException {
        if (classLoader == null) {
            return null;
        }
        Method method = FatJarReflectionUtils.getMethod(classLoader.getClass(), "findClass", String.class);
        try {
            return (Class<?>) method.invoke(classLoader, name);
        } catch (Exception e) {
            Throwable temp = e;
            while (temp != null) {
                if (temp instanceof ClassNotFoundException) {
                    return null;
                } else if (temp instanceof LinkageError) {
                    if (temp.getMessage() != null
                        && temp.getMessage().contains("attempted  duplicate class definition for name")) {
                        return classLoader.loadClass(name);
                    } else {
                        throw new RuntimeException(e);
                    }
                } else {
                    temp = temp.getCause();
                }
            }
            throw new RuntimeException(e);
        }
    }

    static URL invokeFindResource(ClassLoader classLoader, String name) {
        if (classLoader == null) {
            return null;
        }
        Method method = FatJarReflectionUtils.getMethod(classLoader.getClass(), "findResource", String.class);
        try {
            return (URL) method.invoke(classLoader, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Enumeration<URL> invokeFindResources(ClassLoader classLoader, String name) {
        if (classLoader == null) {
            return null;
        }
        Method method = FatJarReflectionUtils.getMethod(classLoader.getClass(), "findResources", String.class);
        try {
            return (Enumeration<URL>) method.invoke(classLoader, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    static boolean isFatJar(Manifest manifest) {
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            String fatJarVersion = attributes.getValue(FAT_JAR_BUILDING_TOOL_ID_KEY);
            if (fatJarVersion != null) {
                return true;
            }
        }
        return false;
    }
}
