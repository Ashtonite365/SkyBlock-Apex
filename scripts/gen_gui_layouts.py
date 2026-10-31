#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes Apex's GUI layout XML files (src/main/resources/assets/apex/gui) from the definitions in gui_layouts.py.

    python3 scripts/gen_gui_layouts.py           # rewrite the XML files
    python3 scripts/gen_gui_layouts.py --check   # exit 1 if any file differs from what would be written

The definitions use a small builder: ``M.Tag(attr=value)[child, ...]`` makes a MoulConfig element, ``A.Tag`` an
Apex-namespace element, and ``label``/``button`` cover the two shapes that make up most layouts. Element trees are
locked by GuiLayoutTreeGoldenTest (see gui_canon.py); formatting is ours.
"""
import sys
from pathlib import Path

MOUL_NS = "http://notenoughupdates.org/moulconfig"
APEX_NS = "http://apex.ashtonite365.dev/moulconfig"
INDENT = "\t"


class Node:
	__slots__ = ("tag", "attrs", "kids")

	def __init__(self, tag, attrs):
		self.tag = tag
		self.attrs = [(k, str(v)) for k, v in attrs.items()]
		self.kids = []

	def __getitem__(self, kids):
		if not isinstance(kids, tuple):
			kids = (kids,)
		self.kids = [k for k in kids if k is not None]
		return self

	def uses_apex(self):
		return self.tag.startswith("apex:") or any(k.uses_apex() for k in self.kids)


class _Tag:
	"""``M.Row`` is a tag; calling it makes an element. Dotted tags such as ``M.Tab.Header`` chain."""

	def __init__(self, name):
		self._name = name

	def __call__(self, **attrs):
		return Node(self._name, attrs)

	def __getattr__(self, part):
		return _Tag(self._name + "." + part)


class _Namespace:
	def __init__(self, prefix):
		self._prefix = prefix

	def __getattr__(self, name):
		return _Tag(self._prefix + name)


M = _Namespace("")
A = _Namespace("apex:")


def label(text, **attrs):
	"""A single line of text."""
	return M.Text(text=text, **attrs)


def button(on_click, *content, apex=True):
	"""A clickable button; plain strings in ``content`` become labels. ``apex=False`` uses MoulConfig's own Button."""
	ns = A if apex else M
	return ns.Button(onClick=on_click)[tuple(label(c) if isinstance(c, str) else c for c in content)]


def _attr(v):
	return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def _emit(node, depth, out):
	pad = INDENT * depth
	head = node.tag + "".join(f' {k}="{_attr(v)}"' for k, v in node.attrs)
	if not node.kids:
		out.append(f"{pad}<{head}/>")
		return
	out.append(f"{pad}<{head}>")
	for k in node.kids:
		_emit(k, depth + 1, out)
	out.append(f"{pad}</{node.tag}>")


def render(body):
	"""Serialises one layout: ``body`` is the single child of the Root element."""
	root = Node("Root", {})[body]
	ns = f' xmlns="{MOUL_NS}"' + (f' xmlns:apex="{APEX_NS}"' if root.uses_apex() else "")
	out = ['<?xml version="1.0" encoding="UTF-8"?>']
	lines = []
	_emit(root, 0, lines)
	lines[0] = lines[0].replace("<Root>", f"<Root{ns}>", 1)
	out += lines
	return "\n".join(out) + "\n"


def main(argv):
	sys.path.insert(0, str(Path(__file__).resolve().parent))
	import gui_layouts

	gui_root = Path(__file__).resolve().parent.parent / "src/main/resources/assets/apex/gui"
	check = "--check" in argv
	stale = []
	for rel, body in sorted(gui_layouts.LAYOUTS.items()):
		text = render(body)
		target = gui_root / rel
		if target.exists() and target.read_text(encoding="utf-8") == text:
			continue
		stale.append(rel)
		if not check:
			target.parent.mkdir(parents=True, exist_ok=True)
			target.write_text(text, encoding="utf-8")
	extra = sorted(p.relative_to(gui_root).as_posix() for p in gui_root.rglob("*.xml")
		if p.relative_to(gui_root).as_posix() not in gui_layouts.LAYOUTS)
	for rel in extra:
		print(f"not generated: {rel}", file=sys.stderr)
	for rel in stale:
		print(("stale: " if check else "wrote: ") + rel)
	return 1 if (check and stale) or extra else 0


if __name__ == "__main__":
	sys.exit(main(sys.argv[1:]))
