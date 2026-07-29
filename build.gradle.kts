
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}
tasks.wrapper {
    gradleVersion = "9.4.0"
    distributionUrl = "https://mirrors.cloud.tencent.com/gradle/gradle-9.4.0-bin.zip"
}
tasks.reobfJar {
    archiveFileName.set("ProjectE-${project.properties["modVersion"]}.jar")
}