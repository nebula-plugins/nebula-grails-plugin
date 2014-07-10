package com.netflix.nebula.grails.integ

import nebula.test.IntegrationSpec

class IntegSpec extends IntegrationSpec {
    def setup() {
        buildFile << """
            import com.netflix.nebula.grails.tasks.GrailsTask

            version = "1.0"

            apply plugin: "nebula.grails"

            repositories {
                grails.central()
            }
        """.stripIndent()
    }
}
