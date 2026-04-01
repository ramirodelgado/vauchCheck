#!/bin/bash

echo "[SYSTEM] Initializing Fabric 1.21.11 Workspace..."

# 1. Inject Folder Structure
mkdir -p src/main/java/com/example/vauch
mkdir -p src/main/resources

# 2. Inject gradle.properties
cat <<EOF > gradle.properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
minecraft_version=1.21.10
yarn_mappings=1.21.10+build.1
loader_version=0.16.0
fabric_version=0.102.0+1.21.10
mod_version=1.0.0
maven_group=com.example.vauch
archives_base_name=vauchcheck
EOF

# 3. Inject settings.gradle
cat <<EOF > settings.gradle
pluginManagement {
    repositories {
        maven { url = "https://maven.fabricmc.net/" }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "vauchcheck"
EOF

# 4. Inject build.gradle (Enforcing Loom 1.14-SNAPSHOT and Java 21)
cat <<EOF > build.gradle
plugins {
    id 'fabric-loom' version '1.14-SNAPSHOT'
    id 'maven-publish'
}
version = project.mod_version
group = project.maven_group
repositories {
    mavenCentral()
}
dependencies {
    minecraft "com.mojang:minecraft:\${project.minecraft_version}"
    mappings "net.fabricmc:yarn:\${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:\${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:\${project.fabric_version}"
}
processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") { expand "version": project.version }
}
tasks.withType(JavaCompile).configureEach {
    it.options.release = 21
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
EOF

# 5. Inject fabric.mod.json (Enforcing Client Environment & Multi-version)
cat <<EOF > src/main/resources/fabric.mod.json
{
  "schemaVersion": 1,
  "id": "vauchcheck",
  "version": "\${version}",
  "name": "Vauch Check",
  "description": "In-game Vauch checking macro.",
  "environment": "client",
  "entrypoints": {
    "client": [ "com.example.vauch.VauchCheck" ]
  },
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": ">=1.21.10 <=1.21.11",
    "fabric-api": "*"
  }
}
EOF

# 6. Create Empty Java Files
touch src/main/java/com/example/vauch/VauchReport.java
touch src/main/java/com/example/vauch/VauchCheck.java

echo "[SYSTEM] Workspace scaffolded successfully."