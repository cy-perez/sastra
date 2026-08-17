// Para los modulos que si pueden ver Spring. La plataforma solo fija versiones:
// no agrega una sola clase al classpath. `domain` no aplica este plugin.

plugins {
    id("sastra.java-conventions")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    val bom = platform(libs.findLibrary("spring-boot-bom").get())
    implementation(bom)
    testImplementation(bom)
}

tasks.withType<Test>().configureEach {
    // Inyeccion por constructor tambien en las pruebas. Sin esto, Spring solo
    // inyecta por campo con @Autowired, que es justo lo que el proyecto prohibe.
    systemProperty("spring.test.constructor.autowire.mode", "all")
}
