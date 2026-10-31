// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.GuiTextRenderState
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemStack
import sun.misc.Unsafe
import java.util.Optional

object HeadlessGui {
	private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) as Unsafe }
	private val instanceField = Minecraft::class.java.getDeclaredField("instance").also { it.isAccessible = true }

	val font: Font by lazy { drawableFont() }

	val fakeGame: Minecraft by lazy {
		val mc = unsafe.allocateInstance(Minecraft::class.java) as Minecraft
		val f = Minecraft::class.java.getDeclaredField("font")
		unsafe.putObject(mc, unsafe.objectFieldOffset(f), font)

		unsafe.putObject(mc, unsafe.objectFieldOffset(Minecraft::class.java.getDeclaredField("lastInputType")), net.minecraft.client.InputType.MOUSE)

		unsafe.putObject(mc, unsafe.objectFieldOffset(Minecraft::class.java.getDeclaredField("gui")), unsafe.allocateInstance(net.minecraft.client.gui.Gui::class.java))

		unsafe.putObject(mc, unsafe.objectFieldOffset(Minecraft::class.java.getDeclaredField("textInputManager")), object : com.mojang.blaze3d.platform.TextInputManager(unsafe.allocateInstance(com.mojang.blaze3d.platform.Window::class.java) as com.mojang.blaze3d.platform.Window) {
			override fun setTextInputArea(x: Int, y: Int, w: Int, h: Int) {}
			override fun startTextInput(owner: Any) {}
			override fun stopTextInput(owner: Any) {}
			override fun stopTextInput() {}
			override fun onTextInputFocusChange(owner: Any, focused: Boolean) {}
		})
		unsafe.putObject(mc, unsafe.objectFieldOffset(Minecraft::class.java.getDeclaredField("mouseHandler")), unsafe.allocateInstance(net.minecraft.client.MouseHandler::class.java))
		val tm = unsafe.allocateInstance(net.minecraft.client.renderer.texture.TextureManager::class.java)
		unsafe.putObject(tm, unsafe.objectFieldOffset(net.minecraft.client.renderer.texture.TextureManager::class.java.getDeclaredField("byPath")), textures)
		unsafe.putObject(mc, unsafe.objectFieldOffset(Minecraft::class.java.getDeclaredField("textureManager")), tm)
		val lm = unsafe.allocateInstance(net.minecraft.client.renderer.Lightmap::class.java)
		unsafe.putObject(lm, unsafe.objectFieldOffset(net.minecraft.client.renderer.Lightmap::class.java.getDeclaredField("textureView")), gpuStub(com.mojang.renderpearl.api.textures.GpuTextureView::class.java, "view:lightmap"))
		val gr = unsafe.allocateInstance(net.minecraft.client.renderer.GameRenderer::class.java)
		unsafe.putObject(gr, unsafe.objectFieldOffset(net.minecraft.client.renderer.GameRenderer::class.java.getDeclaredField("lightmap")), lm)
		unsafe.putObject(mc, unsafe.objectFieldOffset(Minecraft::class.java.getDeclaredField("gameRenderer")), gr)
		installSamplers()
		mc
	}

	fun <T> gpuStub(type: Class<T>, name: String): T = type.cast(java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, m, args ->
		when (m.name) {
			"toString" -> name
			"hashCode" -> name.hashCode()
			"equals" -> args!![0] === self
			"isClosed" -> false
			else -> error("headless $name.${m.name}")
		}
	})

	val textures: MutableMap<Identifier, net.minecraft.client.renderer.texture.AbstractTexture> = HashMap<Identifier, net.minecraft.client.renderer.texture.AbstractTexture>().also { m ->
		for (n in listOf("x", "y")) {
			val id = Identifier.fromNamespaceAndPath("apex", n)
			m[id] = object : net.minecraft.client.renderer.texture.AbstractTexture() {
				init {
					textureView = gpuStub(com.mojang.renderpearl.api.textures.GpuTextureView::class.java, "view:$id")
					sampler = gpuStub(com.mojang.renderpearl.api.textures.GpuSampler::class.java, "sampler:$id")
				}
			}
		}
	}

	private fun installSamplers() {
		val cache = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
		val fld = com.mojang.blaze3d.systems.SamplerCache::class.java.getDeclaredField("samplers").also { it.isAccessible = true }
		val arr = fld.get(cache) as Array<Any?>
		for (i in arr.indices) if (arr[i] == null) arr[i] = gpuStub(com.mojang.renderpearl.api.textures.GpuSampler::class.java, "sampler#$i")
	}

	fun <T> withFakeGame(block: () -> T): T {
		val prev = instanceField.get(null)
		instanceField.set(null, fakeGame)
		try { return block() } finally { instanceField.set(null, prev) }
	}

	class VertexLog(val log: MutableList<String>) : com.mojang.blaze3d.vertex.VertexConsumer {
		private fun r(v: Float) = if (v == Math.round(v).toFloat()) Math.round(v).toString() else "%.4f".format(v)
		override fun addVertex(x: Float, y: Float, z: Float) = also { log += "v ${r(x)} ${r(y)} ${r(z)}" }
		override fun setColor(r: Int, g: Int, b: Int, a: Int) = also { log += "c $r $g $b $a" }
		override fun setColor(argb: Int) = also { log += "c ${Integer.toHexString(argb)}" }
		override fun setUv(u: Float, v: Float) = also { log += "uv ${r(u)} ${r(v)}" }
		override fun setUv1(u: Int, v: Int) = also { log += "uv1 $u $v" }
		override fun setUv2(u: Int, v: Int) = also { log += "uv2 $u $v" }
		override fun setUv3(u: Float, v: Float) = also { log += "uv3 ${r(u)} ${r(v)}" }
		override fun setNormal(x: Float, y: Float, z: Float) = also { log += "n ${r(x)} ${r(y)} ${r(z)}" }
		override fun setLineWidth(w: Float) = also { log += "w ${r(w)}" }
	}

	fun drawableFont(advance: (Int) -> Int = vanillaAdvance): Font {
		val cache = HashMap<Int, net.minecraft.client.gui.font.glyphs.BakedGlyph>()
		fun glyph(cp: Int) = cache.getOrPut(cp) {
			val info = object : com.mojang.blaze3d.font.GlyphInfo {
				override fun getAdvance(): Float = advance(cp).toFloat()
				override fun getBoldOffset(): Float = 1f
			}
			object : net.minecraft.client.gui.font.glyphs.BakedGlyph {
				override fun info() = info
				override fun createGlyph(x: Float, y: Float, color: Int, shadow: Int, style: net.minecraft.network.chat.Style, a: Float, b: Float): net.minecraft.client.gui.font.TextRenderable.Styled =
					Drawn("glyph $cp", x, y, x + advance(cp), y + 9f, color, shadow)
			}
		}
		val source = object : net.minecraft.client.gui.GlyphSource {
			override fun getGlyph(cp: Int) = glyph(cp)
			override fun getRandomGlyph(random: net.minecraft.util.RandomSource, width: Int) = glyph('?'.code)
		}
		return Font(object : Font.Provider {
			override fun glyphs(desc: net.minecraft.network.chat.FontDescription) = source
			override fun effect() = net.minecraft.client.gui.font.glyphs.EffectGlyph { x0, y0, x1, y1, z, color, shadow, off ->
				Drawn("effect z=$z off=$off", x0, y0, x1, y1, color, shadow)
			}
		})
	}

	private val fontView by lazy { gpuStub(com.mojang.renderpearl.api.textures.GpuTextureView::class.java, "view:font") }

	private class Drawn(val tag: String, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: Int, val shadow: Int) : net.minecraft.client.gui.font.TextRenderable.Styled {
		override fun render(pose: org.joml.Matrix4fc, vc: com.mojang.blaze3d.vertex.VertexConsumer, light: Int, seeThrough: Boolean) {
			(vc as? VertexLog)?.log?.add("draw $tag shadow=${Integer.toHexString(shadow)} see=$seeThrough")
			vc.addVertex(pose, x0, y0, 0f).setColor(color).setUv2(light and 0xFFFF, light ushr 16)
			vc.addVertex(pose, x1, y1, 0f).setColor(color).setUv2(light and 0xFFFF, light ushr 16)
		}
		override fun renderType(mode: Font.DisplayMode): net.minecraft.client.renderer.rendertype.RenderType = error("headless glyph")
		override fun textureView(): com.mojang.renderpearl.api.textures.GpuTextureView = fontView
		override fun style(): net.minecraft.network.chat.Style = net.minecraft.network.chat.Style.EMPTY
		override fun guiPipeline(): RenderPipeline = if (tag.startsWith("glyph")) net.minecraft.client.renderer.RenderPipelines.GUI_TEXT else net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED
		override fun left() = x0
		override fun top() = y0
		override fun right() = x1
		override fun bottom() = y1
	}

	fun fmt(seq: FormattedCharSequence): String = encodeStyled(seq)
	fun fmt(c: Component): String = encodeStyled(c.visualOrderText)
	fun fmt(t: FormattedText): String = if (t is Component) fmt(t) else t.string
	fun item(s: ItemStack): String = if (s.isEmpty) "empty" else try {
		"${BuiltInRegistries.ITEM.getKey(s.item)}x${s.count}" + (s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)?.let { "\"" + fmt(it) + "\"" } ?: "")
	} catch (e: Throwable) { "item!" + e.javaClass.simpleName }
	private fun hex(i: Int) = Integer.toHexString(i)
	private fun p(pl: RenderPipeline?) = pl?.location?.toString()

	class RecState : GuiRenderState() {
		@JvmField var log: MutableList<String>? = null
		override fun addItem(s: GuiItemRenderState) { log!! += "state-item" }
		override fun addText(s: GuiTextRenderState) {

			fun f(n: String) = GuiTextRenderState::class.java.getDeclaredField(n).also { it.isAccessible = true }.get(s)
			val sb = StringBuilder()
			(f("text") as net.minecraft.util.FormattedCharSequence).accept { _, _, cp -> sb.appendCodePoint(cp); true }
			log!! += "state-text \"$sb\" y=${f("y")} ${Integer.toHexString(f("color") as Int)} ${f("dropShadow")}"
		}
		override fun addPicturesInPictureState(s: PictureInPictureRenderState) { log!! += "pip ${s.javaClass.simpleName}" }
		override fun addGuiElement(s: GuiElementRenderState) { log!! += "state-element ${s.javaClass.simpleName}" }
		override fun addBlitToCurrentLayer(s: BlitRenderState) { log!! += "state-blit" }
		override fun addGlyphToCurrentLayer(s: GuiElementRenderState) { log!! += "state-glyph" }
		override fun nextStratum() { log!! += "stratum" }
		override fun blurBeforeThisStratum() { log!! += "blur" }
	}

	class Rec(mc: Minecraft, s: GuiRenderState) : GuiGraphicsExtractor(mc, s, 0, 0) {
		@JvmField var log: MutableList<String>? = null
		private fun add(s: String) {
			val m = pose()
			val t = if (m.m00 == 1f && m.m01 == 0f && m.m10 == 0f && m.m11 == 1f && m.m20 == 0f && m.m21 == 0f) "" else
				" @${m.m00},${m.m01},${m.m10},${m.m11},${m.m20},${m.m21}"
			log!! += s + t
		}
		override fun guiWidth() = 480
		override fun guiHeight() = 270
		override fun nextStratum() = add("stratum")
		override fun blurBeforeThisStratum() = add("blur")
		override fun enableScissor(a: Int, b: Int, c: Int, d: Int) = add("scissor $a $b $c $d")
		override fun disableScissor() = add("unscissor")
		override fun horizontalLine(a: Int, b: Int, c: Int, d: Int) = add("hline $a $b $c ${hex(d)}")
		override fun verticalLine(a: Int, b: Int, c: Int, d: Int) = add("vline $a $b $c ${hex(d)}")
		override fun fill(a: Int, b: Int, c: Int, d: Int, e: Int) = add("fill $a $b $c $d ${hex(e)}")
		override fun fill(pl: RenderPipeline, a: Int, b: Int, c: Int, d: Int, e: Int) = add("fill ${p(pl)} $a $b $c $d ${hex(e)}")
		override fun fillGradient(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) = add("gradient $a $b $c $d ${hex(e)} ${hex(f)}")
		override fun fill(pl: RenderPipeline, t: TextureSetup, a: Int, b: Int, c: Int, d: Int) = add("fillTex ${p(pl)} $a $b $c $d")
		override fun outline(a: Int, b: Int, c: Int, d: Int, e: Int) = add("outline $a $b $c $d ${hex(e)}")
		override fun textHighlight(a: Int, b: Int, c: Int, d: Int, e: Boolean) = add("highlight $a $b $c $d $e")
		override fun text(f: Font, s: String?, x: Int, y: Int, c: Int, sh: Boolean) = add("text \"$s\" $x $y ${hex(c)} $sh")
		override fun text(f: Font, s: FormattedCharSequence, x: Int, y: Int, c: Int, sh: Boolean) = add("text ${fmt(s)} $x $y ${hex(c)} $sh")
		override fun text(f: Font, s: Component, x: Int, y: Int, c: Int, sh: Boolean) = add("text ${fmt(s)} $x $y ${hex(c)} $sh")
		override fun text(f: Font, s: String?, x: Int, y: Int, c: Int) = text(f, s, x, y, c, true)
		override fun text(f: Font, s: FormattedCharSequence, x: Int, y: Int, c: Int) = text(f, s, x, y, c, true)
		override fun text(f: Font, s: Component, x: Int, y: Int, c: Int) = text(f, s, x, y, c, true)
		override fun centeredText(f: Font, s: Component, x: Int, y: Int, c: Int) = add("ctext ${fmt(s)} $x $y ${hex(c)}")
		override fun centeredText(f: Font, s: FormattedCharSequence, x: Int, y: Int, c: Int) = add("ctext ${fmt(s)} $x $y ${hex(c)}")
		override fun textWithWordWrap(f: Font, s: FormattedText, x: Int, y: Int, w: Int, c: Int, sh: Boolean): Int { add("wrap ${fmt(s)} $x $y $w ${hex(c)} $sh"); return 0 }
		override fun textWithWordWrap(f: Font, s: FormattedText, x: Int, y: Int, w: Int, c: Int): Int = textWithWordWrap(f, s, x, y, w, c, true)
		override fun textWithBackdrop(f: Font, s: Component, x: Int, y: Int, w: Int, c: Int) = add("backdrop ${fmt(s)} $x $y $w ${hex(c)}")
		override fun blit(pl: RenderPipeline, id: Identifier, x: Int, y: Int, u: Float, v: Float, w: Int, h: Int, tw: Int, th: Int, c: Int) = add("blit ${p(pl)} $id $x $y $u $v $w $h $tw $th ${hex(c)}")
		override fun blit(pl: RenderPipeline, id: Identifier, x: Int, y: Int, u: Float, v: Float, w: Int, h: Int, tw: Int, th: Int) = add("blit ${p(pl)} $id $x $y $u $v $w $h $tw $th")
		override fun blit(pl: RenderPipeline, id: Identifier, x: Int, y: Int, u: Float, v: Float, w: Int, h: Int, rw: Int, rh: Int, tw: Int, th: Int) = add("blit ${p(pl)} $id $x $y $u $v $w $h $rw $rh $tw $th")
		override fun blit(pl: RenderPipeline, id: Identifier, x: Int, y: Int, u: Float, v: Float, w: Int, h: Int, rw: Int, rh: Int, tw: Int, th: Int, c: Int) = add("blit ${p(pl)} $id $x $y $u $v $w $h $rw $rh $tw $th ${hex(c)}")
		override fun blit(id: Identifier, a: Int, b: Int, c: Int, d: Int, e: Float, f: Float, g: Float, h: Float) = add("blit $id $a $b $c $d $e $f $g $h")
		override fun blitSprite(pl: RenderPipeline, id: Identifier, x: Int, y: Int, w: Int, h: Int) = add("sprite ${p(pl)} $id $x $y $w $h")
		override fun blitSprite(pl: RenderPipeline, id: Identifier, x: Int, y: Int, w: Int, h: Int, a: Float) = add("sprite ${p(pl)} $id $x $y $w $h a=$a")
		override fun blitSprite(pl: RenderPipeline, id: Identifier, x: Int, y: Int, w: Int, h: Int, c: Int) = add("sprite ${p(pl)} $id $x $y $w $h ${hex(c)}")
		override fun blitSprite(pl: RenderPipeline, id: Identifier, a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int) = add("sprite ${p(pl)} $id $a $b $c $d $e $f $g $h")
		override fun blitSprite(pl: RenderPipeline, id: Identifier, a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int, i: Int) = add("sprite ${p(pl)} $id $a $b $c $d $e $f $g $h ${hex(i)}")
		override fun item(s: ItemStack, x: Int, y: Int) = add("item ${item(s)} $x $y")
		override fun item(s: ItemStack, x: Int, y: Int, seed: Int) = add("item ${item(s)} $x $y seed=$seed")
		override fun item(e: net.minecraft.world.entity.LivingEntity, s: ItemStack, x: Int, y: Int, seed: Int) = add("item ${item(s)} $x $y seed=$seed")
		override fun fakeItem(s: ItemStack, x: Int, y: Int) = add("fakeItem ${item(s)} $x $y")
		override fun fakeItem(s: ItemStack, x: Int, y: Int, seed: Int) = add("fakeItem ${item(s)} $x $y seed=$seed")
		override fun itemDecorations(f: Font, s: ItemStack, x: Int, y: Int) = add("deco ${item(s)} $x $y")
		override fun itemDecorations(f: Font, s: ItemStack, x: Int, y: Int, t: String?) = add("deco ${item(s)} $x $y \"$t\"")
		override fun setTooltipForNextFrame(c: Component, x: Int, y: Int) = add("tooltip ${fmt(c)} $x $y")
		override fun setTooltipForNextFrame(l: List<FormattedCharSequence>, x: Int, y: Int) = add("tooltip ${l.joinToString("|") { fmt(it) }} $x $y")
		override fun setTooltipForNextFrame(f: Font, s: ItemStack, x: Int, y: Int) = add("tooltip ${item(s)} $x $y")
		override fun setTooltipForNextFrame(f: Font, l: List<Component>, o: Optional<net.minecraft.world.inventory.tooltip.TooltipComponent>, x: Int, y: Int) = add("tooltip ${l.joinToString("|") { fmt(it) }} ${o.isPresent} $x $y")
		override fun setTooltipForNextFrame(f: Font, l: List<Component>, o: Optional<net.minecraft.world.inventory.tooltip.TooltipComponent>, x: Int, y: Int, bg: Identifier?) = add("tooltip ${l.joinToString("|") { fmt(it) }} ${o.isPresent} $x $y $bg")
		override fun setTooltipForNextFrame(f: Font, c: Component, x: Int, y: Int) = add("tooltip ${fmt(c)} $x $y")
		override fun setTooltipForNextFrame(f: Font, c: Component, x: Int, y: Int, bg: Identifier?) = add("tooltip ${fmt(c)} $x $y $bg")
		override fun setComponentTooltipForNextFrame(f: Font, l: List<Component>, x: Int, y: Int) = add("tooltip ${l.joinToString("|") { fmt(it) }} $x $y")
		override fun setComponentTooltipForNextFrame(f: Font, l: List<Component>, x: Int, y: Int, bg: Identifier?) = add("tooltip ${l.joinToString("|") { fmt(it) }} $x $y $bg")
		override fun setTooltipForNextFrame(f: Font, l: List<FormattedCharSequence>, x: Int, y: Int) = add("tooltip ${l.joinToString("|") { fmt(it) }} $x $y")
		override fun setTooltipForNextFrame(f: Font, l: List<FormattedCharSequence>, x: Int, y: Int, bg: Identifier?) = add("tooltip ${l.joinToString("|") { fmt(it) }} $x $y $bg")
		override fun entity(s: net.minecraft.client.renderer.entity.state.EntityRenderState, sc: Float, t: org.joml.Vector3fc, a: org.joml.Quaternionfc, b: org.joml.Quaternionfc?, x0: Int, y0: Int, x1: Int, y1: Int) = add("entity ${s.javaClass.simpleName} $sc $x0 $y0 $x1 $y1")
	}

	fun record(block: (GuiGraphicsExtractor) -> Unit): List<String> = withFakeGame {
		val log = ArrayList<String>()
		val state = unsafe.allocateInstance(RecState::class.java) as RecState
		state.log = log
		val g = unsafe.allocateInstance(Rec::class.java) as Rec
		g.log = log
		fun set(name: String, v: Any) = GuiGraphicsExtractor::class.java.getDeclaredField(name).also { it.isAccessible = true }.set(g, v)
		set("pose", org.joml.Matrix3x2fStack(16))
		set("guiRenderState", state)
		set("minecraft", fakeGame)

		val scissorType = Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor\$ScissorStack")
		val ctor = scissorType.getDeclaredConstructor(net.minecraft.client.gui.navigation.ScreenRectangle::class.java).also { it.isAccessible = true }
		set("scissorStack", ctor.newInstance(net.minecraft.client.gui.navigation.ScreenRectangle(0, 0, 480, 270)))
		set("pendingCursor", com.mojang.blaze3d.platform.cursor.CursorType.DEFAULT)
		try { block(g) } catch (e: Throwable) { log += "!${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}" }
		log
	}
}
