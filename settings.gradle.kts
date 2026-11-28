plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "costostock-backend"

includeBuild("ms-auth")
includeBuild("ms-productos")
includeBuild("ms-usuarios")
