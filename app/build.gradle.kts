import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.jetbrains.kotlin.backend.common.pop
import org.json.JSONObject
import io.sentry.android.gradle.extensions.InstrumentationFeature

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.sentry.android.gradle") version "3.3.0"
}

buildscript {
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.2.0.202206071550-r")
        classpath("org.json:json:20220320")
    }
}

typealias VersionTriple = Triple<String?, Int, ObjectId>

fun describeVersion(git: Git): VersionTriple {
    // jgit doesn't provide a nice way to get strongly-typed objects from its `describe` command
    val describeStr = git.describe().setLong(true).call()

    return if (describeStr != null) {
        val pieces = describeStr.split('-').toMutableList()
        val commit = git.repository.resolve(pieces.pop().substring(1))
        val count = pieces.pop().toInt()
        val tag = pieces.joinToString("-")

        Triple(tag, count, commit)
    } else {
        val log = git.log().call().iterator()
        val head = log.next()
        var count = 1

        while (log.hasNext()) {
            log.next()
            ++count
        }

        Triple(null, count, head.id)
    }
}

fun getVersionCode(triple: VersionTriple): Int {
    val tag = triple.first
    val (major, minor) = if (tag != null) {
        if (!tag.startsWith('v')) {
            throw IllegalArgumentException("Tag does not begin with 'v': $tag")
        }

        val pieces = tag.substring(1).split('.')
        if (pieces.size != 2) {
            throw IllegalArgumentException("Tag is not in the form 'v<major>.<minor>': $tag")
        }

        Pair(pieces[0].toInt(), pieces[1].toInt())
    } else {
        Pair(0, 0)
    }

    // 8 bits for major version, 8 bits for minor version, and 8 bits for git commit count
    assert(major in 0 until 1.shl(8))
    assert(minor in 0 until 1.shl(8))
    assert(triple.second in 0 until 1.shl(8))

    return major.shl(16) or minor.shl(8) or triple.second
}

fun getVersionName(git: Git, triple: VersionTriple): String {
    val tag = triple.first?.replace(Regex("^v"), "") ?: "NONE"

    return buildString {
        append(tag)

        if (triple.second > 0) {
            append(".r")
            append(triple.second)

            append(".g")
            git.repository.newObjectReader().use {
                append(it.abbreviate(triple.third).name())
            }
        }
    }
}

val git = Git.open(File(rootDir, ".git"))!!
val gitVersionTriple = describeVersion(git)
val gitVersionCode = getVersionCode(gitVersionTriple)
val gitVersionName = getVersionName(git, gitVersionTriple)

val projectUrl = "https://github.com/Trinary-Projects/call-sync"
val releaseMetadataBranch = "main"

android {
    namespace = "com.chiller3.bcr"

    compileSdk = 33
    buildToolsVersion = "33.0.0"

    defaultConfig {
        applicationId = "com.chiller3.bcr"
        minSdk = 29
        targetSdk = 33
        versionCode = gitVersionCode
        versionName = gitVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PROJECT_URL_AT_COMMIT",
            "\"${projectUrl}/tree/${gitVersionTriple.third.name}\"")
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        }
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    //Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.5.0")

    //DateTime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    implementation("androidx.activity:activity-ktx:1.6.1")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.fragment:fragment-ktx:1.5.4")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("com.google.android.material:material:1.7.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}

sentry{
    includeProguardMapping.set(false)
    autoUploadProguardMapping.set(false)

}

android.applicationVariants.all {
    val variant = this
    val capitalized = variant.name.capitalize()
    val extraDir = File(buildDir, "extra")
    val variantDir = File(extraDir, variant.name)

    val moduleProp = tasks.register("moduleProp${capitalized}") {
        inputs.property("projectUrl", projectUrl)
        inputs.property("releaseMetadataBranch", releaseMetadataBranch)
        inputs.property("variant.applicationId", variant.applicationId)
        inputs.property("variant.name", variant.name)
        inputs.property("variant.versionCode", variant.versionCode)
        inputs.property("variant.versionName", variant.versionName)

        val outputFile = File(variantDir, "module.prop")
        outputs.file(outputFile)

        doLast {
            val props = LinkedHashMap<String, String>()
            props["id"] = variant.applicationId
            props["name"] = "BCR"
            props["version"] = "v${variant.versionName}"
            props["versionCode"] = variant.versionCode.toString()
            props["author"] = "chenxiaolong"
            props["description"] = "Basic Call Recorder"

            if (variant.name == "release") {
                props["updateJson"] = "${projectUrl}/raw/${releaseMetadataBranch}/app/magisk/updates/${variant.name}/info.json"
            }

            outputFile.writeText(props.map { "${it.key}=${it.value}" }.joinToString("\n"))
        }
    }

    val permissionsXml = tasks.register("permissionsXml${capitalized}") {
        inputs.property("variant.applicationId", variant.applicationId)

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

    val addonD = tasks.register("addonD${capitalized}") {
        inputs.property("variant.applicationId", variant.applicationId)

        // To get output apk filename
        dependsOn.add(variant.assembleProvider)

        val outputFile = File(variantDir, "51-${variant.applicationId}.sh")
        outputs.file(outputFile)

        val backupFiles = variant.outputs.map {
            "priv-app/${variant.applicationId}/${it.outputFile.name}"
        } + listOf(
            "etc/permissions/privapp-permissions-${variant.applicationId}.xml"
        )

        doLast {
            outputFile.writeText("""
                #!/sbin/sh
                # ADDOND_VERSION=2

                . /tmp/backuptool.functions

                files="${backupFiles.joinToString(" ")}"

                case "${'$'}{1}" in
                backup|restore)
                    for f in ${'$'}{files}; do
                        "${'$'}{1}_file" "${'$'}{S}/${'$'}{f}"
                    done
                    ;;
                esac
            """.trimIndent())
        }
    }

    tasks.register<Zip>("zip${capitalized}") {
        inputs.property("variant.applicationId", variant.applicationId)
        inputs.property("variant.name", variant.name)
        inputs.property("variant.versionName", variant.versionName)

        archiveFileName.set("BCR-${variant.versionName}-${variant.name}.zip")
        destinationDirectory.set(File(destinationDirectory.asFile.get(), variant.name))

        // Make the zip byte-for-byte reproducible (note that the APK is still not reproducible)
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        dependsOn.add(variant.assembleProvider)

        from(moduleProp.get().outputs)
        from(addonD.get().outputs) {
            fileMode = 0b111_101_101 // 0o755; kotlin doesn't support octal literals
            into("system/addon.d")
        }
        from(permissionsXml.get().outputs) {
            into("system/etc/permissions")
        }
        from(variant.outputs.map { it.outputFile }) {
            into("system/priv-app/${variant.applicationId}")
        }

        val magiskDir = File(projectDir, "magisk")

        for (script in arrayOf("update-binary", "updater-script")) {
            from(File(magiskDir, script)) {
                into("META-INF/com/google/android")
            }
        }

        from(File(rootDir, "LICENSE"))
        from(File(rootDir, "README.md"))
    }

    tasks.register("updateJson${capitalized}") {
        inputs.property("gitVersionTriple.first", gitVersionTriple.first)
        inputs.property("projectUrl", projectUrl)
        inputs.property("releaseMetadataBranch", releaseMetadataBranch)
        inputs.property("variant.name", variant.name)
        inputs.property("variant.versionCode", variant.versionCode)
        inputs.property("variant.versionName", variant.versionName)

        val magiskDir = File(projectDir, "magisk")
        val updatesDir = File(magiskDir, "updates")
        val variantUpdateDir = File(updatesDir, variant.name)
        val jsonFile = File(variantUpdateDir, "info.json")

        outputs.file(jsonFile)

        doLast {
            if (gitVersionTriple.second != 0) {
                throw IllegalStateException("The release tag must be checked out")
            }

            val root = JSONObject()
            root.put("version", variant.versionName)
            root.put("versionCode", variant.versionCode)
            root.put("zipUrl", "${projectUrl}/releases/download/${gitVersionTriple.first}/BCR-${variant.versionName}-release.zip")
            root.put("changelog", "${projectUrl}/raw/${releaseMetadataBranch}/app/magisk/updates/${variant.name}/changelog.txt")

            jsonFile.writer().use {
                root.write(it, 4, 0)
            }
        }
    }
}