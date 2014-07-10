package com.netflix.nebula.grails.dependencies

import org.gradle.api.Project
import com.netflix.nebula.grails.GrailsProject
import org.grails.launcher.version.GrailsVersion

/**
 * Creates the proper dependency configuration instance for this Grails version.
 * Currently all project use the same configuration, but future version may require
 * more substantial customization.
 */
class DependencyConfigurerFactory {

    static DependencyConfigurer build(Project project, GrailsProject grailsProject) {
        GrailsVersion version = GrailsVersion.parse(grailsProject.grailsVersion)
        return new GrailsDependenciesConfigurer(project, grailsProject, version)
    }
}
