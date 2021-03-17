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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.internal.reflect.Instantiator
import com.netflix.nebula.grails.dependencies.DependencyConfigurer
import com.netflix.nebula.grails.dependencies.DependencyConfigurerFactory
import com.netflix.nebula.grails.eclipse.GrailsEclipseConfigurator
import com.netflix.nebula.grails.idea.GrailsIdeaConfigurator
import com.netflix.nebula.grails.internal.DefaultGrailsProject
import com.netflix.nebula.grails.tasks.GrailsTask
import com.netflix.nebula.grails.tasks.GrailsTaskConfigurator

import javax.inject.Inject

class GrailsPlugin implements Plugin<Project> {

    private final Instantiator instantiator
    private final FileResolver fileResolver

    @Inject
    GrailsPlugin(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator
        this.fileResolver = fileResolver
    }

    void apply(Project project) {
        project.plugins.apply(BasePlugin)
        project.plugins.apply(GroovyBasePlugin)

        DefaultGrailsProject grailsProject = project.extensions.create('grails', DefaultGrailsProject, project)
        project.convention.plugins.put('grails', grailsProject)

        grailsProject.conventionMapping.with {
            map("projectDir") { project.projectDir }
            map("projectWorkDir") { project.buildDir }
        }

        Configuration bootstrapConfiguration = getOrCreateConfiguration(project, "bootstrap")

        Configuration compileConfiguration = getOrCreateConfiguration(project, "compile")
        Configuration compileOnlyConfiguration = getOrCreateConfiguration(project, "compileOnly")
        Configuration providedConfiguration = getOrCreateConfiguration(project, "providedClasspath")
        providedConfiguration.extendsFrom(compileOnlyConfiguration)
        providedConfiguration.setCanBeResolved(true)
        Configuration runtimeConfiguration = getOrCreateConfiguration(project, "runtime")
        Configuration testConfiguration = getOrCreateConfiguration(project, "test")
        Configuration resourcesConfiguration = getOrCreateConfiguration(project, "resources")
        Configuration springloadedConfiguration = getOrCreateConfiguration(project, "springloaded")

        runtimeConfiguration.extendsFrom(compileConfiguration)
        testConfiguration.extendsFrom(runtimeConfiguration)

        grailsProject.onSetGrailsVersion { String grailsVersion ->
            DependencyConfigurer dependenciesUtil = DependencyConfigurerFactory.build(project, grailsProject)
            dependenciesUtil.configureBootstrapClasspath(bootstrapConfiguration)
            dependenciesUtil.configureCompileOnlyClasspath(compileOnlyConfiguration)
            dependenciesUtil.configureCompileClasspath(compileConfiguration)
            dependenciesUtil.configureRuntimeClasspath(runtimeConfiguration)
            dependenciesUtil.configureTestClasspath(testConfiguration)
            dependenciesUtil.configureResources(resourcesConfiguration)
        }
        grailsProject.onSetGroovyVersion { String groovyVersion ->
            DependencyConfigurer dependenciesUtil = DependencyConfigurerFactory.build(project, grailsProject)
            dependenciesUtil.configureGroovyBootstrapClasspath(bootstrapConfiguration)
            dependenciesUtil.configureGroovyCompileClasspath(compileConfiguration)

            project.configurations.all { Configuration config ->
                config.resolutionStrategy {
                    eachDependency { DependencyResolveDetails details ->
                        if (details.requested.group == 'org.codehaus.groovy') {
                            if (details.requested.name == 'groovy-all') {
                                details.useVersion groovyVersion
                            }
                            if (details.requested.name == 'groovy') {
                                details.useTarget group: details.requested.group, name: 'groovy-all', version: groovyVersion
                            }
                        }
                    }
                }
            }
        }

        configureSourceSets(project, grailsProject)
        configureTasks(project, grailsProject)
        project.tasks.withType(GrailsTask) { GrailsTask task ->
            ConventionMapping conventionMapping = task.conventionMapping
            conventionMapping.with {
                map("projectDir") { grailsProject.projectDir }
                map("projectWorkDir") { grailsProject.projectWorkDir }
                map("grailsVersion") { grailsProject.grailsVersion }
                map("pluginProject") { grailsProject.pluginProject }

                map("bootstrapClasspath") { bootstrapConfiguration }

                map("providedClasspath") { providedConfiguration }
                map("compileClasspath") { compileConfiguration }
                map("runtimeClasspath") { runtimeConfiguration }
                map("testClasspath") { testConfiguration }
                map("sourceSets") { project.sourceSets }

                map("springloaded") {
                    if (springloadedConfiguration.dependencies.empty) {
                        DependencyConfigurer dependenciesUtil = DependencyConfigurerFactory.build(project, grailsProject)
                        dependenciesUtil.configureSpringloaded(springloadedConfiguration)
                    }

                    def lenient = springloadedConfiguration.resolvedConfiguration.lenientConfiguration
                    if (lenient.unresolvedModuleDependencies) {
                        def springloadedDependency = springloadedConfiguration.dependencies.toList().first()
                        project.logger.warn("Failed to resolve springloaded dependency: $springloadedDependency (reloading will be disabled)")
                        null
                    } else {
                        springloadedConfiguration
                    }
                }
            }

            doFirst {
                if (grailsProject.grailsVersion == null) {
                    throw new InvalidUserDataException("You must set 'grails.grailsVersion' property before Grails tasks can be run")
                }
            }
        }
        configureIdea(project)
        configureEclipse(project)

        project.plugins.withType(PublishingPlugin) {
            project.afterEvaluate {
                project.publishing {
                    publications {
                        withType(IvyPublication) {
                            artifact(getGrailsOutputArtifact(project)) {
                                conf "runtime"
                            }
                            descriptor.withXml { XmlProvider xml ->
                                def root = xml.asNode()
                                def deps = root.dependencies[0]
                                deps.@defaultconfmapping = "%->default"
                                def runtimeClasspath = project.configurations.runtimeClasspath
                                Map<String, String> revConstraintLookup = runtimeClasspath.allDependencies.collectEntries { Dependency dep ->
                                    [(dep.group + ':' + dep.name): dep.version]
                                }
                                runtimeClasspath.resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency dep ->
                                    deps.appendNode('dependency', ['org'          : dep.moduleGroup,
                                                                   'name'         : dep.moduleName,
                                                                   'rev'          : dep.moduleVersion,
                                                                   'conf'         : 'runtime->default',
                                                                   'revConstraint': revConstraintLookup[(dep.moduleGroup + ':' + dep.moduleName)]])
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    RegularFileProperty getGrailsOutputArtifact(Project project) {
        if (isGrailsPluginProject(project)) {
            project.tasks.findByName(GrailsTaskConfigurator.GRAILS_PACKAGE_PLUGIN_TASK).outputFile
        } else {
            project.tasks.findByName(GrailsTaskConfigurator.GRAILS_WAR_TASK).outputFile
        }
    }

    File grailsPluginFile(Project project) {
        project.projectDir.listFiles().find { it.name.endsWith('GrailsPlugin.groovy') }
    }

    File grailsPluginTemplateFile(Project project) {
        project.projectDir.listFiles().find { it.name.endsWith('GrailsPlugin.groovy.template') }
    }

    boolean isGrailsPluginProject(Project project) {
        grailsPluginFile(project) as boolean ||
                grailsPluginTemplateFile(project) as boolean // We will have a file with the existence of a template
    }

    void configureTasks(Project project, GrailsProject grailsProject) {
        new GrailsTaskConfigurator().configure(project, grailsProject)
    }

    void configureSourceSets(Project project, GrailsProject grailsProject) {
        new GrailsSourceSetConfigurator(instantiator, fileResolver).configure(project, grailsProject)
    }

    void configureIdea(Project project) {
        new GrailsIdeaConfigurator().configure(project)
    }

    void configureEclipse(Project project) {
        new GrailsEclipseConfigurator().configure(project)
    }

    Configuration getOrCreateConfiguration(Project project, String name) {
        ConfigurationContainer container = project.configurations
        container.findByName(name) ?: container.create(name)
    }
}
