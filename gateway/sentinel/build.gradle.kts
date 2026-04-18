plugins {
	java
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"

  // gRPC + protobuf code generation
  id("com.google.protobuf") version "0.9.4"
}

group = "com.velo.sentinel"
version = "0.0.1-SNAPSHOT"

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
	//implementation("org.springframework.boot:spring-boot-starter")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

// protobuf {
//     protoc {
//         artifact = "com.google.protobuf:protoc:3.25.3"
//     }

//     plugins {
//         id("grpc") {
//             artifact = "io.grpc:protoc-gen-grpc-java:1.64.0"
//         }
//     }

//     generateProtoTasks {
//         all().forEach { task ->
//             task.plugins {
//                 id("grpc")
//             }
//         }
//     }
// }

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
