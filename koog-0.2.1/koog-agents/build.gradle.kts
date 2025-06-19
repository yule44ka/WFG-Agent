import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
}

val excluded = setOf(
    ":agents:agents-test",
    ":examples",
    ":integration-tests",
    project.path, // the current project should not depend on itself
)

val included = setOf(
    ":agents:agents-core",
    ":agents:agents-ext",
    ":agents:agents-features:agents-features-common",
    ":agents:agents-features:agents-features-event-handler",
    ":agents:agents-features:agents-features-memory",
    ":agents:agents-features:agents-features-trace",
    ":agents:agents-features:agents-features-tokenizer",
    ":agents:agents-mcp",
    ":agents:agents-tools",
    ":agents:agents-utils",
    ":embeddings:embeddings-base",
    ":embeddings:embeddings-llm",
    ":prompt:prompt-cache:prompt-cache-files",
    ":prompt:prompt-cache:prompt-cache-model",
    ":prompt:prompt-cache:prompt-cache-redis",
    ":prompt:prompt-executor:prompt-executor-cached",
    ":prompt:prompt-executor:prompt-executor-clients",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client",
    ":prompt:prompt-executor:prompt-executor-clients:prompt-executor-ollama-client",
    ":prompt:prompt-executor:prompt-executor-llms",
    ":prompt:prompt-executor:prompt-executor-llms-all",
    ":prompt:prompt-executor:prompt-executor-model",
    ":prompt:prompt-llm",
    ":prompt:prompt-markdown",
    ":prompt:prompt-model",
    ":prompt:prompt-structure",
    ":prompt:prompt-tokenizer",
    ":prompt:prompt-xml"
)

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                val projects = rootProject.subprojects
                    .filterNot { it.path in excluded }
                    .filter { it.buildFile.exists() }

                val projectsPaths = projects.mapTo(sortedSetOf()) { it.path }

                val obsoleteIncluded = included - projectsPaths
                require(obsoleteIncluded.isEmpty()) {
                    "There are obsolete modules that are used for '${project.name}' main jar dependencies but no longer exist, please remove them from 'included' in ${project.name}/build.gradle.kts:\n" +
                            obsoleteIncluded.joinToString(",\n") { "\"$it\"" }
                }

                val notIncluded = projectsPaths - included
                require(notIncluded.isEmpty()) {
                    "There are modules that are not listed for '${project.name}' main jar dependencies, please add them to 'included' or 'excluded' in ${project.name}/build.gradle.kts:\n" +
                            notIncluded.joinToString(",\n") { "\"$it\"" }
                }

                projects.forEach {
                    val text = it.buildFile.readText()

                    require("import ai.grazie.gradle.publish.maven.Publishing.publishToGraziePublicMaven" in text) {
                        "Module ${it.path} is used as a dependency for '${project.name}' main jar. Hence, it should be published. If not, please mark it as excluded in ${project.name}/build.gradle.kts"
                    }

                    require("publishToGraziePublicMaven()" in text) {
                        "Module ${it.path} is used as a dependency for '${project.name}' main jar. Hence, it should be published. If not, please mark it as excluded in ${project.name}/build.gradle.kts"
                    }
                }

                projects.forEach {
                    api(project(it.path))
                }
            }
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        suppress.set(true)
    }
}

publishToGraziePublicMaven()
