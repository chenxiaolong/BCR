/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2023 Patryk Miś
 * SPDX-License-Identifier: GPL-3.0-only
 */

import org.eclipse.jgit.api.ArchiveCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.archive.TarFormat
import org.eclipse.jgit.lib.ObjectId
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.json.JSONObject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

buildscript {
    dependencies {
        "classpath"(libs.jgit)
        "classpath"(libs.jgit.archive)
        "classpath"(libs.json)
    }
}

typealias VersionTriple = Triple<String?, Int, ObjectId>

fun describeVersion(git: Git): VersionTriple {
    // jgit doesn't provide a nice way to get strongly-typed objects from its `describe` command
    val describeStr = git.describe().setLong(true).call()

    return if (describeStr != null) {
        val pieces = describeStr.split('-').toMutableList()
        val commit = git.repository.resolve(pieces.removeLast().substring(1))
        val count = pieces.removeLast().toInt()
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

val projectUrl = "https://github.com/chenxiaolong/BCR"
val releaseMetadataBranch = "master"

val extraDir = layout.buildDirectory.map { it.dir("extra") }
val archiveDir = extraDir.map { it.dir("archive") }

android {
    namespace = "com.chiller3.bcr"

    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.chiller3.bcr"
        minSdk = 28
        targetSdk = 36
        versionCode = gitVersionCode
        versionName = gitVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PROJECT_URL_AT_COMMIT",
            "\"${projectUrl}/tree/${gitVersionTriple.third.name}\"")

        buildConfigField("String", "PROVIDER_AUTHORITY",
            "APPLICATION_ID + \".provider\"")
        resValue("string", "provider_authority", "$applicationId.provider")
    }
    androidResources {
        generateLocaleConfig = true
    }
    sourceSets {
        getByName("main") {
            assets {
                srcDir(archiveDir)
            }
        }
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
        getByName("debug") {
            buildConfigField("boolean", "FORCE_DEBUG_MODE", "true")
        }

        create("debugOpt") {
            buildConfigField("boolean", "FORCE_DEBUG_MODE", "true")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = signingConfigs.getByName("debug")
        }

        getByName("release") {
            buildConfigField("boolean", "FORCE_DEBUG_MODE", "false")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    lint {
        // The translations are always going to lag behind new strings being
        // added to values/strings.xml
        disable += "MissingTranslation"
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kudzu)
    implementation(libs.material)
    testImplementation(libs.junit)
}

val archive = tasks.register("archive") {
    inputs.property("gitVersionTriple.third", gitVersionTriple.third)

    val outputFile = archiveDir.map { it.file("archive.tar") }
    outputs.file(outputFile)

    doLast {
        val format = "tar_for_task_$name"

        ArchiveCommand.registerFormat(format, TarFormat())
        try {
            outputFile.get().asFile.outputStream().use {
                git.archive()
                    .setTree(git.repository.resolve(gitVersionTriple.third.name))
                    .setFormat(format)
                    .setOutputStream(it)
                    .call()
            }
        } finally {
            ArchiveCommand.unregisterFormat(format)
        }
    }
}

android.applicationVariants.all {
    val variant = this
    val capitalized = variant.name.replaceFirstChar { it.uppercase() }
    val variantDir = extraDir.map { it.dir(variant.name) }

    variant.preBuildProvider.configure {
        dependsOn(archive)
    }

    val moduleProp = tasks.register("moduleProp${capitalized}") {
        inputs.property("projectUrl", projectUrl)
        inputs.property("releaseMetadataBranch", releaseMetadataBranch)
        inputs.property("rootProject.name", rootProject.name)
        inputs.property("variant.applicationId", variant.applicationId)
        inputs.property("variant.name", variant.name)
        inputs.property("variant.versionCode", variant.versionCode)
        inputs.property("variant.versionName", variant.versionName)

        val outputFile = variantDir.map { it.file("module.prop") }
        outputs.file(outputFile)

        doLast {
            val props = LinkedHashMap<String, String>()
            props["id"] = variant.applicationId
            props["name"] = rootProject.name
            props["version"] = "v${variant.versionName}"
            props["versionCode"] = variant.versionCode.toString()
            props["author"] = "chenxiaolong"
            props["description"] = "Basic Call Recorder"

            if (variant.name == "release") {
                props["updateJson"] = "${projectUrl}/raw/${releaseMetadataBranch}/app/magisk/updates/${variant.name}/info.json"
            }

            outputFile.get().asFile.writeText(
                props.map { "${it.key}=${it.value}" }.joinToString("\n"))
        }
    }

    val permissionsXml = tasks.register("permissionsXml${capitalized}") {
        inputs.property("variant.applicationId", variant.applicationId)

        val outputFile = variantDir.map { it.file("privapp-permissions-${variant.applicationId}.xml") }
        outputs.file(outputFile)

        doLast {
            outputFile.get().asFile.writeText("""
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

    val configXml = tasks.register("configXml${capitalized}") {
        inputs.property("variant.applicationId", variant.applicationId)

        val outputFile = variantDir.map { it.file("config-${variant.applicationId}.xml") }
        outputs.file(outputFile)

        doLast {
            outputFile.get().asFile.writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <config>
                    <hidden-api-whitelisted-app package="${variant.applicationId}" />
                </config>
            """.trimIndent())
        }
    }

    val addonD = tasks.register("addonD${capitalized}") {
        inputs.property("variant.applicationId", variant.applicationId)

        // To get output apk filename
        dependsOn.add(variant.assembleProvider)

        val outputFile = variantDir.map { it.file("51-${variant.applicationId}.sh") }
        outputs.file(outputFile)

        val backupFiles = variant.outputs.map {
            "priv-app/${variant.applicationId}/${it.outputFile.name}"
        } + listOf(
            "etc/permissions/privapp-permissions-${variant.applicationId}.xml",
            "etc/sysconfig/config-${variant.applicationId}.xml",
        )

        doLast {
            outputFile.get().asFile.writeText("""
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
        inputs.property("rootProject.name", rootProject.name)
        inputs.property("variant.applicationId", variant.applicationId)
        inputs.property("variant.name", variant.name)
        inputs.property("variant.versionName", variant.versionName)

        archiveFileName.set("${rootProject.name}-${variant.versionName}-${variant.name}.zip")
        // Force instantiation of old value or else this will cause infinite recursion
        destinationDirectory.set(destinationDirectory.dir(variant.name).get())

        // Make the zip byte-for-byte reproducible (note that the APK is still not reproducible)
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        dependsOn.add(variant.assembleProvider)

        from(moduleProp.map { it.outputs })
        from(addonD.map { it.outputs }) {
            filePermissions {
                unix("755")
            }
            into("system/addon.d")
        }
        from(permissionsXml.map { it.outputs }) {
            into("system/etc/permissions")
        }
        from(configXml.map { it.outputs }) {
            into("system/etc/sysconfig")
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

        from(File(magiskDir, "action.sh"))
        from(File(magiskDir, "boot_common.sh"))
        from(File(magiskDir, "post-fs-data.sh"))
        from(File(magiskDir, "service.sh"))
        from(File(magiskDir, "customize.sh"))

        from(File(rootDir, "LICENSE"))
        from(File(rootDir, "README.md"))
    }

    tasks.register("updateJson${capitalized}") {
        inputs.property("gitVersionTriple.first", gitVersionTriple.first)
        inputs.property("projectUrl", projectUrl)
        inputs.property("rootProject.name", rootProject.name)
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
            root.put("zipUrl", "${projectUrl}/releases/download/${gitVersionTriple.first}/${rootProject.name}-${variant.versionName}-release.zip")
            root.put("changelog", "${projectUrl}/raw/${gitVersionTriple.first}/app/magisk/updates/${variant.name}/changelog.txt")

            jsonFile.writer().use {
                root.write(it, 4, 0)
            }
        }
    }
}

data class LinkRef(val type: String, val number: Int, val user: String?) : Comparable<LinkRef> {
    override fun compareTo(other: LinkRef): Int = compareValuesBy(
        this,
        other,
        { it.type },
        { it.number },
        { it.user },
    )

    override fun toString(): String = buildString {
        append('[')
        append(type)
        append(" #")
        append(number)
        if (user != null) {
            append(" @")
            append(user)
        }
        append(']')
    }
}

fun checkBrackets(line: String) {
    var expectOpening = true

    for (c in line) {
        if (c == '[' || c == ']') {
            if (c == '[' != expectOpening) {
                throw IllegalArgumentException("Mismatched brackets: $line")
            }

            expectOpening = !expectOpening
        }
    }

    if (!expectOpening) {
        throw IllegalArgumentException("Missing closing bracket: $line")
    }
}

fun updateChangelogLinks(baseUrl: String) {
    val file = File(rootDir, "CHANGELOG.md")
    val regexStandaloneLink = Regex("\\[([^\\]]+)\\](?![\\(\\[])")
    val regexAutoLink = Regex("(Issue|PR) #(\\d+)(?: @([\\w-]+))?")
    val links = hashMapOf<LinkRef, String>()
    var skipRemaining = false
    val changelog = mutableListOf<String>()

    file.useLines { lines ->
        for (rawLine in lines) {
            val line = rawLine.trimEnd()

            if (!skipRemaining) {
                checkBrackets(line)
                val matches = regexStandaloneLink.findAll(line)

                for (linkMatch in matches) {
                    val linkText = linkMatch.groupValues[1]
                    val match = regexAutoLink.matchEntire(linkText)
                    require(match != null) { "Invalid link format: $linkText" }

                    val ref = match.groupValues[0]
                    val type = match.groupValues[1]
                    val number = match.groupValues[2].toInt()
                    val user = match.groups[3]?.value

                    val link = when (type) {
                        "Issue" -> {
                            require(user == null) { "$ref should not have a username" }
                            "$baseUrl/issues/$number"
                        }
                        "PR" -> {
                            require(user != null) { "$ref should have a username" }
                            "$baseUrl/pull/$number"
                        }
                        else -> throw IllegalArgumentException("Unknown link type: $type")
                    }

                    // #0 is used for examples only
                    if (number != 0) {
                        links[LinkRef(type, number, user)] = link
                    }
                }

                if ("Do not manually edit the lines below" in line) {
                    skipRemaining = true
                }

                changelog.add(line)
            }
        }
    }

    for ((ref, link) in links.entries.sortedBy { it.key }) {
        changelog.add("$ref: $link")
    }

    changelog.add("")

    file.writeText(changelog.joinToString("\n"))
}

fun updateChangelog(version: String?, replaceFirst: Boolean) {
    val file = File(rootDir, "CHANGELOG.md")
    val expected = if (version != null) { "### Version $version" } else { "### Unreleased" }

    val changelog = mutableListOf<String>().apply {
        // This preserves a trailing newline, unlike File.readLines()
        addAll(file.readText().lineSequence())
    }

    val index = changelog.indexOfFirst { it.startsWith("### ") }
    if (index == -1) {
        changelog.addAll(0, listOf(expected, ""))
    } else if (changelog[index] != expected) {
        if (replaceFirst) {
            changelog[index] = expected
        } else {
            changelog.addAll(index, listOf(expected, ""))
        }
    }

    file.writeText(changelog.joinToString("\n"))
}

fun updateMagiskChangelog(gitRef: String) {
    File(File(File(File(projectDir, "magisk"), "updates"), "release"), "changelog.txt")
        .writeText("The changelog can be found at: [`CHANGELOG.md`]($projectUrl/blob/$gitRef/CHANGELOG.md).\n")
}

tasks.register("changelogUpdateLinks") {
    doLast {
        updateChangelogLinks(projectUrl)
    }
}

tasks.register("changelogPreRelease") {
    val version = project.findProperty("releaseVersion")

    doLast {
        updateChangelog(version!!.toString(), true)
        updateMagiskChangelog("v$version")
    }
}

tasks.register("changelogPostRelease") {
    doLast {
        updateChangelog(null, false)
        updateMagiskChangelog(releaseMetadataBranch)
    }
}

tasks.register("preRelease") {
    dependsOn("changelogUpdateLinks")
    dependsOn("changelogPreRelease")
}

tasks.register("postRelease") {
    dependsOn("updateJsonRelease")
    dependsOn("changelogPostRelease")
}
