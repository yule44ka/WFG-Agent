import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":agents:agents-ext"))
                api(project(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client"))
                api(project(":prompt:prompt-executor:prompt-executor-llms-all"))
                api(project(":prompt:prompt-tokenizer"))

                api(kotlin("test"))

                api(libs.jetbrains.annotations)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                implementation(libs.logback.classic)
                implementation(libs.oshai.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":agents:agents-features:agents-features-event-handler"))
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.params)
                implementation(libs.ktor.client.cio)
            }
        }
    }

    explicitApi()
}

// Configure the publication to use the Grazie Public Maven repository
publishToGraziePublicMaven()
