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

package com.netflix.nebula.grails.integ

import org.grails.launcher.version.GrailsVersion
import spock.lang.Unroll

@Unroll
class InitSpec extends IntegSpec {
    def "can execute grails #initTask for #grailsVersion project"() {
        given:
        settingsFile.delete()
        settingsFile << """
            rootProject.name = 'grails-project'
        """.stripIndent()

        buildFile << """
            grails.grailsVersion '$grailsVersion'
        """.stripIndent()

        if (grailsVersion.is(2, 1)) {
            buildFile << """
                grails.groovyVersion '${grailsVersion.is(2,1,0) ? "1.8.6" : "1.8.8"}'
            """.stripIndent()
        }

        if (grailsVersion.is(2, 2)) {
            buildFile << """
                grails.groovyVersion '2.0.5'
            """.stripIndent()
        }

        if (grailsVersion.is(2, 3)) {
            buildFile << """
                grails.groovyVersion '2.1.9'

                dependencies {
                    bootstrap 'org.grails.plugins:tomcat:7.0.50'
                }
            """.stripIndent()
        }

        if (grailsVersion.is(2, 4)) {
            buildFile << """
                grails.groovyVersion '2.3.1'

                dependencies {
                    bootstrap 'org.grails.plugins:tomcat:7.0.52.1'
                }
            """.stripIndent()
        }

        when:
        runTasksSuccessfully(initTask)

        then:
        file("grails-app").exists()

        when:
        file("test/integration/test/SomeTest.groovy") << """
            package test

            import org.junit.Test

            class SomeTest {
                @Test
                void something() {
                    assert true
                }
            }
        """.stripIndent()

        then:
        runTasksSuccessfully("test")

        where:
        versionAndTask << ["2.3.5", "2.4.0"].collectMany { String version ->
            ['init', 'init-plugin'].collect { String task ->
                [task: task, version: GrailsVersion.parse(version)]
            }
        }
        grailsVersion = (GrailsVersion) versionAndTask.version
        initTask = (String) versionAndTask.task
    }
}
