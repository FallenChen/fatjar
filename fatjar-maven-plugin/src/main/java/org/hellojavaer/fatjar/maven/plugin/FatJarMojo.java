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
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 27/02/2017.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class FatJarMojo extends AbstractMojo {

    private static final String    FAT_JAR_VERSION     = "1.0.0";

    private static final String    FAT_JAR_VERSION_KEY = "Fat-Jar-Version";

    private static final String    START_CLASS_KEY     = "Start-Class";

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

    @Parameter(defaultValue = "lib/", property = "libDirectory", required = false)
    private String                 libDirectory;

    public void execute() throws MojoExecutionException {
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
        Map<String, Artifact> fileNameMap = new HashMap<String, Artifact>();
        for (Artifact artifact : artifacts) {
            boolean matched = false;
            for (Dependency dependency : dependencies) {//
                if (dependency.getGroupId().equals(artifact.getGroupId())//
                    && dependency.getArtifactId().equals(artifact.getArtifactId())) {
                    String dependencyDesc = dependency.toString();
                    getLog().info("direct " + dependencyDesc);
                    if ("true".equalsIgnoreCase(dependency.getOptional())) {
                        if (directDependencyJarFile == null) {
                            directDependencyJarFile = artifact.getFile();
                            matched = true;
                            getLog().info("use " + dependencyDesc + " as main-dependency");
                        } else {
                            throw new MojoExecutionException(
                                                             "fatjar-maven-plugin use the direct dependency which 'optional' is true as the main-dependency,"
                                                                     + " but there are multiple direct dependencies match this condition.");
                        }
                    }
                }
            }
            if (matched == false) {
                Artifact artifact0 = fileNameMap.get(artifact.getFile().getName());
                if (artifact0 == null) {
                    String fileName = artifact.getFile().getName();
                    fileNameMap.put(fileName, artifact);
                    artifactMap.put(artifact, fileName);
                } else {
                    String fullFileName = artifact.getGroupId() + "-" + artifact.getFile().getName();
                    fileNameMap.put(fullFileName, artifact);
                    artifactMap.put(artifact, fullFileName);
                }
            }
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
            if (attributes != null && attributes.getValue(FAT_JAR_VERSION_KEY) != null) {
                throw new IllegalArgumentException("can't repeated package fat jar for fat-jar");
            }
            attributes.putValue(FAT_JAR_VERSION_KEY, FAT_JAR_VERSION);
            if (startClass != null) {
                startClass = startClass.trim();
                if (startClass.length() > 0) {
                    attributes.putValue(START_CLASS_KEY, startClass);
                }
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
                out.putNextEntry(new ZipEntry(libDirectory + entry.getValue()));
                IOUtils.copy(new FileInputStream(entry.getKey().getFile()), out);
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
