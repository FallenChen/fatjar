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
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 12/03/2017.
 */
class FatJarTempFileManager {

    private static final String                   FATJAR_TEMP_FILE_PATH = "/.fatjar/temp";
    private static Logger                         logger                = new Logger();
    private static String                         tempDir               = System.getProperty("user.home");
    private static volatile File                  createdTempDir;

    // key:'file:/a/b.jar!/c/d.jar'
    private static final Map<String, FileWrapper> fileMap               = new HashMap<>();

    static {
        if (logger.isDebugEnabled()) {
            logger.debug("FatJarTempFileManager is loaded by " + FatJarTempFileManager.class.getClassLoader());
        }
        //
        Class<?> clazz = FileWrapper.class;
        //
        String userDefinedTempDir = FatJarSystemConfig.getTempDir();
        if (userDefinedTempDir != null) {
            tempDir = userDefinedTempDir;
        }
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
                    String actPath = tempDir + FATJAR_TEMP_FILE_PATH;
                    createdTempDir = new File(actPath);
                    if (!createdTempDir.exists()) {
                        createdTempDir.mkdirs();
                    }
                }
            }
        }
        if (tag.compareAndSet(false, true)) {
            if (logger.isInfoEnabled()) {
                logger.info(String.format("[createTempDir] temporary direcotry is at %s",
                                          createdTempDir.getAbsolutePath()));
            }
        }
    }

    public static JarFile buildJarFile(String url, InputStream inputStream) throws IOException {
        initTempFileDir();
        String fileName = url.substring(url.lastIndexOf('/') + 1, url.length());
        boolean isSnapshot = false;
        if (fileName.toLowerCase().endsWith("-snapshot.jar")) {
            fileName = url;
            isSnapshot = true;
        }
        fileName = URLEncoder.encode(fileName, "UTF-8");
        FileWrapper fileWrapper = fileMap.get(url);
        if (fileWrapper != null) {
            return fileWrapper.getJarFile();
        }
        synchronized (fileMap) {
            fileWrapper = fileMap.get(url);
            if (fileWrapper != null) {
                return fileWrapper.getJarFile();
            }
            File file = new File(createdTempDir, fileName);
            if (file.exists()) {
                if (isSnapshot) {
                    file.delete();
                    file.createNewFile();
                    if (logger.isInfoEnabled()) {
                        logger.info(String.format("+ %s | created a new temp file in %s", url, file.getAbsolutePath()));
                    }
                } else {//
                    JarFile jarFile = new JarFile(file);
                    fileMap.put(url, new FileWrapper(file, jarFile));
                    return jarFile;//
                }
            } else {
                file.createNewFile();
                if (logger.isInfoEnabled()) {
                    logger.info(String.format("+ %s | created a new temp file in %s", url, file.getAbsolutePath()));
                }
            }
            FileOutputStream tempOut = new FileOutputStream(file);
            int n;
            byte[] buffer = new byte[1024];
            while ((n = inputStream.read(buffer)) != -1) {
                tempOut.write(buffer, 0, n);
            }
            JarFile jarFile = new JarFile(file);
            fileMap.put(url, new FileWrapper(file, jarFile));
            return jarFile;
        }
    }

    public static JarFile getJarFile(String key) {
        FileWrapper fileWrapper = fileMap.get(key);
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
