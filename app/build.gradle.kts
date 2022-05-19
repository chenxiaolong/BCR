plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 32

    defaultConfig {
        applicationId = "com.chiller3.bcr"
        minSdk = 29
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            val keystore = System.getenv("RELEASE_KEYSTORE")
            storeFile = if (keystore != null) { File(keystore) } else { null }
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSPHRASE")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSPHRASE")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_1_8)
        targetCompatibility(JavaVersion.VERSION_1_8)
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.4.0")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.fragment:fragment-ktx:1.4.1")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("com.google.android.material:material:1.5.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}

android.applicationVariants.all {
    val variant = this
    val capitalized = variant.name.capitalize()
    val extraDir = File(buildDir, "extra")
    val variantDir = File(extraDir, variant.name)

    val moduleProp = tasks.register("moduleProp${capitalized}") {
        val outputFile = File(variantDir, "module.prop")
        outputs.file(outputFile)

        doLast {
            outputFile.writeText("""
                id=${variant.applicationId}
                name=Basic Call Recorder
                version=v${variant.versionName}
                versionCode=${variant.versionCode}
                author=chenxiaolong
                description=Basic Call Recorder
            """.trimIndent())
        }
    }

    val permissionsXml = tasks.register("permissionsXml${capitalized}") {
        val outputFile = File(variantDir, "privapp-permissions-${variant.applicationId}.xml")
        outputs.file(outputFile)

        doLast {
            outputFile.writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <permissions>
                    <privapp-permissions package="${variant.applicationId}">
                        <permission name="android.permission.CAPTURE_AUDIO_OUTPUT" />
                        <permission name="android.permission.CONTROL_INCALL_EXPERIENCE" />
                    </privapp-permissions>
                </permissions>
            """.trimIndent())
        }
    }

    tasks.register<Zip>("zip${capitalized}") {
        archiveFileName.set("BCR-${variant.versionName}-${variant.name}.zip")
        destinationDirectory.set(File(destinationDirectory.asFile.get(), variant.name))

        // Make the zip byte-for-byte reproducible (note that the APK is still not reproducible)
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        dependsOn.add(variant.assembleProvider)

        from(moduleProp.get().outputs)
        from(permissionsXml.get().outputs) {
            into("system/etc/permissions")
        }
        from(variant.outputs.map { it.outputFile }) {
            into("system/priv-app/${variant.applicationId}")
        }

        val magiskDir = File(projectDir, "magisk")
        from(magiskDir) {
            into("META-INF/com/google/android")
        }

        from(File(rootDir, "LICENSE"))
        from(File(rootDir, "README.md"))
    }
}