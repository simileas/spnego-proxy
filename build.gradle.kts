plugins {
  distribution
  java
  checkstyle
}

group = "com.nopadding.internal"
version = "1.0.0"

repositories {
  mavenCentral()
}

configure<JavaPluginConvention> {
  setSourceCompatibility(1.8)
  setTargetCompatibility(1.8)
}

dependencies {
  testCompile("junit:junit:4.12")
  testCompile("org.apache.httpcomponents:httpclient:4.5.12")
  compile("org.slf4j:slf4j-log4j12:1.7.30")
  compile("io.netty:netty-codec-http:4.1.50.Final")
  compile("commons-cli:commons-cli:1.3")
  compileOnly("org.projectlombok:lombok:1.18.12")
  testCompileOnly("org.projectlombok:lombok:1.18.12")
  annotationProcessor("org.projectlombok:lombok:1.18.12")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.12")
}

checkstyle {
  configFile = file("${configDir}/google_checks.xml")
  dependencies {
    checkstyle("com.puppycrawl.tools:checkstyle:8.31")
    checkstyle("com.github.sevntu-checkstyle:sevntu-checks:1.37.1")
  }
}

distributions {
  main {
    contents {
      into("bin") {
        from("bin") {
          include("*.sh")
        }
        from("src/main/resources")
      }
      into("lib") {
        from(tasks.jar)
        from(configurations.compile)
      }
    }
  }
}

tasks.named<Tar>("distTar") {
  compression = Compression.GZIP;
}
