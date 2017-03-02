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

    private static final String    FAT_JAR_VERSION           = "1.0.0";

    private static final String    FAT_JAR_VERSION_CONSTANTS = "Fat-Jar-Version";

    @Parameter(defaultValue = "${project.artifacts}", required = true, readonly = true)
    private Collection<Artifact>   artifacts;

    @Parameter(defaultValue = "${project.dependencies}", required = true, readonly = true)
    private Collection<Dependency> dependencies;

    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private File                   outputDirectory;

    @Parameter(defaultValue = "${project.build.finalName}.jar", required = true)
    private String                 fileName;

    public void execute() throws MojoExecutionException {
        if (artifacts == null || artifacts.isEmpty() || dependencies == null || dependencies.isEmpty()) {
            throw new MojoExecutionException("Dependency is empty");
        }
        File directDependencyJarFile = null;
        Map<Artifact, String> fileNameMap = new LinkedHashMap<Artifact, String>();
        Map<String, Artifact> names = new HashMap<String, Artifact>();
        for (Artifact artifact : artifacts) {
            boolean matched = false;
            for (Dependency dependency : dependencies) {
                if (dependency.getGroupId().equals(artifact.getGroupId())
                    && dependency.getArtifactId().equals(artifact.getArtifactId())) {
                    if (directDependencyJarFile == null) {
                        directDependencyJarFile = artifact.getFile();
                        matched = true;
                    } else {
                        throw new MojoExecutionException("Direct dependency limits 1");
                    }
                }
            }
            if (matched == false) {
                Artifact exsit = names.get(artifact.getFile().getName());
                if (exsit == null) {
                    names.put(artifact.getFile().getName(), artifact);
                    fileNameMap.put(artifact, artifact.getFile().getName());
                } else {
                    fileNameMap.put(artifact, artifact.getGroupId() + "-" + artifact.getFile().getName());
                    fileNameMap.put(exsit, exsit.getGroupId() + "-" + exsit.getFile().getName());
                }
            }
        }
        if (directDependencyJarFile == null) {
            throw new MojoExecutionException("Direct dependency is empty");
        }

        JarOutputStream out = null;
        JarInputStream directDependencyJarInputStream = null;
        File jarFile;
        try {
            // 0.verification
            directDependencyJarInputStream = new JarInputStream(new FileInputStream(directDependencyJarFile));
            Manifest manifest = directDependencyJarInputStream.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            if (attributes != null && attributes.getValue(FAT_JAR_VERSION_CONSTANTS) != null) {
                throw new IllegalArgumentException("Can't repeated package fat jar for fat-jar");
            }
            attributes.putValue(FAT_JAR_VERSION_CONSTANTS, FAT_JAR_VERSION);

            // 1.create output file
            jarFile = new File(outputDirectory, fileName);
            out = new JarOutputStream(new FileOutputStream(jarFile, false), manifest);

            // 2.import direct dependency
            ZipEntry zipEntry;
            while ((zipEntry = directDependencyJarInputStream.getNextEntry()) != null) {
                out.putNextEntry(zipEntry);
                IOUtils.copy(directDependencyJarInputStream, out);
                out.closeEntry();
            }

            // 3.import indirect dependency
            for (Map.Entry<Artifact, String> entry : fileNameMap.entrySet()) {
                out.putNextEntry(new ZipEntry("LIB-INF/" + entry.getValue()));
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
