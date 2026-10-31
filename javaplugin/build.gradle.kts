plugins {
	java
	idea
	id("apex.common")
}
dependencies {
	implementation("net.fabricmc:stitch:0.6.2")
	implementation(libs.jspecify)
}
val compilerModules = listOf("util", "comp", "tree", "api", "code")
	.map { "jdk.compiler/com.sun.tools.javac.$it" }

tasks.withType(JavaCompile::class) {
	val module = "ALL-UNNAMED"
	options.compilerArgs.addAll(
		compilerModules.map { "--add-exports=$it=$module" }
	)
}
