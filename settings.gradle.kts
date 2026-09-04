/*
 * ClearScan build configuration
 * Copyright (c) 2026 SuiYueMengHen (original code, MIT License)
 * Modifications Copyright (c) 2026 ant-cave <antmmmmm@126.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
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
