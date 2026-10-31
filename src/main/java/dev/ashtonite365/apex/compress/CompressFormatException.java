// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.io.IOException;

public final class CompressFormatException extends IOException {
	public CompressFormatException(String message) {
		super(message);
	}
}
