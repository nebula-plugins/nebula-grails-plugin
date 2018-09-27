package com.netflix.nebula.grails

import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.Instantiator

/**
 * Configures the source sets with the Grails source structure. Much of this code is replicated from the Java and
 * Groovy plugins.
 */
class GrailsSourceSetConfigurator {

    private final Instantiator instantiator
    private final FileResolver fileResolver

    GrailsSourceSetConfigurator(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator
        this.fileResolver = fileResolver
    }

    void configure(Project project, GrailsProject grailsProject) {

        //Add the 'groovy' DSL extension to the source sets

        createMainSourceSet(project, grailsProject)
        createTestSourceSet(project, grailsProject)
    }

    /**
     * Configure the main source sets
     */
    void createMainSourceSet(Project project, GrailsProject grailsProject) {
        def sourceSetClosure = {
            main {
                groovy {
                    srcDirs = [
                        'grails-app/conf',
                        'grails-app/controllers',
                        'grails-app/domain',
                        'grails-app/services',
                        'grails-app/taglib',
                        'grails-app/utils',
                        'src/groovy',
                        'scripts'
                    ]
                    filter {
                        exclude 'grails-app/conf/hibernate'
                        exclude 'grails-app/conf/spring'
                    }
                }
                resources {
                    srcDirs = [
                        'grails-app/conf/hibernate',
                        'grails-app/conf/spring',
                        'grails-app/views',
                        'web-app'
                    ]
                }
                java {
                    srcDirs = [
                        'src/java'
                    ]
                }
                output.with {
                    ['plugin-build-classes', 'plugin-classes', 'plugin-provided-classes'].each {
                        dir buildPath(grailsProject, it)
                    }
                    dir 'buildPlugins'
                    resourcesDir = buildPath(grailsProject, 'resources')
                }
            }
        }

        project.sourceSets(sourceSetClosure)
        project.sourceSets.main.groovy.outputDir = new File(buildPath(grailsProject, 'classes'))
    }

    String buildPath(GrailsProject project, String path) {
        return new File(project.projectWorkDir, path).path
    }

    /**
     * Configure the test source set
     */
    void createTestSourceSet(Project project, GrailsProject grailsProject) {
        def sourceSetClosure = {
            test {
                groovy {
                    srcDirs = [
                        'test/functional',
                        'test/integration',
                        'test/unit'
                    ]
                }
            }
        }

        project.sourceSets(sourceSetClosure)
        project.sourceSets.main.groovy.outputDir = new File(buildPath(grailsProject, 'classes'))
    }
}