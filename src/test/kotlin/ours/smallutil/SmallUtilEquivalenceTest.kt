// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.smallutil

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.minecraft.world.phys.Vec2
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.asm.AsmAnnotationUtil
import dev.ashtonite365.apex.util.async.discard
import dev.ashtonite365.apex.util.math.Projections
import dev.ashtonite365.apex.util.net.HttpUtil

enum class OursProbeMode { ALPHA, BETA, GAMMA }

@Retention(AnnotationRetention.RUNTIME)
annotation class OursProbeInner(val label: String = "inner-default", val weight: Int = 7)

@Retention(AnnotationRetention.RUNTIME)
annotation class OursProbe(
	val text: String = "d",
	val number: Int = 3,
	val big: Long = 4L,
	val flag: Boolean = true,
	val ratio: Double = 0.5,
	val small: Float = 1.5f,
	val tiny: Byte = 1,
	val short: Short = 2,
	val letter: Char = 'q',
	val mode: OursProbeMode = OursProbeMode.ALPHA,
	val modes: Array<OursProbeMode> = [],
	val names: Array<String> = [],
	val numbers: IntArray = [],
	val type: kotlin.reflect.KClass<*> = Any::class,
	val types: Array<kotlin.reflect.KClass<*>> = [],
	val inner: OursProbeInner = OursProbeInner(),
	val inners: Array<OursProbeInner> = [],
)

@OursProbe(text = "hello", number = 42, big = 9000000000L, flag = false, ratio = 2.25, small = -3.5f, tiny = 9, short = 300, letter = 'Z',
	mode = OursProbeMode.GAMMA, modes = [OursProbeMode.BETA, OursProbeMode.ALPHA], names = ["a", "b c"], numbers = [5, 6, 7],
	type = String::class, types = [Int::class, List::class], inner = OursProbeInner(label = "x", weight = 1),
	inners = [OursProbeInner(label = "p"), OursProbeInner(weight = 99)])
class OursProbeFull

@OursProbe
class OursProbeEmpty

@OursProbe(names = ["only"])
class OursProbeOne

@Serializable
data class OursHttpProbe(val name: String, val count: Int = 1, val tags: List<String> = listOf())

class SmallUtilEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun attempt(block: () -> Any?): String = try {
		block().toString()
	} catch (e: Exception) {
		"threw ${e.javaClass.simpleName}: ${e.message}"
	}

	private fun failureClass(block: () -> Any?): String = try {
		block().toString()
	} catch (e: Exception) {
		generateSequence<Throwable>(e) { it.cause }.joinToString(" <- ", prefix = "threw ") { it.javaClass.simpleName }
	}

	private fun f(v: Float) = "%.5f".format(v)
	private fun v(v: Vec2?) = v?.let { "(${f(it.x)}, ${f(it.y)})" } ?: "null"

	@Test
	fun projectionsAreTheSame() {
		val out = mutableListOf<String>()
		val two = Projections.Two
		out += "const ${two.ε} ${"%.8f".format(two.π)} ${"%.8f".format(two.τ)}"
		out += "nullish ${two.isNullish(0f)} ${two.isNullish(1e-7f)} ${two.isNullish(-1e-7f)} ${two.isNullish(1e-5f)} ${two.isNullish(-0.5f)}"
		for (deg in -720..720 step 15) out += "box $deg ${v(two.projectAngleOntoUnitBox(Math.toRadians(deg.toDouble())))}"
		for (r in listOf(0.0, 1e-9, Math.PI / 4, -Math.PI / 4, 3.0, 100.0, -100.0, Double.MAX_VALUE)) out += "box r=$r ${v(two.projectAngleOntoUnitBox(r))}"
		val dirs = listOf(Vec2(0f, 1f), Vec2(1f, 0f), Vec2(1f, 1f), Vec2(-2f, 3f), Vec2(0.5f, -0.25f), Vec2(1e-7f, 5f), Vec2(4f, 1e-7f))
		for (o in listOf(Vec2(0f, 0f), Vec2(3f, 4f), Vec2(-1f, 2f))) for (d in dirs) out += "xint ${v(o)} ${v(d)} ${v(two.xInterceptOfLine(o, d))}"
		for (dist in listOf(0f, 2f, -3f)) for (slope in listOf(0f, 1e-7f, 0.5f, -2f, 10f)) out += "card $dist $slope ${two.interceptAlongCardinal(dist, slope)?.let(::f)}"
		OursGolden.check("smallutil-projections", out)
	}

	private fun annotationOf(type: Class<*>): AnnotationNode {
		val node = ClassNode()
		type.classLoader.getResourceAsStream(type.name.replace('.', '/') + ".class")!!.use { ClassReader(it).accept(node, 0) }
		return node.visibleAnnotations.single { it.desc.contains("OursProbe;") }
	}

	private fun show(value: Any?): String = when (value) {
		null -> "null"
		is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { show(it) }
		is IntArray -> value.contentToString()
		is Class<*> -> "class " + value.name
		is OursProbeInner -> "inner(" + show(value.label) + ", " + show(value.weight) + ")"
		else -> value.javaClass.simpleName + ":" + value
	}

	@Test
	fun annotationProxiesAnswerTheSame() {
		val out = mutableListOf<String>()
		for (type in listOf(OursProbeFull::class.java, OursProbeEmpty::class.java, OursProbeOne::class.java)) {
			val node = annotationOf(type)
			out += "${type.simpleName} values=${node.values?.size}"
			val proxy = try {
				AsmAnnotationUtil.createProxy(OursProbe::class.java, node)
			} catch (e: Exception) {
				out += "  create threw ${e.javaClass.simpleName}"
				continue
			}
			for (m in OursProbe::class.java.declaredMethods.sortedBy { it.name })
				out += "  ${m.name} = ${failureClass { show(m.invoke(proxy)) }}"
			out += "  toString=${failureClass { show(proxy.toString()) }} hash=${failureClass { proxy.hashCode() }} eq=${failureClass { proxy.equals(proxy) }}"
		}
		out += "wrong ${failureClass { AsmAnnotationUtil.createProxy(OursProbeInner::class.java, annotationOf(OursProbeFull::class.java)) }}"
		OursGolden.check("smallutil-asm", out)
	}

	private fun feed(handler: HttpResponse.BodyHandler<*>, chunks: List<String>, fail: Boolean = false): String {
		val info = object : HttpResponse.ResponseInfo {
			override fun statusCode() = 200
			override fun headers(): HttpHeaders = HttpHeaders.of(mapOf()) { _, _ -> true }
			override fun version() = HttpClient.Version.HTTP_1_1
		}
		val subscriber = handler.apply(info)
		val requested = mutableListOf<Long>()
		subscriber.onSubscribe(object : Flow.Subscription {
			override fun request(n: Long) { requested += n }
			override fun cancel() {}
		})
		for (c in chunks) subscriber.onNext(listOf(ByteBuffer.wrap(c.toByteArray())))
		if (fail) subscriber.onError(IllegalStateException("boom")) else subscriber.onComplete()
		return attempt { subscriber.body.toCompletableFuture().get() } + " requested=${requested.isNotEmpty()}"
	}

	@Test
	fun httpHelpersBuildTheSame() {
		val out = mutableListOf<String>()
		for (url in listOf("https://example.com/a?b=c", "http://localhost:1234/x y".replace(" ", "%20"))) {
			val r = HttpUtil.request(url)
			r.header("x-probe", "1")
			val built = r.request.build()
			out += "req ${built.uri()} ${built.method()} ${built.headers().map().filterKeys { it != "user-agent" }} ua=${built.headers().firstValue("user-agent").get().substringBefore('/')} timeout=${built.timeout()} expect=${built.expectContinue()}"
		}
		out += "uri-overloads ${HttpUtil.request(URI("https://e.org/p")).request.build().uri()} ${HttpUtil.request(URI("https://e.org/q").toURL()).request.build().uri()}"
		out += "bad ${attempt { HttpUtil.request("not a uri") }}"
		out += "client redirect=${HttpUtil.httpClient.followRedirects()}"
		out += "equals ${HttpUtil.Request(java.net.http.HttpRequest.newBuilder()).let { it == it }}"
		val probe = HttpUtil.jsonBodyHandler(OursHttpProbe.serializer())
		out += "json1 " + feed(probe, listOf("{\"name\":\"a\",", "\"count\":5}"))
		out += "json2 " + feed(probe, listOf("{\"name\":\"b\"}"))
		out += "json3 " + feed(probe, listOf("{\"count\":5}"))
		out += "json4 " + feed(probe, listOf("{\"name\":\"c\""), fail = true)
		out += "json5 " + feed(HttpUtil.jsonBodyHandler(MapSerializer(String.serializer(), Double.serializer())), listOf("{\"X\":1.5,\"Y\":2}"))
		out += "json6 " + feed(probe, listOf("{\"name\":\"d\",\"extra\":true}"))
		val done = CompletableFuture.completedFuture(5).discard()
		out += "discard ${done.get()} ${done.isDone} ${attempt { CompletableFuture.failedFuture<Int>(IllegalStateException("x")).discard().get() }}"
		OursGolden.check("smallutil-http", out)
	}
}
