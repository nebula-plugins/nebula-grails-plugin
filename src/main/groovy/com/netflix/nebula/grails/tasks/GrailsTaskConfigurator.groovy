package com.netflix.nebula.grails.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import com.netflix.nebula.grails.GrailsProject

/**
 * Configures the default Grails tasks and wires them into the standard build process:
 * ('build', 'check', 'test', 'assemble')
 */
class GrailsTaskConfigurator {

    public static final String GRAILS_INIT_TASK = 'init'
    public static final String GRAILS_INIT_PLUGIN_TASK = 'init-plugin'
    public static final String GRAILS_TEST_TASK = JavaPlugin.TEST_TASK_NAME
    public static final String GRAILS_RUN_TASK = 'run'
    public static final String GRAILS_PACKAGE_PLUGIN_TASK = 'packagePlugin'
    public static final String GRAILS_WAR_TASK = WarPlugin.WAR_TASK_NAME

    void configure(Project project, GrailsProject grailsProject) {
        //Create the Grails init task
        project.tasks.create(GRAILS_INIT_TASK, GrailsInitTask)

        //Create the Grails init plugin task
        project.tasks.create(GRAILS_INIT_PLUGIN_TASK, GrailsInitTask).with {
            command = 'create-plugin'
            description = 'Creates a new Grails plugin in the current directory'
        }

        project.tasks.getByName(BasePlugin.CLEAN_TASK_NAME) {
            delete 'buildPlugins', 'plugin.xml', "grails-${project.name}-${-> project.version}.zip"
        }

        //Set up the proper assemble task and adds it's artifact to the configuration
        configureAssemble(grailsProject, project)

        //Add the run task...this is allowable for both applications and plugins
        project.tasks.create(GRAILS_RUN_TASK, GrailsTask).with {
            command = 'run-app'
            description = 'Starts the Grails application'
            reload = true
        }

        //Create the Grails test task.
        project.tasks.create(name: GRAILS_TEST_TASK, type: GrailsTestTask, overwrite: true)

        //Create a task rule that converts any task with that starts with 'grail-' into an invocation of
        //the corresponding Grails script
        project.tasks.addRule("Pattern: ${GrailsTask.GRAILS_TASK_PREFIX}<script-name>: Execute the specified Grails script") { String name ->
            if (name.startsWith(GrailsTask.GRAILS_TASK_PREFIX)) {
                project.task(name, type: GrailsTask) {
                    String scriptName = name - GrailsTask.GRAILS_TASK_PREFIX
                    command = scriptName
                    if (project.hasProperty(GrailsTask.GRAILS_ARGS_PROPERTY)) {
                        args = project.property(GrailsTask.GRAILS_ARGS_PROPERTY)
                    }
                    if (project.hasProperty(GrailsTask.GRAILS_ENV_PROPERTY)) {
                        env = project.property(GrailsTask.GRAILS_ENV_PROPERTY)
                    }
                    if (scriptName == 'run-app') {
                        reload = true
                    }
                }
            }
        }

        project.tasks.withType(GrailsTask) {
            if (project.hasProperty(GrailsTask.GRAILS_DEBUG_PROPERTY)) {
                jvmOptions.debug = Boolean.parseBoolean(project.property(GrailsTask.GRAILS_DEBUG_PROPERTY))
            }
        }

        //Setup some tasks that mimic the Java build pattern
        configureJavaStyleTasks(project)
    }

    private GrailsAssembleTask createPackagePluginTask(Project project) {
        project.tasks.create(GRAILS_PACKAGE_PLUGIN_TASK, GrailsPluginPackageTask)
        return project.tasks.findByName(GRAILS_PACKAGE_PLUGIN_TASK)
    }

    private GrailsAssembleTask createWarTask(Project project) {
        GrailsAssembleTask war = project.tasks.create(GRAILS_WAR_TASK, GrailsWarTask)
        war.env = 'production'
        return project.tasks.findByName(GRAILS_WAR_TASK)
    }

    /**
     * Wire up the Grails project into the standard Gradle Java build flow (mimic the Java Plugin)
     */
    private void configureJavaStyleTasks(Project project) {
        configureCheck(project)
        configureBuild(project)
        configureTest(project)
    }

    /**
     * Add the 'check' task
     */
    private void configureCheck(Project project) {
        if (!project.tasks.findByName(JavaBasePlugin.CHECK_TASK_NAME)) {
            project.tasks.create(JavaBasePlugin.CHECK_TASK_NAME)
        }
        Task checkTask = project.tasks.findByName(JavaBasePlugin.CHECK_TASK_NAME)
        checkTask.setDescription("Runs all checks.")
        checkTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP)
    }

    /**
     * Add the 'build' task and wire it to 'check' and 'assemble'
     */
    private void configureBuild(Project project) {
        if (!project.tasks.findByName(JavaBasePlugin.BUILD_TASK_NAME)) {
            project.tasks.create(JavaBasePlugin.BUILD_TASK_NAME, DefaultTask.class)
        }
        DefaultTask buildTask = project.tasks.findByName(JavaBasePlugin.BUILD_TASK_NAME)
        buildTask.setDescription("Assembles and tests this project.")
        buildTask.setGroup(BasePlugin.BUILD_GROUP)
        buildTask.dependsOn(BasePlugin.ASSEMBLE_TASK_NAME)
        buildTask.dependsOn(JavaBasePlugin.CHECK_TASK_NAME)
    }

    /**
     * Add the 'test' task and wire it to 'check'
     */
    private void configureTest(Project project) {
        Task test = project.tasks.findByName(JavaPlugin.TEST_TASK_NAME)
        project.tasks.getByName(JavaBasePlugin.CHECK_TASK_NAME).dependsOn(test)
        test.setDescription("Runs the tests.")
    }

    private void configureAssemble(GrailsProject grailsProject, Project project) {
        //Depending on the project type, configure either the package-plugin or war tasks
        //as the assemble task
        GrailsAssembleTask grailsAssemble = grailsProject.pluginProject ?
            createPackagePluginTask(project) : createWarTask(project)

        project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn grailsAssemble
        project.configurations.default.extendsFrom(project.configurations.runtime)
        project.afterEvaluate {
            project.artifacts.add('runtime', grailsAssemble.outputFile) {
                type grailsAssemble.outputFile.path.tokenize('.').last()
                builtBy grailsAssemble
            }
        }
    }
}
