/*
 * Copyright 2012 the original author or authors.
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

package com.netflix.nebula.grails.internal;

import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.process.JavaExecSpec;
import org.grails.launcher.ForkedGrailsLauncher;
import org.grails.launcher.Main;
import org.grails.launcher.context.GrailsLaunchContext;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class GrailsLaunchConfigureAction implements Action<JavaExecSpec> {

    private final GrailsLaunchContext launchContext;
    private final File springloadedJar;
    private final File contextDestination;

    public GrailsLaunchConfigureAction(GrailsLaunchContext launchContext, File springloadedJar, File contextDestination) {
        this.launchContext = launchContext;
        this.springloadedJar = springloadedJar;
        this.contextDestination = contextDestination;
    }

    @Override
    public void execute(JavaExecSpec javaExec) {
        configureReloadAgent(javaExec); // mutates the launch context

        OutputStream fileOut = null;
        ObjectOutputStream oos = null;
        try {
            fileOut = new FileOutputStream(contextDestination);
            oos = new ObjectOutputStream(fileOut);
            oos.writeObject(launchContext);

            javaExec.setWorkingDir(launchContext.getBaseDir());
            if (launchContext.getGrailsHome() != null) {
                javaExec.systemProperty("grails.home", launchContext.getGrailsHome().getAbsolutePath());
            }

            File launcherJar = findJarFile(ForkedGrailsLauncher.class);
            javaExec.classpath(launcherJar);
            javaExec.getMainClass().set(Main.class.getName());
            javaExec.args(contextDestination.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }
                if (fileOut != null) {
                    fileOut.close();
                }
            } catch (IOException ignore) {

            }
        }
    }

    public void configureReloadAgent(JavaExecSpec exec) {
        if (springloadedJar == null) {
            return;
        }

        String agentJarFilePath = springloadedJar.getAbsolutePath();

        // Workaround http://issues.gradle.org/browse/GRADLE-2485
        Boolean isDebug = exec.getDebug();
        exec.jvmArgs(String.format("-javaagent:%s", agentJarFilePath), "-noverify");
        if (isDebug) {
            exec.setDebug(true);
        }
        exec.systemProperty("springloaded", "profile=grails");
    }

    private File findJarFile(Class targetClass) {
        String absolutePath = targetClass.getResource('/' + targetClass.getName().replace(".", "/") + ".class").getPath();
        String jarPath = absolutePath.substring("file:".length(), absolutePath.lastIndexOf("!"));
        return new File(jarPath);
    }

}
