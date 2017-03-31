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
package org.hellojavaer.fatjar.maven.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 27/02/2017.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class FatJarMojo extends AbstractMojo {

    private static final String    FAT_JAR_BUILDING_TOOL_ID_KEY = "Fat-Jar-Building-Tool-Id";
    private static final String    FAT_JAR_BUILDING_TOOL_ID     = "org.hellojavaer.fatjar-fatjar-maven-plugin";

    private static final String    START_CLASS_KEY              = "Start-Class";
    private static final String    MAIN_CLASS_KEY               = "Main-Class";

    @Parameter(defaultValue = "${project.artifacts}", required = true, readonly = true)
    private Collection<Artifact>   artifacts;

    @Parameter(defaultValue = "${project.dependencies}", required = true, readonly = true)
    private Collection<Dependency> dependencies;

    @Parameter(defaultValue = "${project.dependencyManagement.dependencies}", required = false, readonly = true)
    private Collection<Dependency> dependencyManagement;

    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private File                   targetDirectory;

    @Parameter(defaultValue = "${project.build.finalName}.jar", required = true)
    private String                 fileName;

    @Parameter(defaultValue = "", property = "startClass", required = false)
    private String                 startClass;

    @Parameter(defaultValue = "", property = "mainClass", required = false)
    private String                 mainClass;

    @Parameter(defaultValue = "lib/", property = "libDirectory", required = false)
    private String                 libDirectory;

    public void execute() throws MojoExecutionException {
        if (startClass != null) {
            startClass = startClass.trim();
            if (startClass.length() == 0) {
                startClass = null;
            }
        }
        if (mainClass != null) {
            mainClass = mainClass.trim();
            if (mainClass.length() == 0) {
                mainClass = null;
            }
        }

        if (artifacts == null || artifacts.isEmpty() || dependencies == null || dependencies.isEmpty()) {
            throw new MojoExecutionException("Dependency can't be empty when building fatjar");
        }
        if (dependencyManagement != null && dependencyManagement.size() > 0) {
            throw new MojoExecutionException("Can't include dependencyManagement when building fatjar");
        }
        if (!libDirectory.endsWith("/")) {
            libDirectory = libDirectory + "/";
        }
        File directDependencyJarFile = null;
        Map<Artifact, String> artifactMap = new LinkedHashMap<Artifact, String>();
        for (Artifact artifact : artifacts) {
            boolean isSkip = false;
            for (Dependency dependency : dependencies) {//
                if (dependency.getGroupId().equals(artifact.getGroupId())//
                    && dependency.getArtifactId().equals(artifact.getArtifactId())) {
                    if (dependency.isOptional()) {
                        if (directDependencyJarFile == null) {
                            directDependencyJarFile = artifact.getFile();
                            getLog().info("use " + dependency.toString() + " as main-dependency");
                        } else {
                            throw new MojoExecutionException(
                                                             "fatjar-maven-plugin use the direct dependency which 'optional' is true as the main-dependency,"
                                                                     + " but there are multiple direct dependencies match this condition.");
                        }
                    }
                    if (startClass == null) {
                        isSkip = true;
                    }
                    break;
                }
            }
            if (isSkip) {
                continue;
            }
            String fullFileName = artifact.getGroupId() + "-" + artifact.getFile().getName();
            artifactMap.put(artifact, fullFileName);
        }
        if (directDependencyJarFile == null) {
            throw new MojoExecutionException(
                                             "fatjar-maven-plugin use the direct dependency which 'optional' is true as the main-dependency,"
                                                     + " and there isn't a direct dependency which matches this condition.");
        }

        JarOutputStream out = null;
        JarInputStream directDependencyJarInputStream = null;
        File jarFile;
        try {
            // 0.verification
            directDependencyJarInputStream = new JarInputStream(new FileInputStream(directDependencyJarFile));
            Manifest manifest = directDependencyJarInputStream.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            if (attributes != null && attributes.getValue(FAT_JAR_BUILDING_TOOL_ID_KEY) != null) {
                throw new IllegalArgumentException("can't repeated package fat jar for a fat-jar");
            }
            attributes.putValue(FAT_JAR_BUILDING_TOOL_ID_KEY, FAT_JAR_BUILDING_TOOL_ID);
            if (startClass != null) {
                attributes.putValue(START_CLASS_KEY, startClass);
            }
            if (mainClass != null) {
                attributes.putValue(MAIN_CLASS_KEY, mainClass);
            }

            // 1.create output file
            jarFile = new File(targetDirectory, fileName);
            out = new JarOutputStream(new FileOutputStream(jarFile, false), manifest);

            // 2.import direct dependency
            ZipEntry zipEntry;
            while ((zipEntry = directDependencyJarInputStream.getNextEntry()) != null) {
                out.putNextEntry(zipEntry);
                IOUtils.copy(directDependencyJarInputStream, out);
                out.closeEntry();
            }

            // 3.import indirect dependency
            for (Map.Entry<Artifact, String> entry : artifactMap.entrySet()) {
                ZipEntry zipEntry0 = new ZipEntry(libDirectory + entry.getValue());
                File file = entry.getKey().getFile();
                long lastModified = -1;
                try {
                    JarFile temp = new JarFile(file);
                    JarEntry jarEntry = temp.getJarEntry("META-INF/MANIFEST.MF");
                    if (jarEntry != null) {
                        lastModified = jarEntry.getTime();
                    }
                } catch (Throwable e) {
                    getLog().warn(entry.getValue() + " isn't a jar");
                }
                zipEntry0.setTime(lastModified);// mark down the lastModified
                out.putNextEntry(zipEntry0);
                IOUtils.copy(new FileInputStream(file), out);
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(out);
            IOUtils.closeQuietly(directDependencyJarInputStream);
        }
    }
}
