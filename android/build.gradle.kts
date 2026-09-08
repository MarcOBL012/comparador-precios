// Top-level build file — versions live in app/build.gradle.kts and libs.versions.toml equivalent.
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    // Room usa kapt en vez de KSP: KSP aún no publica un build dirigido a Kotlin 2.4.0
    // (el más reciente es 2.3.11), y mezclarlo con el compilador 2.4.0 que exige Clerk
    // falla con "unexpected jvm signature V" (mismatch de metadata KSP/Kotlin real,
    // verificado compilando). kapt no tiene ese acoplamiento de versión tan estricto.
    id("org.jetbrains.kotlin.kapt") version "2.4.0" apply false
}
