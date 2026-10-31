// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.asm

import com.google.common.base.Defaults
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode

object AsmAnnotationUtil {

	fun <T : Annotation> createProxy(annotationClass: Class<T>, annotationNode: AnnotationNode): T {
		require(Type.getType(annotationNode.desc) == Type.getType(annotationClass))
		val handler = NodeBackedAnnotation(annotationNode)
		@Suppress("UNCHECKED_CAST")
		return Proxy.newProxyInstance(AsmAnnotationUtil::class.java.classLoader, arrayOf<Class<*>>(annotationClass), handler) as T
	}

	private class NodeBackedAnnotation(private val node: AnnotationNode) : InvocationHandler {

		private val positions: Map<String, Int> = buildMap {
			val raw: List<Any?> = node.values
			var i = 0
			while (i < raw.size) {
				put(raw[i] as String, i + 1)
				i += 2
			}
		}

		override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
			val returnType = method.returnType
			val at = positions[method.name] ?: return zeroValue(returnType)
			var depth = 0
			var element: Class<*> = returnType
			while (element.isArray) {
				element = element.componentType
				depth++
			}
			return convert(node.values[at], element, depth)
		}

		private fun zeroValue(type: Class<*>): Any? = when {
			type.isArray -> java.lang.reflect.Array.newInstance(type.componentType, 0)
			type.isPrimitive -> Defaults.defaultValue(type)
			type == String::class.java -> ""
			else -> null
		}

		private fun arrayClass(element: Class<*>, depth: Int): Class<*> {
			var type = element
			repeat(depth) { type = java.lang.reflect.Array.newInstance(type, 0).javaClass }
			return type
		}

		@Suppress("UNCHECKED_CAST")
		private fun convert(value: Any?, element: Class<*>, depth: Int): Any? {
			if (value == null) return null
			if (depth > 0) {
				val items = (value as List<Any?>).map { convert(it, element, depth - 1) }
				val target = java.lang.reflect.Array.newInstance(arrayClass(element, depth - 1), 0) as Array<Any?>
				return java.util.ArrayList(items).toArray(target)
			}
			if (element.isEnum) {
				val constant = when (value) {
					is String -> value
					is List<*> -> value[1] as String
					else -> error("Unknown enum variant $value for $element")
				}
				return java.lang.Enum.valueOf(element as Class<out Enum<*>>, constant)
			}
			return when (value) {
				is Type -> Class.forName(value.className)
				is AnnotationNode -> createProxy(element as Class<out Annotation>, value)
				is String, is Boolean, is Char, is Byte, is Short, is Int, is Long, is Float, is Double -> value
				else -> error("Unknown enum variant $value for $element")
			}
		}
	}
}
