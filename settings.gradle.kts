/*
 * SPDX-FileCopyrightText: 2022 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BCR"
include(":app")
