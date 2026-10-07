import io.micronaut.build.internal.RewritePom
import io.micronaut.build.internal.VerifyExecArguments

plugins {
    id("io.micronaut.build.internal.maven-pom")
}

pom {
    catalogPropertyPrefix.set("parent")
    includeBomPropertiesStringListProperty.set(listOf(
        libs.boms.micronaut.grpc.get().toString(),
        libs.boms.micronaut.picocli.get().toString()
    ))
}

tasks {
    val verifyExecArguments = register<VerifyExecArguments>("verifyExecArguments") {
        description = "Verifies the command line that the parent POM gives exec:exec."
        group = "verification"
        pomFile.set(named<RewritePom>("rewritePomFile").flatMap { it.outputFile })
        // No -Dcom.sun.management.jmxremote: tools start the JMX agent on demand when they attach.
        expectedArguments.set(listOf("-classpath", "<classpath/>", "-XX:TieredStopAtLevel=1", "\${exec.mainClass}"))
    }

    check {
        dependsOn(verifyExecArguments)
    }
}
