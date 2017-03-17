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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 12/03/2017.
 */
class FatJarTempFileManager {

    private static final String                            FATJAR_TEMP_FILE_PATH = "/.fatjar/temp";
    private static Logger                                  logger                = LoggerFactory.getLogger(FatJarTempFileManager.class);
    private static String                                  tempDir;
    private static volatile File                           createdTempDir;

    // key:'file:/a/b.jar!/c/d.jar'
    private static ConcurrentHashMap<String, FileWrapper>  map                   = new ConcurrentHashMap();

    private static final ConcurrentHashMap<String, Object> lockMap               = new ConcurrentHashMap<>();

    static {
        tempDir = FatJarSystemConfig.getTempDir();
    }

    public static String getTempDir() {
        return tempDir;
    }

    public static void setTempDir(String tempDir) {
        FatJarTempFileManager.tempDir = filterString(tempDir);
    }

    private static AtomicBoolean tag = new AtomicBoolean(false);

    public static void initTempFileDir() {
        if (createdTempDir == null) {
            synchronized (FatJarTempFileManager.class) {
                if (createdTempDir == null) {
                    String actPath = tempDir;
                    if (tempDir == null) {
                        actPath = FatJarClassLoaderUtils.getBaseDirectry(FatJarTempFileManager.class).getPath()
                                  + FATJAR_TEMP_FILE_PATH;
                    }
                    createdTempDir = new File(actPath);
                    if (!createdTempDir.exists()) {
                        createdTempDir.mkdirs();
                    }
                } else {
                    if (!createdTempDir.exists()) {
                        createdTempDir.mkdirs();
                    }
                }
            }
        }
        if (tag.compareAndSet(false, true)) {
            if (logger.isInfoEnabled()) {
                logger.info("[FarJar] fatjar temporary direcotry is at {}", createdTempDir.getAbsolutePath());
            }
        }
    }

    public static JarFile buildJarFile(String key, String fileName, InputStream inputStream) throws IOException {
        initTempFileDir();
        fileName = URLEncoder.encode(fileName, "UTF-8");
        synchronized (getLockObject(key)) {
            FileWrapper fileWrapper = map.get(key);
            JarFile jarFile = null;
            if (fileWrapper != null) {
                jarFile = fileWrapper.getJarFile();
            }
            if (jarFile != null) {
                return jarFile;
            }
            File file = null;
            String encodeFileName = URLEncoder.encode(key, "UTF-8");
            file = new File(createdTempDir, encodeFileName);
            if (file.exists()) {
                if (encodeFileName.toLowerCase().endsWith("-snapshot.jar")) {
                    file.delete();
                    file.createNewFile();
                    if (logger.isDebugEnabled()) {
                        logger.debug("[FarJar] + {} | created a new temp file in {}", key, file.getAbsolutePath());
                    }
                }
            } else {
                file.createNewFile();
                if (logger.isDebugEnabled()) {
                    logger.debug("[FarJar] + {} | created a new temp in {}", key, file.getAbsolutePath());
                }
            }
            FileOutputStream tempOut = new FileOutputStream(file);
            int n;
            byte[] buffer = new byte[1024];
            while ((n = inputStream.read(buffer)) != -1) {
                tempOut.write(buffer, 0, n);
            }
            jarFile = new JarFile(file);
            map.put(key, new FileWrapper(file, jarFile));
            return jarFile;
        }
    }

    public static JarFile getJarFile(String key) {
        FileWrapper fileWrapper = map.get(key);
        if (fileWrapper != null) {
            return fileWrapper.getJarFile();
        } else {
            return null;
        }
    }

    private static String filterString(String str) {
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

    private static class FileWrapper {

        public FileWrapper(File file, JarFile jarFile) {
            this.file = file;
            this.jarFile = jarFile;
        }

        private File    file;
        private JarFile jarFile;

        public File getFile() {
            return file;
        }

        public void setFile(File file) {
            this.file = file;
        }

        public JarFile getJarFile() {
            return jarFile;
        }

        public void setJarFile(JarFile jarFile) {
            this.jarFile = jarFile;
        }
    }

}
