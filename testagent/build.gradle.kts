plugins {
	java
	id("apex.common")
	id("com.gradleup.shadow")
}
dependencies {
	implementation(libs.asm)
}
tasks.withType<Jar> {
	val agentMain = "dev.ashtonite365.apex.testagent.AgentMain"
	manifest.attributes(
		"Agent-Class" to agentMain,
		"Premain-Class" to agentMain,
	)
}
