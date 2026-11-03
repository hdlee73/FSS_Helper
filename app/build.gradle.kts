import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

val appVersion: String = (findProperty("appVersion") as String?) ?: "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 앱 버전·업데이트 레포를 코드에서 읽을 수 있게 BuildInfo.kt 를 만든다.
val generatedDir = layout.buildDirectory.dir("generated/buildinfo")
val generateBuildInfo by tasks.registering {
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(generatedDir)
    doLast {
        val f = generatedDir.get().file("io/github/hdlee73/fsssupporter/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            """
            package io.github.hdlee73.fsssupporter

            object BuildInfo {
                const val VERSION = "$version"
                const val REPO = "hdlee73/FSS_Helper"
            }
            """.trimIndent()
        )
    }
}
sourceSets.main { kotlin.srcDir(generatedDir) }
tasks.named("compileKotlin") { dependsOn(generateBuildInfo) }

dependencies {
    implementation(project(":law-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // Windows DPAPI(키 암호화 저장)
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    // 엑셀 내보내기 (Apache POI보다 훨씬 가볍다)
    // 기업 개황 PDF 저장(맑은 고딕을 필요한 글자만 넣는다)
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "io.github.hdlee73.fsssupporter.MainKt"
        jvmArgs += listOf("-Xms64m", "-Xmx512m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8", "-Dsun.java2d.d3d=true", "-Djava.net.useSystemProxies=true")

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            // 설치 파일은 Inno Setup 으로 만든다(.github/workflows). 여기서는 앱 이미지(createDistributable)만 쓴다.
            targetFormats(TargetFormat.Exe)
            packageName = "FSS_Helper"
            packageVersion = appVersion
            description = "FSS 업무 도우미"
            vendor = "이현덕 with Claude"
            copyright = "© 2026 이현덕"
            modules("java.net.http", "java.naming", "java.sql", "java.xml", "java.logging", "jdk.crypto.ec", "jdk.unsupported", "jdk.accessibility")
            windows {
                iconFile.set(project.file("src/main/resources/icons/app.ico"))
                menuGroup = "FSS Supporter"
                upgradeUuid = "4F1D7C3A-2B8E-4E51-9A6D-53C2E7A0B911"
                perUserInstall = true
            }
        }
    }
}
