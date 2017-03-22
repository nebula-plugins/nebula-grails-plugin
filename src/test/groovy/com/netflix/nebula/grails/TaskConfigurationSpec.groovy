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

package com.netflix.nebula.grails

import org.gradle.testfixtures.ProjectBuilder
import com.netflix.nebula.grails.tasks.GrailsPluginPackageTask
import com.netflix.nebula.grails.tasks.GrailsWarTask

class TaskConfigurationSpec extends PluginSpec {

    def "basic tasks are in place"() {
        given:
        def baseTasks = ['check', 'build', 'assemble', 'clean']
        def grailsTasks = ['init', 'init-plugin', 'test', 'run', 'war']

        expect:
        (baseTasks + grailsTasks).each {
            assert project.tasks.findByName(it)
        }
    }

    def "default configuration extends the runtime configuration"() {
        expect:
        project.configurations.default.extendsFrom.contains(project.configurations.runtime)
    }

    def "war task defaults to production environment"() {
        expect:
        project.tasks.findByName('war').getEnv() == 'production'
    }

    def "war file is configured as runtime artifact for application"() {
        given:
        project.evaluate()
        List<File> artifactFiles = project.configurations.runtime.artifacts.files.files as List

        expect:
        assert artifactFiles.size() == 1
        assert artifactFiles.first() == project.file("build/distributions/${project.name}-${project.version}.war")

        and:
        assert project.configurations.default.allArtifacts.files.files.toList().first() ==
                project.file("build/distributions/${project.name}-${project.version}.war")

    }

    def "version is included in war file name"() {
        given:
        project.version = '1.0'
        project.evaluate()
        List<File> artifactFiles = project.configurations.runtime.artifacts.files.files as List

        expect:
        GrailsWarTask war = project.tasks.getByName('war')
        assert war.outputFile.path == project.file("build/distributions/${project.name}-1.0.war").path

        and:
        assert artifactFiles.size() == 1
        assert artifactFiles.first() == project.file("build/distributions/${project.name}-1.0.war")

        and:
        assert project.configurations.default.allArtifacts.files.files.toList().first() ==
                project.file("build/distributions/${project.name}-1.0.war")

    }

    def "zip file is configured as runtime artifact for plugin"() {
        given:
        project = ProjectBuilder.builder().build()

        project.file("${project.name.capitalize()}GrailsPlugin.groovy") << """
class ${project.name.capitalize()}GrailsPlugin { }
"""
        project.apply plugin: "nebula.grails"
        project.grails.grailsVersion = '2.0.0'
        project.evaluate()
        List<File> artifactFiles = project.configurations.runtime.artifacts.files.files as List

        expect:
        assert artifactFiles.size() == 1
        assert artifactFiles.first() == project.file("grails-${project.name}-${project.version}.zip")

        and:
        assert project.configurations.default.allArtifacts.files.files.toList().first() ==
                project.file("grails-${project.name}-${project.version}.zip")

        and:
        assert project.tasks.findByName('packagePlugin')
    }

    def "version included in zip file artifact for plugin"() {
        given:
        project = ProjectBuilder.builder().build()

        project.file("${project.name.capitalize()}GrailsPlugin.groovy") << """
class ${project.name.capitalize()}GrailsPlugin { }
"""
        project.apply plugin: "nebula.grails"
        project.grails.grailsVersion = '2.0.0'
        project.version = '1.0'
        project.evaluate()
        List<File> artifactFiles = project.configurations.runtime.artifacts.files.files as List

        expect:
        GrailsPluginPackageTask war = project.tasks.getByName('packagePlugin')
        assert war.outputFile.path == project.file("grails-${project.name}-1.0.zip").path

        and:
        assert artifactFiles.size() == 1
        assert artifactFiles.first() == project.file("grails-${project.name}-1.0.zip")

        and:
        assert project.configurations.default.allArtifacts.files.files.toList().first() ==
                project.file("grails-${project.name}-1.0.zip")

    }

    def "command defaults to task name"() {
        given:
        def task = grailsTask("compile")

        expect:
        task.command == "compile"

        when:
        task.command = "test"

        then:
        task.command == "test"
    }

    def "can log task classpath"() {
        given:
        def task = grailsTask("compile")
        task.compileClasspath = project.files("c")
        task.runtimeClasspath = project.files("r")
        task.testClasspath = project.files("t")
        task.bootstrapClasspath = project.files("b")

        when:
        task.logClasspaths()

        then:
        notThrown(Exception)
    }

    def "grails.nebula plugin can be applied after java plugin"() {
        given:
        project = ProjectBuilder.builder().build()

        project.apply plugin: "java"
        project.apply plugin: "nebula.grails"
        project.grails.grailsVersion = '2.0.0'
        project.evaluate()

        expect:
        assert project.tasks.findByName('test')
    }

    def "grails.nebula plugin can be applied after implicitly applied java plugin"() {
        given:
        project = ProjectBuilder.builder().build()

        project.apply plugin: com.gorylenko.GitPropertiesPlugin
        project.apply plugin: "nebula.grails"
        project.grails.grailsVersion = '2.0.0'
        project.evaluate()

        expect:
        assert project.tasks.findByName('test')
    }
}
