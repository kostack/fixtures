plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("com.vanniktech.maven.publish") version "0.33.0"
  id("java-test-fixtures")
  id("com.diffplug.spotless") version "8.11.0"
  jacoco
}

val springBootVersion = "4.1.2"
val springShellVersion = "4.0.3"
val kotlinVersion: String by project
val packageId = "fixtures"
val packageName = "Fixtures"
group = "io.github.kostack"
version = providers.gradleProperty("projectVersion").getOrElse("1.0.0")
description = "Data fixtures"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  // API
  api("net.datafaker:datafaker:2.7.0")

  // Implementation
  implementation("org.fusesource.jansi:jansi:2.4.2")

  // Build and tests only: the Spring Boot and Spring Shell BOMs manage all versions below and are not published.
  listOf(
    platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"),
    platform("org.springframework.shell:spring-shell-dependencies:$springShellVersion")
  ).forEach {
    compileOnly(it)
    testImplementation(it)
  }
  testFixturesImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

  // Provided by the consuming application; not published as dependencies.
  listOf(
    "org.springframework:spring-context",
    "org.springframework.boot:spring-boot-autoconfigure",
    "org.springframework.shell:spring-shell-starter-jni",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core"
  ).forEach {
    compileOnly(it)
    testImplementation(it)
  }

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("io.mockk:mockk:1.14.11")
  testImplementation("com.ninja-squad:springmockk:5.0.1")
  testImplementation(kotlin("test"))
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  testFixturesImplementation("org.springframework.boot:spring-boot-starter")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
  }
}

// Test fixtures are for this project's tests only; don't publish them.
(components["java"] as AdhocComponentWithVariants).apply {
  withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
  withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}
// The publish plugin adds the fixtures' sources variant late, so skip it after evaluation.
afterEvaluate {
  configurations.findByName("testFixturesSourcesElements")?.let {
    (components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(it) { skip() }
  }
}

val installGitHook by tasks.registering {
  group = "build setup"
  description = "Installs a git pre-commit hook to enforce formatting rules"
  doLast {
    val hookSource = file("scripts/pre-commit")
    val hookTarget = file(".git/hooks/pre-commit")
    if (!hookTarget.exists()) {
      hookSource.copyTo(hookTarget, overwrite = false)
    }
    hookTarget.setExecutable(true)
  }
}

tasks.assemble {
  dependsOn(installGitHook)
}

tasks.test {
  useJUnitPlatform()
  dependsOn(installGitHook)
  finalizedBy(tasks.jacocoTestReport)
  // Needed by MockK's inline mocking agent and Spring Shell's JNI terminal on recent JDKs.
  jvmArgs("-XX:+EnableDynamicAgentLoading", "--enable-native-access=ALL-UNNAMED")

  testLogging {
    events("passed", "skipped", "failed")
  }
}

tasks.jacocoTestReport {
  reports {
    xml.required = true
    html.required = true
    csv.required = false
    html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    xml.outputLocation = layout.buildDirectory.file("jacocoReport.xml")
  }
}

tasks.jacocoTestCoverageVerification {
  violationRules {
    rule {
      limit {
        minimum = "0.6".toBigDecimal()
      }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktlint()
  }
  kotlinGradle {
    target("*.kts")
    ktlint()
  }
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  coordinates(
    groupId = group.toString(),
    artifactId = packageId,
    version = version.toString()
  )

  pom {
    name = packageName
    description = project.description
    url = "https://github.com/kostack/$packageId"

    licenses {
      license {
        name = "MIT License"
        url = "https://opensource.org/licenses/MIT"
      }
    }

    developers {
      developer {
        id = "zender"
        name = "Nikolay Georgiev"
        url = "https://github.com/zender"
      }
    }

    scm {
      connection = "scm:git:git://github.com/kostack/$packageId.git"
      developerConnection = "scm:git:ssh://github.com/kostack/$packageId.git"
      url = "https://github.com/kostack/$packageId"
    }
  }
}
