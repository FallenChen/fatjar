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

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 *
 * The implement of this class refered to org.apache.catalina.loader.WebappClassLoaderBase
 *
 * 
 * 
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 02/03/2017.
 */
public class FatJarClassLoader extends URLClassLoader {

    private static final String        CLASSS_SUBFIX     = ".class";
    private static final String        SEPARATOR         = "!/";

    private boolean                    delegate          = false;

    private JarFile                    currentJar;
    private List<JarFile>              dependencyJars    = new ArrayList<>();

    private List<FatJarClassLoader>    subClassLoaders   = new ArrayList<>();

    private Map<String, ResourceEntry> loadedResources   = new HashMap<>();
    private Set<String>                notFoundResources = new HashSet<>();

    private String                     pathPrefix        = "";

    private static ClassLoader         j2seClassLoader;

    static {
        ClassLoader cl = String.class.getClassLoader();
        if (cl == null) {
            cl = getSystemClassLoader();
            while (cl.getParent() != null) {
                cl = cl.getParent();
            }
        }
        j2seClassLoader = cl;
    }

    public FatJarClassLoader(URL[] urls, ClassLoader parent, boolean delegate) {
        super(urls, parent);
        this.delegate = delegate;
        init();
    }

    public FatJarClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        init();
    }

    public FatJarClassLoader(URL[] urls) {
        super(urls);
        init();
    }

    private void init() {
        for (URL url : getURLs()) {
            List<File> jarFiles = listJarFiles(url);
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    try {
                        JarFile jar = new JarFile(jarFile);
                        Manifest manifest = jar.getManifest();
                        if (isFatJar(manifest)) {
                            URL path = jarFile.getCanonicalFile().toURI().toURL();
                            subClassLoaders.add(new FatJarClassLoader(jar, getParent(), "jar:" + path.toString()
                                                                                        + SEPARATOR, delegate));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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

    private FatJarClassLoader(JarFile currentJar, ClassLoader parent, String pathPrefix, boolean delegate) {
        super(new URL[0], parent);
        this.currentJar = currentJar;
        this.pathPrefix = pathPrefix;
        this.delegate = delegate;

        Enumeration<JarEntry> jarEntries = currentJar.entries();
        if (jarEntries != null) {
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                if (isDependencyLib(jarEntry)) {
                    try {
                        InputStream inputStream = currentJar.getInputStream(jarEntry);
                        JarFile subJarFile = buildJarFile(inputStream);
                        Manifest manifest = subJarFile.getManifest();
                        if (isFatJar(manifest)) {
                            subClassLoaders.add(new FatJarClassLoader(subJarFile, parent, pathPrefix
                                                                                          + jarEntry.getName()
                                                                                          + SEPARATOR, delegate));
                        } else {
                            dependencyJars.add(subJarFile);
                        }
                    } catch (IOException e) {
                        System.err.println(e);
                    }
                }// else ignore
            }
        }
    }

    protected JarFile buildJarFile(InputStream inputStream) throws IOException {
        File tempFile = File.createTempFile(String.valueOf(System.currentTimeMillis()), ".jar");
        FileOutputStream tempOut = new FileOutputStream(tempFile);
        int n;
        byte[] buffer = new byte[1024];
        while ((n = inputStream.read(buffer)) != -1) {
            tempOut.write(buffer, 0, n);
        }
        return new JarFile(tempFile);
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

    private boolean isDependencyLib(JarEntry jarEntry) {
        String name = jarEntry.getName();
        if (!jarEntry.isDirectory() && name.endsWith(".jar")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = null;
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

        // 2. parent delegate
        if (delegate) {
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

        // 3.0 load from local resources
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
        // 3.1 load by sub-classload which will recursive find in fat-jar
        if (subClassLoaders != null) {
            for (FatJarClassLoader fatJarClassLoader : subClassLoaders) {
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

        // 4.
        if (!delegate) {
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
        return null;
    }

    //
    @Override
    public URL getResource(String name) {
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

        // 2. parent delegate
        if (delegate) {
            url = getParent().getResource(name);
            if (url != null) {
                return url;
            }
        }

        // 3.
        ResourceEntry resourceEntry = findResourceInternal(name, name);
        if (resourceEntry != null) {
            return resourceEntry.getUrl();
        }

        // 4.
        if (delegate) {
            url = getParent().getResource(name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
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

        // 2. parent delegate
        if (delegate) {
            inputStream = getParent().getResourceAsStream(name);
            if (inputStream != null) {
                return inputStream;
            }
        }

        // 3.
        ResourceEntry resourceEntry = findResourceInternal(name, name);
        if (resourceEntry != null) {
            return new ByteArrayInputStream(resource.getBytes());
        }

        // 4.
        if (delegate) {
            inputStream = getParent().getResourceAsStream(name);
            if (inputStream != null) {
                return inputStream;
            }
        }
        return null;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return findClassInternal(name);
    }

    protected Class<?> findClassInternal(String name) throws ClassNotFoundException {
        ResourceEntry resource = loadedResources.get(name);
        if (resource != null) {
            return resource.getClazz();
        }

        String packageName = null;
        String path = name.replace('.', '/') + CLASSS_SUBFIX;
        resource = findResourceInternal(name, path);
        if (resource == null) {
            return null;
        } else {
            int pos = name.lastIndexOf('.');
            if (pos < -1) {
                packageName = name.substring(0, pos);
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

            Class clazz = defineClass(name, resource.getBytes(), 0, resource.getBytes().length,
                                      new CodeSource(resource.getUrl(), resource.getCertificates()));
            resource.setClazz(clazz);
            return clazz;
        }
    }

    protected ResourceEntry findResourceInternal(String name, String path) {
        if (notFoundResources.contains(name)) {
            return null;
        }
        if (this.currentJar != null) {
            ResourceEntry resource = findResourceInternal0(this.currentJar, name, path);
            if (resource != null) {
                return resource;
            }
        }
        if (this.dependencyJars != null) {
            for (JarFile item : this.dependencyJars) {
                ResourceEntry resource = findResourceInternal0(item, name, path);
                if (resource != null) {
                    return resource;
                }
            }
        }
        notFoundResources.add(name);
        return null;
    }

    protected ResourceEntry findResourceInternal0(JarFile jarFile, String name, String path) {
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
                resource.setUrl(new URL(pathPrefix + path));
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

    protected boolean containsClass(String name) {
        String path = name.replace('.', '/') + CLASSS_SUBFIX;
        if (currentJar != null && currentJar.getJarEntry(path) != null) {
            return true;
        } else {
            return false;
        }
    }

    // get from local
    @Override
    public URL findResource(String name) {
        ResourceEntry resource = findResourceInternal(name, name);
        return resource.getUrl();
    }

    private class ResourceEntry {

        private byte[]       bytes;
        private URL          url;
        private Class<?>     clazz;
        private Manifest     manifest;
        public Certificate[] certificates;

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
    }
}
