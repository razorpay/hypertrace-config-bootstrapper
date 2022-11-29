import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork

plugins {
  java
  application
  jacoco
  id("org.hypertrace.docker-java-application-plugin")
  id("org.hypertrace.docker-publish-plugin")
  id("org.hypertrace.integration-test-plugin")
  id("org.hypertrace.jacoco-report-plugin")
}

application {
  mainClass.set("org.hypertrace.core.bootstrapper.ConfigBootstrapper")
}

hypertraceDocker {
  defaultImage {
    javaApplication {
      serviceName.set("${project.name}")
    }
  }
}

tasks.register<DockerCreateNetwork>("createIntegrationTestNetwork") {
  networkName.set("config-bootstrapper-int-test")
  networkId.set("config-bootstrapper-int-test")
}

tasks.register<com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork>("removeIntegrationTestNetwork") {
  networkId.set("config-bootstrapper-int-test")
}

tasks.register<DockerPullImage>("pullMongoImage") {
  image.set("mongo:4.4.0")
}

tasks.register<DockerCreateContainer>("createMongoContainer") {
  dependsOn("createIntegrationTestNetwork")
  dependsOn("pullMongoImage")
  targetImageId(tasks.getByName<DockerPullImage>("pullMongoImage").image)
  containerName.set("mongo-local")
  hostConfig.network.set(tasks.getByName<DockerCreateNetwork>("createIntegrationTestNetwork").networkId)
  hostConfig.portBindings.set(listOf("37017:27017"))
  hostConfig.autoRemove.set(true)
}

tasks.register<DockerStartContainer>("startMongoContainer") {
  dependsOn("createMongoContainer")
  targetContainerId(tasks.getByName<DockerCreateContainer>("createMongoContainer").containerId)
}

tasks.register<DockerStopContainer>("stopMongoContainer") {
  targetContainerId(tasks.getByName<DockerCreateContainer>("createMongoContainer").containerId)
  finalizedBy("removeIntegrationTestNetwork")
}

tasks.register<DockerPullImage>("pullAttributeServiceImage") {
  image.set("hypertrace/attribute-service:0.12.0")
}

tasks.register<DockerCreateContainer>("createAttributeServiceContainer") {
  dependsOn("pullAttributeServiceImage")
  targetImageId(tasks.getByName<DockerPullImage>("pullAttributeServiceImage").image)
  containerName.set("attribute-service-local")
  envVars.put("SERVICE_NAME", "attribute-service")
  envVars.put("MONGO_HOST", tasks.getByName<DockerCreateContainer>("createMongoContainer").containerName)
  exposePorts("tcp", listOf(9012))
  hostConfig.portBindings.set(listOf("9012:9012"))
  hostConfig.network.set(tasks.getByName<DockerCreateNetwork>("createIntegrationTestNetwork").networkId)
  hostConfig.autoRemove.set(true)
}

tasks.register<DockerStartContainer>("startAttributeServiceContainer") {
  dependsOn("startMongoContainer")
  dependsOn("createAttributeServiceContainer")
  targetContainerId(tasks.getByName<DockerCreateContainer>("createAttributeServiceContainer").containerId)
}

tasks.register<DockerStopContainer>("stopAttributeServiceContainer") {
  targetContainerId(tasks.getByName<DockerCreateContainer>("createAttributeServiceContainer").containerId)
  finalizedBy("stopMongoContainer")
}

tasks.register<DockerPullImage>("pullEntityServiceImage") {
  image.set("hypertrace/entity-service:0.6.4")
}

tasks.register<DockerCreateContainer>("createEntityServiceContainer") {
  dependsOn("pullEntityServiceImage")
  targetImageId(tasks.getByName<DockerPullImage>("pullEntityServiceImage").image)
  containerName.set("entity-service-local")
  envVars.put("SERVICE_NAME", "entity-service")
  envVars.put("MONGO_HOST", tasks.getByName<DockerCreateContainer>("createMongoContainer").containerName)
  exposePorts("tcp", listOf(50061))
  hostConfig.portBindings.set(listOf("50061:50061"))
  hostConfig.network.set(tasks.getByName<DockerCreateNetwork>("createIntegrationTestNetwork").networkId)
  hostConfig.autoRemove.set(true)
}

tasks.register<DockerStartContainer>("startEntityServiceContainer") {
  dependsOn("startMongoContainer")
  dependsOn("createEntityServiceContainer")
  targetContainerId(tasks.getByName<DockerCreateContainer>("createEntityServiceContainer").containerId)
}

tasks.register<DockerStopContainer>("stopEntityServiceContainer") {
  targetContainerId(tasks.getByName<DockerCreateContainer>("createEntityServiceContainer").containerId)
  finalizedBy("stopMongoContainer")
}

tasks.integrationTest {
  useJUnitPlatform()
  dependsOn("startAttributeServiceContainer")
  dependsOn("startEntityServiceContainer")
  finalizedBy("stopAttributeServiceContainer")
  finalizedBy("stopEntityServiceContainer")
}

tasks.test {
  useJUnitPlatform()
}

dependencies {
  implementation("org.hypertrace.entity.service:entity-service-client:0.6.4")
  implementation("org.hypertrace.entity.service:entity-service-api:0.6.4")
  implementation("org.hypertrace.core.documentstore:document-store:0.5.4")
  implementation("org.hypertrace.core.attribute.service:attribute-service-client:0.12.0")
  implementation("org.hypertrace.core.grpcutils:grpc-context-utils:0.6.2")
  implementation("org.hypertrace.core.grpcutils:grpc-client-utils:0.6.2")

  implementation("org.slf4j:slf4j-api:1.7.30")
  implementation("org.apache.logging.log4j:log4j-api:2.17.0")
  implementation("org.apache.logging.log4j:log4j-core:2.17.0")
  implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
  implementation("org.apache.httpcomponents:httpclient:4.5.13")
  implementation("commons-io:commons-io:2.8.0")
  implementation("com.typesafe:config:1.4.1")
  implementation("com.google.protobuf:protobuf-java:3.19.1")
  implementation("com.google.protobuf:protobuf-java-util:3.19.1")
  implementation("commons-cli:commons-cli:1.4")
  implementation("org.reflections:reflections:0.9.12")
  implementation("com.fasterxml.jackson.core:jackson-core:2.12.2")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.12.2")

  runtimeOnly("io.grpc:grpc-netty:1.42.0")

  constraints {
    implementation("commons-codec:commons-codec:1.15") {
      because("https://snyk.io/vuln/SNYK-JAVA-COMMONSCODEC-561518")
    }
    runtimeOnly("io.netty:netty-codec-http2:4.1.71.Final")
    runtimeOnly("io.netty:netty-handler-proxy:4.1.71.Final")
  }

  testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
  testImplementation("org.mockito:mockito-core:3.9.0")

  integrationTestImplementation("org.hypertrace.core.serviceframework:integrationtest-service-framework:0.1.31")
  integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
}
