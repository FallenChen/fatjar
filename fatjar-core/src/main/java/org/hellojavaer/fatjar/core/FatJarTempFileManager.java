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
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
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
    private static volatile File                  releaseTempDir;
    private static volatile File                  snapshotTempDir;

    private static AtomicBoolean                  inited                = new AtomicBoolean(false);

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

    public static void initTempFileDir() {
        if (releaseTempDir == null || !releaseTempDir.exists()) {
            synchronized (FatJarTempFileManager.class) {
                if (releaseTempDir == null || !releaseTempDir.exists()) {
                    String path = tempDir + FATJAR_TEMP_FILE_PATH + "/release";
                    releaseTempDir = new File(path);
                    if (!releaseTempDir.exists()) {
                        releaseTempDir.mkdirs();
                    }
                }
            }
        }
        if (snapshotTempDir == null || !snapshotTempDir.exists()) {
            synchronized (FatJarTempFileManager.class) {
                if (snapshotTempDir == null || !snapshotTempDir.exists()) {
                    String path = tempDir + FATJAR_TEMP_FILE_PATH + "/snapshot";
                    snapshotTempDir = new File(path);
                    if (!snapshotTempDir.exists()) {
                        snapshotTempDir.mkdirs();
                    }
                }
            }
        }
        //
        if (inited.compareAndSet(false, true)) {
            if (logger.isInfoEnabled()) {
                logger.info(String.format("[createTempDir] temporary direcotry is at %s", tempDir
                                                                                          + FATJAR_TEMP_FILE_PATH));
            }
        }
    }

    public static JarFile buildJarFile(String fullFilePath, InputStream inputStream) throws IOException {
        initTempFileDir();
        String fileName = fullFilePath.substring(fullFilePath.lastIndexOf('/') + 1, fullFilePath.length());
        fileName = URLEncoder.encode(fileName, "UTF-8");
        FileWrapper fileWrapper = fileMap.get(fullFilePath);
        if (fileWrapper != null) {
            return fileWrapper.getJarFile();
        }
        synchronized (fileMap) {
            fileWrapper = fileMap.get(fullFilePath);
            if (fileWrapper != null) {
                return fileWrapper.getJarFile();
            }
            File file = null;
            if (fileName.toLowerCase().endsWith("-snapshot.jar")) {
                String actFileName = getActFileName(fullFilePath, fileName);
                file = new File(snapshotTempDir, actFileName);
                if (file.exists()) {
                    file.delete();
                }
                file.createNewFile();
                if (logger.isInfoEnabled()) {
                    logger.info(String.format("+ %s | created a new temp file in %s", fullFilePath,
                                              file.getAbsolutePath()));
                }
            } else {
                file = new File(releaseTempDir, fileName);
                if (file.exists()) {
                    JarFile jarFile = new JarFile(file);
                    fileMap.put(fullFilePath, new FileWrapper(file, jarFile));
                    return jarFile;//
                } else {
                    file.createNewFile();
                    if (logger.isInfoEnabled()) {
                        logger.info(String.format("+ %s | created a new temp file in %s", fullFilePath,
                                                  file.getAbsolutePath()));
                    }
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

    private static String getActFileName(String fullFilePath, String fileName) throws IOException {
        File catalogFile = new File(snapshotTempDir, "catalog.properties");
        if (!catalogFile.exists()) {
            catalogFile.createNewFile();
        }
        Properties catalog = new Properties();
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new BufferedInputStream(new FileInputStream(catalogFile));
            catalog.load(in);
            Properties snapshotFileCatalog = catalog;
            String actName = snapshotFileCatalog.getProperty(fullFilePath);
            if (actName == null) {
                actName = System.currentTimeMillis() + "-" + fileName;
                snapshotFileCatalog.setProperty(fullFilePath, actName);
                out = new FileOutputStream(catalogFile);
                snapshotFileCatalog.store(out, null);
            }
            return actName;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e1) {
                }
            }
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
