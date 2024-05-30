plugins {
    id("java")
}

group = "com.dcli"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("org.eclipse.jetty.websocket:websocket-client:9.4.53.v20231009")
    implementation("com.eclipsesource.minimal-json:minimal-json:0.9.5")
}

tasks.test {
    useJUnitPlatform()
}