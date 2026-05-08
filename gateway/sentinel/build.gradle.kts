plugins {
	java
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"

  // gRPC + protobuf code generation
  id("com.google.protobuf") version "0.9.4"
  id("me.champeau.jmh") version "0.7.3"
  jacoco
}

group = "com.velo.sentinel"
version = "0.0.1-SNAPSHOT"

jmh {
    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("us")
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    failOnError.set(true)
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {

  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")

  // Using spring-aspects directly to bypass the 'starter-aop' resolution bug
  implementation("org.springframework:spring-aspects:7.0.6")

  // Micrometer
  implementation("io.micrometer:micrometer-core:1.14.0")
  implementation("io.micrometer:micrometer-registry-prometheus:1.14.0")

  // Resilience & Circuit Breakers
  implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
  implementation("io.github.resilience4j:resilience4j-micrometer:2.2.0")

  // gRPC runtime
  implementation("io.grpc:grpc-netty-shaded:1.64.0")
  implementation("io.grpc:grpc-protobuf:1.64.0")
  implementation("io.grpc:grpc-stub:1.64.0")

  // protobuf runtime
  implementation("com.google.protobuf:protobuf-java:3.25.3")

  // Spring Boot 4 / Java 25 compatibility
  compileOnly("jakarta.annotation:jakarta.annotation-api:2.1.1")

  // Required for gRPC 1.64.0 generated code compatibility, emitting legacy annotation
  implementation("javax.annotation:javax.annotation-api:1.3.2")

  // tests
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure:4.0.5")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("it.ozimov:embedded-redis:0.7.3") {
      exclude(group = "org.slf4j", module = "slf4j-simple")
  }
  testImplementation("org.springframework.security:spring-security-test")

  // OpenTelemetry
  implementation(platform("io.opentelemetry:opentelemetry-bom:1.44.1"))
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry:opentelemetry-sdk")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  
  // Bridge Micrometer to OpenTelemetry
  implementation("io.micrometer:micrometer-tracing-bridge-otel:1.4.0")

  // Structured JSON Logging
  implementation("net.logstash.logback:logstash-logback-encoder:8.0")

  // Enterprise Security
  implementation("org.springframework.boot:spring-boot-starter-security")

  // JMH
  jmh("org.openjdk.jmh:jmh-core:1.37")
  jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

configure<com.google.protobuf.gradle.ProtobufExtension> {

    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }

    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.64.0"
        }
    }

    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs("build/generated/source/proto/main/java")
            srcDirs("build/generated/source/proto/main/grpc")
        }
    }
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "com/velo/sentinel/grpc/**",
                    "com/velo/sentinel/dev/**",
                    "com/velo/sentinel/SentinelJApplication.class"
                )
            }
        })
    )
}

// Ensure the application stops and ports are released on CTRL-C
tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
    // This allows the child process to receive the SIGINT from CTRL-C
    standardInput = System.`in`
}

// Custom task to kill any zombie processes on our specific ports before boot
val killStaleServers = tasks.register("killStaleServers") {
    group = "verification"
    description = "Kills any stale processes on ports 8080, 8001, and 9001."
    doLast {
        val ports = listOf(8080, 8001, 9001)
        val currentPid = ProcessHandle.current().pid()
        ports.forEach { port ->
            // Use lsof with -c java to only find Java processes, avoiding Docker cleanup
            // grep -v $currentPid ensures Gradle doesn't kill itself
            ProcessBuilder("sh", "-c", "lsof -ti :$port -sTCP:LISTEN -c java | grep -v $currentPid | xargs kill -9 2>/dev/null || true").start().waitFor()
        }
    }
}

// Make bootRun depend on the cleanup task
// Make bootRun depend on the cleanup task
// tasks.named("bootRun") {
//     dependsOn(killStaleServers)
// }
