/*
 * ClearScan build configuration
 * Modifications Copyright (c) 2026 ant-cave <antmmmmm@126.com>
 * SPDX-License-Identifier: MIT
 *
 * Based on ClearScan by SuiYueMengHen (MIT License).
 * ant-cave modifications: use official Maven repositories, drop llama.cpp module.
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ClearScan"
include(":app")
