/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.pli;

import java.util.Locale;

/**
 * Parsed PL/I PICTURE specification.
 *
 * <p>
 * Character pictures consist of {@code X}, {@code A} and {@code 9}. Numeric
 * pictures support digit positions ({@code 9 Z * Y}), the assumed decimal point
 * {@code V}, sign characters ({@code S + -}), overpunched signs
 * ({@code T I R}), currency {@code $}, insertion characters ({@code . , / B})
 * and the {@code CR}/{@code DB} credit symbols. Drifting sign or currency
 * characters count as digit positions after the first one. Floating point and
 * scaling pictures ({@code E}, {@code K}, {@code F}) are rejected.
 * </p>
 *
 * @param length    number of storage bytes
 * @param precision number of digit positions for numeric pictures
 * @param scale     digit positions after {@code V}
 * @param numeric   whether the picture is numeric
 */
record PliPicture(long length, int precision, int scale, boolean numeric) {

	static PliPicture parse(String specification) {
		String expanded = expand(specification.toUpperCase(Locale.ROOT));
		if (expanded.isEmpty()) {
			throw new IllegalArgumentException("Empty PICTURE specification.");
		}
		if (expanded.chars().allMatch(c -> c == 'X' || c == 'A' || c == '9')
				&& expanded.chars().anyMatch(c -> c == 'X' || c == 'A')) {
			return new PliPicture(expanded.length(), 0, 0, false);
		}
		long length = 0;
		int precision = 0;
		int scale = 0;
		boolean afterV = false;
		boolean signSeen = false;
		char drifting = 0;
		for (int i = 0; i < expanded.length(); i++) {
			char c = expanded.charAt(i);
			if ((c == 'C' && next(expanded, i) == 'R') || (c == 'D' && next(expanded, i) == 'B')) {
				if (signSeen) {
					throw new IllegalArgumentException("Multiple sign characters in PICTURE: " + specification);
				}
				signSeen = true;
				length += 2;
				i++;
				drifting = 0;
				continue;
			}
			switch (c) {
			case 'V':
				if (afterV) {
					throw new IllegalArgumentException("Multiple V characters in PICTURE: " + specification);
				}
				afterV = true;
				continue;
			case '9', 'Z', '*', 'Y', 'T', 'I', 'R':
				if (c == 'T' || c == 'I' || c == 'R') {
					if (signSeen) {
						throw new IllegalArgumentException("Multiple sign characters in PICTURE: " + specification);
					}
					signSeen = true;
				}
				precision++;
				if (afterV) {
					scale++;
				}
				drifting = 0;
				break;
			case 'S', '+', '-', '$':
				if (drifting == c) {
					precision++;
					if (afterV) {
						scale++;
					}
				} else {
					if (c != '$') {
						if (signSeen) {
							throw new IllegalArgumentException("Multiple sign characters in PICTURE: " + specification);
						}
						signSeen = true;
					}
					drifting = c;
				}
				break;
			case '.', ',', '/', 'B':
				break;
			case 'E', 'K', 'F':
				throw new IllegalArgumentException(
						"Floating point and scaling-factor pictures are not supported: " + specification);
			default:
				throw new IllegalArgumentException("Unsupported PICTURE character '" + c + "': " + specification);
			}
			length++;
		}
		if (precision == 0) {
			throw new IllegalArgumentException("Numeric PICTURE has no digit positions: " + specification);
		}
		return new PliPicture(length, precision, scale, true);
	}

	private static char next(String value, int index) {
		return index + 1 < value.length() ? value.charAt(index + 1) : 0;
	}

	/**
	 * Expands repetition factors such as {@code (5)9}.
	 */
	private static String expand(String specification) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < specification.length(); i++) {
			char c = specification.charAt(i);
			if (Character.isWhitespace(c)) {
				continue;
			}
			if (c != '(') {
				builder.append(c);
				continue;
			}
			int end = specification.indexOf(')', i);
			if (end < 0 || end + 1 >= specification.length()) {
				throw new IllegalArgumentException("Invalid repetition factor in PICTURE: " + specification);
			}
			int count;
			try {
				count = Integer.parseInt(specification.substring(i + 1, end).trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid repetition factor in PICTURE: " + specification, e);
			}
			if (count <= 0) {
				throw new IllegalArgumentException("Invalid repetition factor in PICTURE: " + specification);
			}
			char repeated = specification.charAt(end + 1);
			builder.append(String.valueOf(repeated).repeat(count));
			i = end + 1;
		}
		return builder.toString();
	}
}
