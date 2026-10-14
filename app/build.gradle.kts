plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Version aus dem Tag: ./gradlew -PversionName=1.2.0 …; ohne Angabe eine Entwicklungsversion.
// versionCode = MAJOR * 10000 + MINOR * 100 + PATCH (entspricht AppVersion.codeOf in der App).
val appVersionName: String = (findProperty("versionName") as String?)?.removePrefix("v") ?: "0.1.0-dev"
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersionName)
    ?.destructured?.let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() } ?: 1
val buildId: String = System.getenv("GITHUB_SHA")?.take(7) ?: "lokal"

android {
    namespace = "de.flauschiversum.publisher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "de.flauschiversum.publisher"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "BUILD_ID", "\"$buildId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signatur nur aus Umgebungsvariablen (Geheimnisse der Build-Umgebung), nie aus dem Repository
    val keystorePath = System.getenv("SIGNING_KEYSTORE_FILE")
    if (keystorePath != null) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "META-INF/versions/**", "META-INF/*.kotlin_module", "**/module-info.class",
                "META-INF/eclipse.inf", "about.html", "plugin.properties",
            )
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // Wo Robolectric nicht selbst aus dem Netz laden kann (z. B. hinter einem Proxy), liegen die
        // Android-Jars in einem Verzeichnis, auf das ROBOLECTRIC_DEPS zeigt.
        unitTests.all { test ->
            // Dateinamen mit Umlauten müssen auch bei Locale POSIX ankommen (Tests schreiben Beiträge mit solchen Namen)
            test.jvmArgs("-Dsun.jnu.encoding=UTF-8", "-Dfile.encoding=UTF-8")
            System.getenv("ROBOLECTRIC_DEPS")?.let {
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", it)
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.jgit) { exclude(group = "org.slf4j") }
    implementation(libs.jgit.ssh.jsch) {
        exclude(group = "com.jcraft", module = "jsch")
        exclude(group = "org.slf4j")
    }
    implementation(libs.jsch)
    implementation(libs.bouncycastle.prov)
    implementation(libs.slf4j.api)
    implementation(libs.snakeyaml.engine)
    implementation(libs.lucene.core)
    implementation(libs.lucene.analyzers)
    implementation(libs.androidx.webkit)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strike)
    implementation(libs.commonmark.footnotes)
    runtimeOnly(libs.slf4j.nop)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.exifinterface)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.sshd.core)
    testImplementation(libs.sshd.git) { exclude(group = "org.eclipse.jgit") }
    testRuntimeOnly(libs.slf4j.simple)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}