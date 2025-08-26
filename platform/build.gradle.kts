plugins {
    id("io.micronaut.build.internal.bom")
}

repositories {
    mavenCentral()
    gradlePluginPortal() // needed for checkBom task to resolve plugin dependencies defined in kotest-pom
}

micronautBom {
    propertyName.set("platform")
    extraExcludedProjects.add("parent")

    suppressions {
        // https://github.com/micronaut-projects/micronaut-core/pull/7631#issuecomment-1174702395
        bomAuthorizedGroupIds.put(
                "io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom",
                setOf(
                    "io.opentelemetry",
                    "io.opentelemetry.instrumentation",
                    "io.opentelemetry.javaagent",
                    "io.opentelemetry.javaagent.instrumentation"
                )
        )
        bomAuthorizedGroupIds.put(
                "io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha",
                setOf(
                    "io.opentelemetry",
                    "io.opentelemetry.semconv",
                    "io.opentelemetry.instrumentation",
                    "io.opentelemetry.javaagent",
                    "io.opentelemetry.javaagent.instrumentation"
                )
        )
        bomAuthorizedGroupIds.put(
                "io.zipkin.brave:brave-bom",
                setOf(
                    "io.zipkin.brave",
                    "io.zipkin.zipkin2",
                    "io.zipkin.reporter2",
                    "io.zipkin.proto3"
                )
        )
        bomAuthorizedGroupIds.put(
                "io.zipkin.reporter2:zipkin-reporter-bom",
                setOf(
                    "io.zipkin.reporter2",
                    "io.zipkin.zipkin2",
                    "io.zipkin.proto3"
                )
        )
        bomAuthorizedGroupIds.put(
            "io.projectreactor:reactor-bom",
            setOf("io.projectreactor", "org.reactivestreams")
        )
        // micronaut-oraclecloud (since v3.6.0) pulls in the ojdbc-bom which imports dependencies from many groups
        bomAuthorizedGroupIds.put(
            "com.oracle.database.jdbc:ojdbc-bom",
            setOf(
                "com.oracle.database.ha",
                "com.oracle.database.nl",
                "com.oracle.database.nls",
                "com.oracle.database.observability",
                "com.oracle.database.security",
                "com.oracle.database.xml",
            )
        )

        dependencies.add("io.zipkin.reporter2:zipkin-reporter-bom:3.5.1")
        dependencies.add("io.zipkin.brave:brave-instrumentation-benchmarks:6.0.3")

        dependencies.add("io.opentelemetry:opentelemetry-bom:1.52.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.40.0-alpha")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:1.33.5")

        // This is in the micrometer BOM for version 1.13.3, but the module is never published
        // See https://github.com/micrometer-metrics/micrometer/issues/5395
        dependencies.add("io.micrometer:concurrency-tests:1.13.3")

        acceptedLibraryRegressions.add("micronaut-oraclecloud-bmc-applicationmigration")
        acceptedLibraryRegressions.add("kafka")
        acceptedVersionRegressions.add("kafka-compat")

        // Removed reactor-netty-http removed in https://github.com/micronaut-projects/micronaut-r2dbc/commit/ae5f341d26b89800b45caffd1494771948584588
        acceptedVersionRegressions.add("reactor-netty")
        acceptedLibraryRegressions.add("reactor-netty-http")

        // Removed in https://github.com/micronaut-projects/micronaut-r2dbc/releases/tag/v6.0.0
        acceptedVersionRegressions.add("r2dbc-io-asyncer-mysql")
        acceptedLibraryRegressions.add("r2dbc-io-asyncer-mysql")

        // Micronaut Coherence 5 regressions
        acceptedLibraryRegressions.add("coherence-java-client")
        acceptedLibraryRegressions.add("micronaut-coherence-grpc-test")
        acceptedLibraryRegressions.add("micronaut-coherence-grpc-client")
        acceptedLibraryRegressions.add("coherence-grpc-proxy")

        // Netty 4.2 moved these from incubator to the Netty monorepo
        acceptedVersionRegressions.add("netty-iouring")
        acceptedVersionRegressions.add("netty-http3")
        acceptedLibraryRegressions.add("netty-incubator-codec-http3")

        // Langchain4j BOM no longer imported
        acceptedVersionRegressions.add("langchain4j")
        acceptedLibraryRegressions.add("boms-langchain4j")

        // https://github.com/micronaut-projects/micronaut-oracle-cloud/issues/1151
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-distributeddatabase:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-onesubscription:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-dataintegration:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-identity:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-mngdmac:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-datasafe:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-certificatesmanagement:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-encryption:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-dns:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-ons:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-datascience:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-datalabelingservice:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-identitydataplane:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-modeldeployment:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-ocicontrolcenter:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-cims:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-datacatalog:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-objectstorage-generated:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-events:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-devops:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-dts:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-dblm:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-opa:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-identitydomains:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-monitoring:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-circuitbreaker:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-datalabelingservicedataplane:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-dataflow:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-ocvp:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-oce:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-filestorage:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-disasterrecovery:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-delegateaccesscontrol:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-apmconfig:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-governancerulescontrolplane:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-email:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-marketplacepublisher:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-core:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-cloudbridge:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-oda:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-demandsignal:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-atp:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-adm:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-generativeaiagent:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-audit:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-ailanguage:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-healthchecks:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-apiaccesscontrol:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-apmcontrolplane:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-emaildataplane:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-budget:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-computeinstanceagent:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-aivision:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-mediaservices:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-cloudguard:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-clusterplacementgroups:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-dashboardservice:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-functions:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-apmtraces:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-desktops:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-aianomalydetection:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-accessgovernancecp:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-autoscaling:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-aispeech:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-generativeaiagentruntime:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-keymanagement:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-emwarehouse:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-apigateway:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-apmsynthetics:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-capacitymanagement:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-containerengine:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-analytics:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-bds:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-computecloudatcustomer:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-fleetappsmanagement:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-nosql:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-database:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-cloudmigrations:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-appmgmtcontrol:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-fusionapps:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-genericartifactscontent:5.3.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-aidocument:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-loggingsearch:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-bastion:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-generativeaiinference:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-licensemanager:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-databasemanagement:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-loganalytics:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-integration:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-mysql:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-containerinstances:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-certificates:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-announcementsservice:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-loadbalancer:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-fleetsoftwareupdate:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-managementdashboard:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-objectstorage:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-blockchain:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-generativeai:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-artifacts:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-globallydistributeddatabase:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-lustrefilestorage:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-limits:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-databasemigration:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-logging:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-jms:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-networkfirewall:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-lockbox:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-marketplace:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-objectstorage-extensions:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-goldengate:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-managementagent:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-databasetools:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-loggingingestion:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-networkloadbalancer:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-marketplaceprivateoffer:5.2.1")
        dependencies.add("io.micronaut.oraclecloud:micronaut-oraclecloud-bmc-jmsjavadownloads:5.2.1")
        dependencies.add("io.opentelemetry:opentelemetry-bom:1.50.0")
        dependencies.add("io.opentelemetry.semconv:opentelemetry-semconv:1.32.0")
        dependencies.add("io.opentelemetry:opentelemetry-bom-alpha:1.50.0-alpha")
        dependencies.add("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.16.0")
    }
}

micronautBuild {
    binaryCompatibility.enabled.set(version != "4.0.0-SNAPSHOT")
}

tasks {
    // This is a workaround for the `jackson-databind` version being removed from the catalog
    // because it's not referenced anywhere anymore. However we must keep it for backwards
    // compatibility. This canbe removed after the next major release.
    if (version.toString().startsWith("4.")) {
        generateCatalogAsToml {
            doLast {
                val catalogFile = outputFile.get().asFile.readLines()
                // append one line after the first line which starts with `jackson = `
                val newCatalogFile = catalogFile.joinToString("\n") {
                    if (it.startsWith("jackson = ")) {
                        var versionPart = it.substringAfterLast(" = ")
                        "$it\n#@NextMajorVersion @Deprecated Delete in Micronaut Framework 5.\njackson-databind = $versionPart"
                    } else {
                        it
                    }
                }
                outputFile.get().asFile.writeText(newCatalogFile)
            }
        }
    }
    checkVersionCatalogCompatibility {
        doFirst {
            println(baseline.get().asFile)
            println(current.get().asFile)
        }
    }
}
