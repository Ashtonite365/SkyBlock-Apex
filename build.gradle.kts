/*
 * SPDX-FileCopyrightText: 2023 Linnea Gräf <nea@nea.moe>
 * SPDX-FileCopyrightText: 2024 Linnea Gräf <nea@nea.moe>
 *
 * SPDX-License-Identifier: CC0-1.0
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.devtools.ksp.gradle.KspAATask
import com.google.gson.Gson
import com.google.gson.JsonObject
import moe.nea.licenseextractificator.LicenseDiscoveryTask
import moe.nea.mcautotranslations.gradle.CollectTranslations
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.utils.extendsFrom
import java.util.*

plugins {
	java
	`maven-publish`
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.plugin.serialization)
	alias(libs.plugins.kotlin.plugin.powerassert)
	alias(libs.plugins.kotlin.plugin.ksp)
	id("apex.common")
	id("apex.loom")
	id("apex.license-management")
	alias(libs.plugins.mcAutoTranslations)
}

version = "2.0.0+mc${libs.versions.minecraft.get()}"

base.archivesName.set("SkyBlockApex")

java {
	withSourcesJar()
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(25))
	}
}

val javaLanguageVersion = java.toolchain.languageVersion
val javaLanguageVersionName = javaLanguageVersion.map { it.toString() }
val javaLanguageVersionJvmTarget = javaLanguageVersionName.map { JvmTarget.fromTarget(it) }

loom {
	mixin.useLegacyMixinAp.set(false)
}

tasks.withType(KotlinCompile::class) {
	compilerOptions {
		jvmTarget.set(javaLanguageVersionJvmTarget)
	}
}

kotlin {
	sourceSets.all {
		languageSettings {

		}
	}
}
fun String.capitalizeN() = replaceFirstChar { it.uppercaseChar() }

val unpackAllJars by tasks.registering
fun innerJarsOf(name: String, dependency: Dependency): Provider<FileTree> {
	val task = tasks.create("unpackInnerJarsFor${name.capitalizeN()}", InnerJarsUnpacker::class) {
		this.inputJars.setFrom(files(configurations.detachedConfiguration(dependency)))
		this.outputDir.set(layout.buildDirectory.dir("unpackedJars/$name").also {
			it.get().asFile.mkdirs()
		})
	}
	unpackAllJars { dependsOn(task) }
	return project.provider {
		project.files(task).asFileTree
	}
}

val collectTranslations by tasks.registering(CollectTranslations::class) {
	this.baseTranslations.from(file("translations/en_us.json"))
	this.baseTranslations.from(file("translations/extra.json"))
	this.classes.from(sourceSets.main.get().kotlin.classesDirectory)
}

val shadowJar = tasks.register("shadowJar", ShadowJar::class)
val mergedSourceSetsJar = tasks.register("mergedSourceSetsJar", ShadowJar::class)

val compatSourceSets: MutableSet<SourceSet> = mutableSetOf()
fun createIsolatedSourceSet(
	name: String,
	path: String = "compat/$name",
	isEnabled: Boolean = true,
	inheritsFromMain: Boolean = true,
	enableKsp: Boolean = true,
): SourceSet {
	val ss = sourceSets.create(name) {
		this.java.setSrcDirs(listOf(layout.projectDirectory.dir("src/$path/java")))
		this.kotlin.setSrcDirs(listOf(layout.projectDirectory.dir("src/$path/java")))
	}
	val mainSS = sourceSets.main.get()
	val upperName = ss.name.capitalizeN()
	afterEvaluate {
		tasks.named("ksp${upperName}Kotlin", KspAATask::class) {
			this.commandLineArgumentProviders.add {
				listOf("apex.sourceset=${ss.name}")
			}
			if (!enableKsp)
				this.enabled = false
		}
		tasks.named("compile${upperName}Kotlin", KotlinCompile::class) {
			this.enabled = isEnabled
		}
		tasks.named("compile${upperName}Java", JavaCompile::class) {
			this.enabled = isEnabled
		}
	}
	compatSourceSets.add(ss)
	if (!isEnabled) {
		ss.output.files.forEach { it.deleteRecursively() }
		return ss
	}
	configurations {
		(ss.compileOnlyConfigurationName) {
			if (inheritsFromMain) {

				extendsFrom(configurations.named(mainSS.compileClasspathConfigurationName))

			}
		}
		(ss.annotationProcessorConfigurationName) {
			extendsFrom(getByName(mainSS.annotationProcessorConfigurationName))
		}
		(mainSS.runtimeOnlyConfigurationName) {
			if (isEnabled)
				extendsFrom(getByName(ss.runtimeClasspathConfigurationName))
		}
		if (enableKsp)
			("ksp$upperName") {
				extendsFrom(ksp.get())
			}
	}
	dependencies {
		if (isEnabled)
			runtimeOnly(ss.output)
		if (inheritsFromMain) {
			(ss.implementationConfigurationName)(project.files(tasks.compileKotlin.map { it.destinationDirectory }))
			(ss.implementationConfigurationName)(project.files(tasks.compileJava.map { it.destinationDirectory }))
		}
	}
	mergedSourceSetsJar.configure {
		from(ss.output)
	}
	collectTranslations {
		this.classes.from(ss.kotlin.classesDirectory)
	}
	return ss
}

@Deprecated("No more remapping")
val SourceSet.modImplementationConfigurationName
	get() =
		this.implementationConfigurationName

@Deprecated("No more remapping")
val SourceSet.modRuntimeOnlyConfigurationName
	get() =
		this.runtimeOnlyConfigurationName

val shadowMe by configurations.creating {
	exclude(group = "org.jetbrains.kotlin")
	exclude(group = "org.jetbrains.kotlinx")
	exclude(group = "org.jetbrains")
	exclude(module = "gson")
	exclude(group = "org.slf4j")
}

val hotswap by configurations.creating {}

val testAgent by configurations.creating {}

fabricApi.configureTests {
	createSourceSet.set(true)
	enableClientGameTests.set(true)
	modId.set("apex-gametest")
	eula.set(true)
	username.set("CoolGuy123")
}
val gameTestSourceSet by sourceSets.named("gametest") {
	configurations.named(compileClasspathConfigurationName).extendsFrom(configurations.testCompileClasspath)
	configurations.named(runtimeClasspathConfigurationName).extendsFrom(configurations.testRuntimeClasspath)
}

val sodiumSourceSet = createIsolatedSourceSet("sodium")
val yaclSourceSet = createIsolatedSourceSet("yacl")

val jadeSourceSet = createIsolatedSourceSet("jade")
val modmenuSourceSet = createIsolatedSourceSet("modmenu")

val jeiSourceSet = createIsolatedSourceSet("jei")
val reiSourceSet = createIsolatedSourceSet("rei")
val emiSourceSet = createIsolatedSourceSet("emi")
val moulconfigSourceSet = createIsolatedSourceSet("moulconfig")
val irisSourceSet = createIsolatedSourceSet("iris")
val customTexturesSourceSet = createIsolatedSourceSet("texturePacks", "texturePacks")
val apiSourceSet = createIsolatedSourceSet("api", "api", inheritsFromMain = false, enableKsp = false)

val reiArchitecturyApi = "dev.architectury:architectury:${libs.versions.architectury.rei.get()}:dev"

val emiApiClasses = tasks.register<Sync>("emiApiClasses") {
	from(configurations.detachedConfiguration(dependencies.create(libs.emi.get().toString()).also {
		(it as ExternalModuleDependency).isTransitive = false
	}).elements.map { it.map { f -> zipTree(f) } })
	include("dev/emi/emi/**/*.class")
	exclude("dev/emi/emi/mixin/**")
	into(layout.buildDirectory.dir("emiApiClasses"))
}

dependencies {

	"minecraft"(libs.minecraft)

	hotswap(libs.hotswap)

	implementation(libs.fabric.loader)
	implementation(libs.fabric.kotlin)
	implementation(libs.moulconfig)
	implementation(libs.manninghamMills)
	implementation(libs.basicMath)
	implementation(apiSourceSet.output)

	(apiSourceSet.compileOnlyConfigurationName)(loom.namedMinecraftJars)
	(apiSourceSet.implementationConfigurationName)(libs.jspecify)
	(apiSourceSet.implementationConfigurationName)(libs.jbAnnotations)
	include(libs.basicMath)
	(modmenuSourceSet.implementationConfigurationName)(libs.modmenu)
	implementation(libs.hypixelmodapi)
	runtimeOnly(libs.hypixelmodapi.fabric)
	include(libs.hypixelmodapi.fabric)
	compileOnly(projects.javaplugin)
	annotationProcessor(projects.javaplugin)
	implementation("com.google.auto.service:auto-service-annotations:1.1.1")
	ksp("dev.zacsweers.autoservice:auto-service-ksp:1.2.0")
	include(libs.manninghamMills)
	shadowMe(libs.moulconfig)

	annotationProcessor(libs.mixinextras)
	implementation(libs.mixinextras)
	include(libs.mixinextras)

	implementation(libs.fabric.api)
	runtimeOnly(libs.fabric.api.deprecated)

	(irisSourceSet.implementationConfigurationName)(libs.iris)

	(sodiumSourceSet.implementationConfigurationName)(libs.sodium)
	(sodiumSourceSet.implementationConfigurationName)(customTexturesSourceSet.output)
	(jadeSourceSet.implementationConfigurationName)(libs.jade)

	(yaclSourceSet.implementationConfigurationName)(libs.yacl)

	(jeiSourceSet.compileOnlyConfigurationName)(libs.jei.common.api)
	(jeiSourceSet.compileOnlyConfigurationName)(libs.jei.fabric.api)
	(reiSourceSet.compileOnlyConfigurationName)(libs.rei.api.fabric) { isTransitive = false }
	(reiSourceSet.compileOnlyConfigurationName)(reiArchitecturyApi) { isTransitive = false }
	(emiSourceSet.compileOnlyConfigurationName)(libs.emi) { isTransitive = false }

	testImplementation(reiSourceSet.output)
	testImplementation(emiSourceSet.output)
	testImplementation(libs.rei.api.fabric) { isTransitive = false }
	testImplementation(reiArchitecturyApi) { isTransitive = false }
	testImplementation(files(emiApiClasses))
	implementation(libs.repoparser)
	shadowMe(libs.repoparser)

	runtimeOnly(libs.devauth)
	runtimeOnly(libs.modmenu)
	runtimeOnly(libs.noChatRestrictions)

	testImplementation("net.fabricmc:fabric-loader-junit:${libs.versions.fabric.loader.get()}")

	testImplementation(files("test-libs/aircompressor-v3-3.13.jar"))

	testImplementation(customTexturesSourceSet.output)
	testAgent(files(tasks.getByPath(":testagent:jar")))

	"gametestImplementation"(sourceSets.test.map { it.output })

	implementation(projects.symbols)
	ksp(projects.symbols)
}

loom {
	accessWidenerPath.set(project.file("src/main/resources/apex.accesswidener"))
	runs {
		removeIf { it.name == "server" }
		configureEach {
			property("fabric.log.level", "info")
			property("apex.debug", "true")
			property(
				"apex.classroots",
				compatSourceSets.joinToString(File.pathSeparator) {
					File(it.output.classesDirs.asPath).absolutePath
				})
			property("mixin.debug.export", "true")

			parseEnvFile(file(".env")).forEach { (t, u) ->
				environmentVariable(t, u)
			}
			parseEnvFile(file(".properties")).forEach { (t, u) ->
				property(t, u)
			}
		}
		named("client") {
			property("devauth.enabled", "false")
			if (project.findProperty("mixinAudit") == "true") property("apex.mixinAudit", "true")
			vmArg("-ea")
			if (project.findProperty("apex.useHotswap") == "true") {
				vmArg("-XX:+AllowEnhancedClassRedefinition")
				vmArg("-XX:HotswapAgent=external")
				vmArg("-javaagent:${hotswap.resolve().single().absolutePath}")
			}
		}
	}
}

mcAutoTranslations {
	translationFunction.set("dev.ashtonite365.apex.util.tr")
	translationFunctionResolved.set("dev.ashtonite365.apex.util.trResolved")
}

val downloadTestRepo by tasks.registering(RepoDownload::class) {
	this.hash.set(project.property("apex.compiletimerepohash") as String)
}

val updateTestRepo by tasks.registering {
	outputs.upToDateWhen { false }
	doLast {
		val propertiesFile = rootProject.file("gradle.properties")
		val json =
			Gson().fromJson(
				uri("https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/branches/master")
					.toURL().readText(), JsonObject::class.java
			)
		val latestSha = json["commit"].asJsonObject["sha"].asString
		var text = propertiesFile.readText()
		text = text.replace(
			"apex\\.compiletimerepohash=[^\n]*".toRegex(),
			"apex.compiletimerepohash=$latestSha"
		)
		propertiesFile.writeText(text)
	}
}

tasks.test {
	maxHeapSize = "2g"
	systemProperty("apex.projectDir", project.projectDir.absolutePath)
	filter { excludeTestsMatching("*JarContentsTest*") }
	val wd = file("build/testWorkDir")
	workingDir(wd)
	dependsOn(downloadTestRepo)
	dependsOn(testAgent)

	val entrypointClassDirs = listOf(sourceSets.main.get(), jeiSourceSet, reiSourceSet, emiSourceSet, modmenuSourceSet, jadeSourceSet)
		.map { it.output.classesDirs }
	entrypointClassDirs.forEach { inputs.files(it) }
	doFirst {
		systemProperty("apex.entrypointClassDirs", entrypointClassDirs.flatMap { it.files }.joinToString(File.pathSeparator))
		wd.mkdirs()
		wd.resolve("config").deleteRecursively()
		systemProperty(
			"apex.testrepo",
			downloadTestRepo.flatMap { it.outputDirectory.asFile }.map { it.absolutePath }.get()
		)
		jvmArgs("-javaagent:${testAgent.singleFile.absolutePath}")
	}
	systemProperty("jdk.attach.allowAttachSelf", "true")

	jvmArgs("-XX:+EnableDynamicAgentLoading", "-XX:-OmitStackTraceInFastThrow")
	systemProperties(
		"kotest.framework.classpath.scanning.config.disable" to true,
		"kotest.framework.config.fqn" to "dev.ashtonite365.apex.test.testutil.KotestPlugin",
	)
	useJUnitPlatform { excludeTags("bench", "moulplatform", "screen") }
	finalizedBy("platformTest")
}

val platformTest = tasks.register<Test>("platformTest") {
	description = "Runs the tests that need moulconfig's platform initialised, isolated from the main test JVM."
	group = "verification"
	val t = sourceSets.test.get()
	testClassesDirs = t.output.classesDirs
	classpath = t.runtimeClasspath
	val wd = file("build/testWorkDir")
	workingDir(wd)
	dependsOn(downloadTestRepo)
	dependsOn(testAgent)
	maxHeapSize = "1g"
	systemProperty("apex.projectDir", project.projectDir.absolutePath)
	systemProperty("jdk.attach.allowAttachSelf", "true")
	jvmArgs("-XX:+EnableDynamicAgentLoading")
	doFirst {
		wd.mkdirs()
		systemProperty("apex.testrepo", downloadTestRepo.flatMap { it.outputDirectory.asFile }.map { it.absolutePath }.get())
		jvmArgs("-javaagent:${testAgent.singleFile.absolutePath}")
	}
	systemProperties(
		"kotest.framework.classpath.scanning.config.disable" to true,
		"kotest.framework.config.fqn" to "dev.ashtonite365.apex.test.testutil.KotestPlugin",
	)
	useJUnitPlatform { includeTags("moulplatform") }
}

val benchTest = tasks.register<Test>("benchTest") {
	description = "Runs the texture engine benchmarks (B-*)."
	group = "verification"
	val t = sourceSets.test.get()
	testClassesDirs = t.output.classesDirs
	classpath = t.runtimeClasspath
	val wd = file("build/testWorkDir")
	workingDir(wd)
	dependsOn(downloadTestRepo)
	dependsOn(testAgent)
	systemProperty("apex.projectDir", project.projectDir.absolutePath)
	systemProperty("apex.benchDir", file("build/bench").absolutePath)
	systemProperty("jdk.attach.allowAttachSelf", "true")
	jvmArgs("-XX:+EnableDynamicAgentLoading")
	doFirst {
		wd.mkdirs()
		file("build/bench").mkdirs()
		systemProperty("apex.testrepo", downloadTestRepo.flatMap { it.outputDirectory.asFile }.map { it.absolutePath }.get())
		jvmArgs("-javaagent:${testAgent.singleFile.absolutePath}")
	}
	systemProperties(
		"kotest.framework.classpath.scanning.config.disable" to true,
		"kotest.framework.config.fqn" to "dev.ashtonite365.apex.test.testutil.KotestPlugin",
	)
	useJUnitPlatform { includeTags("bench") }
	outputs.upToDateWhen { false }
}

val screenGoldenTest = tasks.register<Test>("screenGoldenTest") {
	description = "Runs the screen-bound golden differential tests in an interpreter-only JVM."
	group = "verification"
	val t = sourceSets.test.get()
	testClassesDirs = t.output.classesDirs
	classpath = t.runtimeClasspath
	val wd = file("build/testWorkDir")
	workingDir(wd)
	dependsOn(downloadTestRepo)
	dependsOn(testAgent)
	mustRunAfter(tasks.test)
	systemProperty("apex.projectDir", project.projectDir.absolutePath)
	systemProperty("apex.screenJvm", "true")
	systemProperty("jdk.attach.allowAttachSelf", "true")
	maxHeapSize = "1536m"
	jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xint")
	doFirst {
		wd.mkdirs()
		systemProperty("apex.testrepo", downloadTestRepo.flatMap { it.outputDirectory.asFile }.map { it.absolutePath }.get())
		jvmArgs("-javaagent:${testAgent.singleFile.absolutePath}")
	}
	systemProperties(
		"kotest.framework.classpath.scanning.config.disable" to true,
		"kotest.framework.config.fqn" to "dev.ashtonite365.apex.test.testutil.KotestPlugin",
	)
	useJUnitPlatform { includeTags("screen") }
}
tasks.check { dependsOn(screenGoldenTest) }

tasks.withType<JavaCompile> {
	this.sourceCompatibility = javaLanguageVersionName.get()
	this.targetCompatibility = javaLanguageVersionName.get()
	options.encoding = "UTF-8"
	val module = "ALL-UNNAMED"
	options.forkOptions.jvmArgs!!.addAll(
		listOf(
			"--add-exports=jdk.compiler/com.sun.tools.javac.util=$module",
			"--add-exports=jdk.compiler/com.sun.tools.javac.comp=$module",
			"--add-exports=jdk.compiler/com.sun.tools.javac.tree=$module",
			"--add-exports=jdk.compiler/com.sun.tools.javac.api=$module",
			"--add-exports=jdk.compiler/com.sun.tools.javac.code=$module",
		)
	)
	options.isFork = true
	afterEvaluate {
		options.compilerArgs.add("-Xplugin:IntermediaryNameReplacement")
	}
}

fun AbstractArchiveTask.badjar() = destinationDirectory.set(layout.buildDirectory.dir("badjars"))
tasks.jar {
	badjar()
	archiveClassifier.set("slim")
}
val apiJar = tasks.register("apiJar", Jar::class) {
	archiveClassifier = "api"
	from(apiSourceSet.output)
}

val apiSourcesJar = tasks.register("apiSourcesJar", Jar::class) {
	from(apiSourceSet.allSource)
	archiveClassifier = "api-sources"
}

tasks.assemble {
	dependsOn(apiJar)
	dependsOn(apiSourcesJar)
}

fun ShadowJar.configureDuplicateStrategy() {
	duplicatesStrategy = DuplicatesStrategy.INCLUDE
	failOnDuplicateEntries = true
}

mergedSourceSetsJar.configure {
	from(zipTree(tasks.jar.flatMap { it.archiveFile }))
	badjar()
	archiveClassifier.set("merged-source-sets")
	mergeServiceFiles()
	configureDuplicateStrategy()
}

shadowJar.configure {
	from(zipTree(mergedSourceSetsJar.flatMap { it.archiveFile }))
	configurations = listOf(shadowMe)
	configureDuplicateStrategy()
	archiveClassifier.set("")
	relocate("io.github.moulberry.repo", "dev.ashtonite365.apex.deps.repo")
	relocate("io.github.notenoughupdates.moulconfig", "dev.ashtonite365.apex.deps.moulconfig")

	eachFile {
		if (path == "LICENSE" && runCatching { file.readText().startsWith("Mozilla Public License") }.getOrDefault(false))
			path = "META-INF/apex/LICENSES/LibNinePatch-MPL-2.0-via-MoulConfig.txt"
	}
	mergeServiceFiles()
	transform<FabricModTransform>()

	exclude("assets/moulconfig/toggle_1.png", "assets/moulconfig/delete.png", "assets/moulconfig/reset.png", "assets/moulconfig/search.png")
}

tasks.register<JavaExec>("apexPack") {
	description = "Encodes, decodes or inspects Apex pack / zstd files."
	group = "application"
	classpath = sourceSets.main.get().runtimeClasspath
	mainClass.set("dev.ashtonite365.apex.compress.ApexPackTool")
	maxHeapSize = "2g"
}

val jarContentsTest = tasks.register<Test>("jarContentsTest") {
	description = "Checks the contents of the built SkyBlock Apex jar."
	group = "verification"
	val t = sourceSets.test.get()
	testClassesDirs = t.output.classesDirs
	classpath = t.runtimeClasspath
	dependsOn(shadowJar)
	inputs.file(shadowJar.flatMap { it.archiveFile })
	systemProperty("apex.jar", shadowJar.flatMap { it.archiveFile }.get().asFile.absolutePath)
	filter { includeTestsMatching("*JarContentsTest*") }
	useJUnitPlatform()
}

tasks.assemble { dependsOn(shadowJar) }

tasks.processResources {
	val replacements = listOf(
		"version" to project.version.toString().substringBefore("+"),
		"minecraft_version" to libs.versions.minecraft.get(),
		"fabric_kotlin_version" to libs.versions.fabric.kotlin.get(),
		"fabric_api_version" to libs.versions.fabric.api.get()
	)
	replacements.forEach { (key, value) -> inputs.property(key, value) }
	filesMatching("**/fabric.mod.json") {
		expand(*replacements.toTypedArray())
	}
	exclude("**/*.license")
	from(tasks.scanLicenses)
	from(collectTranslations) {
		into("assets/apex/lang")
	}
	from(project.files("translations/languages/")) {
		into("assets/apex/lang")
	}

	from(project.files("LICENSES")) {
		into("META-INF/apex/LICENSES")

		exclude("LicenseRef-Hypixel-SkyBlock-Pack.txt")
	}

	from(project.files("third-party-licenses")) {
		into("META-INF/apex/LICENSES")
	}

	from(project.files("LICENSE"))
	from(project.files("LICENSE", "NOTICE")) {
		into("META-INF/apex")
	}
}

tasks.scanLicenses {

	outputFile.set(layout.buildDirectory.file("LICENSES-APEX.json"))
	licenseFormatter.set(moe.nea.licenseextractificator.JsonLicenseFormatter())
}
tasks.register("printAllLicenses", LicenseDiscoveryTask::class.java, licensing).configure {
	outputFile.set(layout.buildDirectory.file("LICENSES-APEX.txt"))
	licenseFormatter.set(moe.nea.licenseextractificator.TextLicenseFormatter())

	doLast {
		println(outputFile.get().asFile.readText())
	}
	outputs.upToDateWhen { false }
}
fun patchRenderDoc(
	javaLauncher: JavaLauncher,
): JavaLauncher {
	val wrappedJavaExecutable = javaLauncher.executablePath.asFile.absolutePath
	require("\"" !in wrappedJavaExecutable)
	val hashBytes = wrappedJavaExecutable.encodeToByteArray()
	val hash = Base64.getUrlEncoder().encodeToString(hashBytes)
		.replace("=", "")
	val wrapperJavaRoot = rootProject.layout.buildDirectory
		.dir("binaries/renderdoc-wrapped-java/$hash/")
		.get()
	val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
	val wrapperJavaExe =
		if (isWindows) wrapperJavaRoot.file("java.cmd")
		else wrapperJavaRoot.file("java")
	return object : JavaLauncher {
		override fun getMetadata(): JavaInstallationMetadata {
			return object : JavaInstallationMetadata by javaLauncher.metadata {
				override fun isCurrentJvm(): Boolean {
					return false
				}
			}
		}

		override fun getExecutablePath(): RegularFile {
			val fileF = wrapperJavaExe.asFile
			if (!fileF.exists()) {
				fileF.parentFile.mkdirs()
				if (isWindows) {
					fileF.writeText(
						"""
					setlocal enableextensions
					start "" renderdoccmd.exe capture --opt-hook-children --wait-for-exit --working-dir . "$wrappedJavaExecutable" %*
					endlocal
					""".trimIndent()
					)
				} else {
					fileF.writeText(
						"""
					#!/usr/bin/env bash
					exec renderdoccmd capture --opt-hook-children --wait-for-exit --working-dir . "$wrappedJavaExecutable" "$@"
					""".trimIndent()
					)
					fileF.setExecutable(true)
				}
			}
			return wrapperJavaExe
		}
	}
}
tasks.runClient {
	javaLauncher.set(
		javaToolchains.launcherFor(java.toolchain)

	)
}

tasks.withType<AbstractArchiveTask>().configureEach {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
}
