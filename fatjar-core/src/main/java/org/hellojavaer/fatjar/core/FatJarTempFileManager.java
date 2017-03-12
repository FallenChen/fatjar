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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.security.CodeSource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarFile;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 12/03/2017.
 */
public class FatJarTempFileManager {

    private static String                                  TEMP_FILE_DIR_KEY = "fatjar.temp.createdTempDir";
    private static String                                  tempFileDir;
    private static File                                    createdTempDir;

    private static ConcurrentHashMap<String, JarFile>      map               = new ConcurrentHashMap();

    private static final ConcurrentHashMap<String, Object> lockMap           = new ConcurrentHashMap<>();
    private static final AtomicLong                        count             = new AtomicLong(0);

    static {
        tempFileDir = System.getProperty(TEMP_FILE_DIR_KEY);
        tempFileDir = filter(tempFileDir);
    }

    public static String getTempFileDir() {
        return tempFileDir;
    }

    public static void setTempFileDir(String tempFileDir) {
        FatJarTempFileManager.tempFileDir = filter(tempFileDir);
    }

    public static JarFile buildJarFile(String key, String fileName, InputStream inputStream) throws IOException {
        fileName = URLEncoder.encode(fileName, "UTF-8");
        synchronized (getLockObject(key)) {
            JarFile jarFile = map.get(key);
            if (jarFile != null) {
                return jarFile;
            }
            File file = null;
            if (tempFileDir == null) {
                file = File.createTempFile("_fatjar_" + fileName, "");
            } else {
                if (createdTempDir == null) {
                    String actPath = tempFileDir;
                    if (tempFileDir.equals('.')) {
                        actPath = getClassLocation(FatJarTempFileManager.class).getPath();
                    }
                    createdTempDir = new File(actPath);
                    createdTempDir.mkdirs();
                } else {
                    if (!createdTempDir.exists()) {
                        createdTempDir.mkdirs();
                    }
                    file = new File(createdTempDir, fileName);
                }
            }
            while (!file.exists()) {
                file = new File(createdTempDir, "duplicate_" + count.incrementAndGet() + "_" + fileName);
            }
            file.createNewFile();
            FileOutputStream tempOut = new FileOutputStream(file);
            int n;
            byte[] buffer = new byte[1024];
            while ((n = inputStream.read(buffer)) != -1) {
                tempOut.write(buffer, 0, n);
            }
            jarFile = new JarFile(file);
            map.put(key, jarFile);
            return jarFile;
        }
    }

    public static JarFile getJarFile(String key) {
        return map.get(key);
    }

    private static String filter(String str) {
        if (str != null) {
            str = str.trim();
            if (str.length() == 0) {
                str = null;
            }
        }
        return str;
    }

    private static Object getLockObject(String className) {
        Object newLock = new Object();
        Object lock = lockMap.putIfAbsent(className, newLock);
        if (lock == null) {
            return newLock;
        } else {
            return lock;
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
