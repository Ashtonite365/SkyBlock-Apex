#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Canonical tree form of an Apex GUI layout XML file.

One line per file: Tag(attr="v",...)[child,child]. Attributes are sorted, comments and inter-element whitespace are
dropped, the MoulConfig namespace is written bare and the Apex namespace as ``apex:``. Any other namespace is written
as ``{uri}name``. Non-blank text content is reported as ``!text`` so it can never pass silently.

The Kotlin test GuiLayoutTreeGoldenTest computes the same form with the JDK DOM parser; both must agree with
src/test/resources/ours-util-golden/gui-layout-trees.tsv.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MOUL = "http://notenoughupdates.org/moulconfig"
APEX = "http://apex.ashtonite365.dev/moulconfig"


def _name(qname: str) -> str:
	if qname.startswith("{"):
		uri, local = qname[1:].split("}", 1)
		if uri == MOUL:
			return local
		if uri == APEX:
			return "apex:" + local
		return qname
	return qname


def _quote(v: str) -> str:
	return '"' + v.replace("\\", "\\\\").replace('"', '\\"') + '"'


def canon(e: ET.Element) -> str:
	out = _name(e.tag)
	if e.attrib:
		out += "(" + ",".join(_name(k) + "=" + _quote(v) for k, v in sorted(e.attrib.items(), key=lambda kv: _name(kv[0]))) + ")"
	kids = []
	if e.text and e.text.strip():
		kids.append("!text")
	for c in e:
		kids.append(canon(c))
		if c.tail and c.tail.strip():
			kids.append("!text")
	if kids:
		out += "[" + ",".join(kids) + "]"
	return out


def canon_file(p: Path) -> str:
	return canon(ET.parse(p).getroot())


def esc(s: str) -> str:
	o = []
	for ch in s:
		if ch == "\\":
			o.append("\\\\")
		elif ch == "\n":
			o.append("\\n")
		elif ch == "\r":
			o.append("\\r")
		elif ch == "\t":
			o.append("\\t")
		elif ord(ch) < 0x20:
			o.append("\\u%04x" % ord(ch))
		else:
			o.append(ch)
	return "".join(o)


def golden_rows(gui_root: Path):
	for p in sorted(gui_root.rglob("*.xml"), key=lambda q: q.relative_to(gui_root).as_posix()):
		yield esc(p.relative_to(gui_root).as_posix()) + "\t" + esc(canon_file(p))


if __name__ == "__main__":
	root = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/apex/gui")
	for row in golden_rows(root):
		print(row)
