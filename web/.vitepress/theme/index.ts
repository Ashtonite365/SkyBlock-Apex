/*
 * SPDX-FileCopyrightText: 2025 Linnea Gräf <nea@nea.moe>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import DefaultTheme from 'vitepress/theme'
import './minecraft-hover.css'
import type { Theme } from 'vitepress/client';
import tippy from 'tippy.js';
import { useData, useRoute } from 'vitepress/client';
import { onMounted, nextTick, watch } from 'vue'

const theme: Theme = {
	extends: DefaultTheme,
	setup() {
		onMounted(inject)
		const route = useRoute();
		const { page } = useData();
		const lazyInject = () => nextTick().then(inject)
		watch(() => route.path, lazyInject)
		watch(() => page.value, lazyInject)
	},
}

const attrs: Array<[string, (ns: string, path: string, attr: string | null) => string,]> = [
	["fqfi", (ns, path) => `This is a fully-qualified file identifier. You would edit the file <code>assets/${ns}/${path}</code>.`],
	["model", (ns, path, attr) => `This is a${orEmpty(attr)} model identifier. You would edit the file <code>assets/${ns}/models${orEmpty(attr, "/")}/${path}.json</code>.`],
]
const orEmpty = (str: string | null | undefined, seperator: string = " ") => str ? seperator + str : ""

function inject() {
	for (const [attr, appl] of attrs) {
		document.querySelectorAll(`code[${attr}]`).forEach(entry => {
			const [ns, path] = parseIdent(entry.innerHTML)
			const attrValue = entry.getAttribute(attr)
			tippy(entry, {
				content: appl(ns, path, attrValue),
				allowHTML: true,
				theme: 'translucent'
			})
		})
	}
}
function parseIdent(str: string): [string, string] {
	const parts = str.split(":")
	if (parts.length == 1)
		return ["minecraft", parts[0]]
	if (parts.length == 2)
		return [parts[0], parts[1]]
	throw `${str} is not a valid identifier`
}
export default theme;
