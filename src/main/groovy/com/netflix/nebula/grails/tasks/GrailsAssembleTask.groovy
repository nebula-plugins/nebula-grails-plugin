package com.netflix.nebula.grails.tasks

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile

class GrailsAssembleTask extends GrailsTask {

    @OutputFile
    final RegularFileProperty outputFile = project.objects.fileProperty()
}

class GrailsPluginPackageTask extends GrailsAssembleTask {

    GrailsPluginPackageTask() {
        outputFile.set(project.layout.projectDirectory.file(project.provider({
            "grails-${project.name}-${project.version}.zip" }
        )))
        command = 'package-plugin'
        description = 'Packages a grails plugin'
    }
}

class GrailsWarTask extends GrailsAssembleTask {

    GrailsWarTask() {
        outputFile.set(project.layout.projectDirectory.file(project.provider({
            "build/distributions/${project.name}-${project.version}.war"}
        )))
        command = "war"
        description = 'Generates the application WAR file'
    }

    @Override
    CharSequence getArgs() {
        return "${-> output} ${-> super.args}"
    }
}
