// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ApexModule(val id: String, val category: String = "", val dependsOn: Array<String> = [])

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ModuleOwner(val id: String)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CoreOwner
