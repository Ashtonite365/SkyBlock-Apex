#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2025 Linnea Gräf <nea@nea.moe>
# SPDX-License-Identifier: GPL-3.0-or-later
dbg() {
	echo "$@" >&2
}

THIS_VERSION=$(git describe --tags --abbrev=0 HEAD|tr -d '\n')
LAST_VERSION=$(git describe --tags --abbrev=0 HEAD^|tr -d '\n')
echo "**Full Changelog**: <https://github.com/nea89o/Firmament/compare/$LAST_VERSION...$THIS_VERSION>"
git log --pretty='- %s ~%aN' --grep '[no changelog]' --invert-grep --fixed-strings "$LAST_VERSION..$THIS_VERSION" | sort
