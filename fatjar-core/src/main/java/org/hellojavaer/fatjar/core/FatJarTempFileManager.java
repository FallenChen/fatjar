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

    private static Logger                         logger                     = new Logger();

    private static final String                   FATJAR_TEMP_FILE_BASE_PATH = "/.fatjar";

    private static final String                   FATJAR_TEMP_FILE_LIB_PATH  = FATJAR_TEMP_FILE_BASE_PATH + "/temp/lib";

    private static volatile File                  createdTempDir;

    private static String                         tempDir                    = System.getProperty("user.home");

    private static AtomicBoolean                  inited                     = new AtomicBoolean(false);

    // key:'file:/a/b.jar!/c/d.jar'
    private static final Map<String, FileWrapper> fileMap                    = new HashMap<>();

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

    public static void initTempFileDir() {
        if (createdTempDir == null || !createdTempDir.exists()) {
            synchronized (FatJarTempFileManager.class) {
                if (createdTempDir == null || !createdTempDir.exists()) {
                    String path = tempDir + FATJAR_TEMP_FILE_LIB_PATH;
                    createdTempDir = new File(path);
                    if (!createdTempDir.exists()) {
                        createdTempDir.mkdirs();
                    }
                }
            }
        }

        //
        if (inited.compareAndSet(false, true)) {
            if (logger.isInfoEnabled()) {
                logger.info(String.format("[createTempDir] temporary direcotry is at %s", tempDir
                                                                                          + FATJAR_TEMP_FILE_BASE_PATH));
            }
        }
    }

    /**
     * use fileName and lastModified identify a file
     */
    public static JarFile buildJarFile(String fullFilePath, long lastModified, InputStream inputStream)
                                                                                                       throws IOException {
        initTempFileDir();
        String fileName = fullFilePath.substring(fullFilePath.lastIndexOf('/') + 1, fullFilePath.length());
        FileWrapper fileWrapper = fileMap.get(fullFilePath);
        if (fileWrapper != null) {
            return fileWrapper.getJarFile();
        }
        synchronized (fileMap) {
            fileWrapper = fileMap.get(fullFilePath);
            if (fileWrapper != null) {
                return fileWrapper.getJarFile();
            }
            //
            int lastIndexOfDot = fileName.lastIndexOf('.');
            // standardize file name
            if (lastModified < 0) {
                lastModified = 0;
            }
            String fileNameWithLastModified = fileName.substring(0, lastIndexOfDot) + "-" + lastModified
                                              + fileName.substring(lastIndexOfDot);
            fileNameWithLastModified = URLEncoder.encode(fileNameWithLastModified, "UTF-8");
            File file = new File(createdTempDir, fileNameWithLastModified);
            if (file.exists()) {
                JarFile jarFile = new JarFile(file);
                fileMap.put(fullFilePath, new FileWrapper(file, jarFile));
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("link %s to %s", fullFilePath, file.getAbsolutePath()));
                }
                return jarFile;//
            } else {
                file.createNewFile();
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("decompress %s to %s", fullFilePath, file.getAbsolutePath()));
                }
            }
            //
            FileOutputStream tempOut = new FileOutputStream(file);
            int n;
            byte[] buffer = new byte[1024];
            while ((n = inputStream.read(buffer)) != -1) {
                tempOut.write(buffer, 0, n);
            }
            JarFile jarFile = new JarFile(file);
            fileMap.put(fullFilePath, new FileWrapper(file, jarFile));
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
