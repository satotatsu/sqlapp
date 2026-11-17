/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */
package com.sqlapp.data.converter;

import java.util.Locale;
import java.util.Set;

/**
 * WKT / EWKT の構文検証。
 *
 * Geolatte の WKT デコーダは末尾の余分な文字(例 "POINT(1 2) junk")や座標の不足(例 "POINT(1)")を受理するため、
 * デコード前に入力全体の構文を検証する。図形の種類ごとの入れ子の構造は Geolatte のデコーダに任せ、ここでは次を検証する。
 * <ul>
 * <li>先頭の "SRID=n;" の形式</li>
 * <li>図形のキーワードと次元指定(Z / M / ZM、PostGIS の POINTM 形式)</li>
 * <li>括弧の対応と、入力の末尾に余分な文字がないこと</li>
 * <li>座標の個数が2~4で、1つの図形の中でそろっていること、次元指定と一致すること</li>
 * </ul>
 */
final class WktSyntaxValidator {

	private static final Set<String> KEYWORDS = Set.of("POINT", "LINESTRING", "POLYGON", "MULTIPOINT",
			"MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION", "CIRCULARSTRING", "COMPOUNDCURVE",
			"CURVEPOLYGON", "MULTICURVE", "MULTISURFACE", "POLYHEDRALSURFACE", "TIN", "TRIANGLE", "LINEARRING");

	private final String text;
	private int pos;

	private WktSyntaxValidator(final String text) {
		this.text = text;
	}

	/**
	 * WKT / EWKT の構文を検証します。
	 *
	 * @throws IllegalArgumentException
	 *             構文が不正な場合
	 */
	static void validate(final String text) {
		final WktSyntaxValidator validator = new WktSyntaxValidator(text);
		validator.skipWhitespace();
		if (validator.text.regionMatches(true, validator.pos, "SRID=", 0, 5)) {
			validator.pos += 5;
			if (validator.peek() == '-') {
				validator.pos++;
			}
			if (validator.readDigits() == 0) {
				throw validator.error("SRID must be an integer");
			}
			validator.expect(';');
		}
		validator.skipWhitespace();
		validator.geometry();
		validator.skipWhitespace();
		if (validator.pos != validator.text.length()) {
			throw validator.error("unexpected character");
		}
	}

	/** 1つの図形の座標の個数 */
	private static final class TupleSize {
		int size;
	}

	private void geometry() {
		final int start = pos;
		final String word = readWord();
		if (word.isEmpty()) {
			throw error("geometry keyword expected");
		}
		String keyword = word.toUpperCase(Locale.ROOT);
		final TupleSize tupleSize = new TupleSize();
		if (!KEYWORDS.contains(keyword) && keyword.endsWith("M")
				&& KEYWORDS.contains(keyword.substring(0, keyword.length() - 1))) {
			// PostGIS の "POINTM(...)" 形式
			keyword = keyword.substring(0, keyword.length() - 1);
			tupleSize.size = 3;
		}
		if (!KEYWORDS.contains(keyword)) {
			pos = start;
			throw error("unknown geometry keyword '" + word + "'");
		}
		skipWhitespace();
		String next = peekWord();
		if (tupleSize.size == 0) {
			switch (next) {
			case "Z":
			case "M":
				tupleSize.size = 3;
				readWord();
				break;
			case "ZM":
				tupleSize.size = 4;
				readWord();
				break;
			default:
				break;
			}
			skipWhitespace();
			next = peekWord();
		}
		if ("EMPTY".equals(next)) {
			readWord();
			return;
		}
		if ("GEOMETRYCOLLECTION".equals(keyword)) {
			expect('(');
			do {
				skipWhitespace();
				geometry();
				skipWhitespace();
			} while (tryConsume(','));
			expect(')');
			return;
		}
		list(tupleSize);
	}

	/**
	 * "(" 要素 ("," 要素)* ")" を読みます。要素は座標、括弧で囲んだ列、EMPTY、または種類付きの図形(曲線の構成要素など)。
	 */
	private void list(final TupleSize tupleSize) {
		expect('(');
		do {
			skipWhitespace();
			final char c = peek();
			if (c == '(') {
				list(tupleSize);
			} else if (Character.isLetter(c)) {
				if ("EMPTY".equals(peekWord())) {
					readWord();
				} else {
					geometry();
				}
			} else {
				tuple(tupleSize);
			}
			skipWhitespace();
		} while (tryConsume(','));
		expect(')');
	}

	private void tuple(final TupleSize tupleSize) {
		final int start = pos;
		int count = 0;
		while (true) {
			readNumber();
			count++;
			final int save = pos;
			skipWhitespace();
			if (pos == save) {
				break;
			}
			final char c = peek();
			if (c == ',' || c == ')' || c == 0) {
				break;
			}
		}
		if (count < 2 || count > 4) {
			pos = start;
			throw error("coordinate must have 2 to 4 values but had " + count);
		}
		if (tupleSize.size == 0) {
			tupleSize.size = count;
		} else if (tupleSize.size != count) {
			pos = start;
			throw error("coordinate must have " + tupleSize.size + " values but had " + count);
		}
	}

	private void readNumber() {
		final int start = pos;
		if (peek() == '+' || peek() == '-') {
			pos++;
		}
		int digits = readDigits();
		if (peek() == '.') {
			pos++;
			digits += readDigits();
		}
		if (digits == 0) {
			pos = start;
			throw error("number expected");
		}
		if (peek() == 'e' || peek() == 'E') {
			pos++;
			if (peek() == '+' || peek() == '-') {
				pos++;
			}
			if (readDigits() == 0) {
				throw error("invalid exponent");
			}
		}
	}

	private int readDigits() {
		int count = 0;
		while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
			pos++;
			count++;
		}
		return count;
	}

	private String readWord() {
		final int start = pos;
		while (pos < text.length() && Character.isLetter(text.charAt(pos))) {
			pos++;
		}
		return text.substring(start, pos);
	}

	private String peekWord() {
		final int save = pos;
		final String word = readWord().toUpperCase(Locale.ROOT);
		pos = save;
		return word;
	}

	private char peek() {
		return pos < text.length() ? text.charAt(pos) : 0;
	}

	private boolean tryConsume(final char c) {
		if (peek() == c) {
			pos++;
			return true;
		}
		return false;
	}

	private void expect(final char c) {
		skipWhitespace();
		if (peek() != c) {
			throw error("'" + c + "' expected");
		}
		pos++;
	}

	private void skipWhitespace() {
		while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
			pos++;
		}
	}

	private IllegalArgumentException error(final String message) {
		return new IllegalArgumentException(
				"Invalid WKT: \"" + text + "\" (" + message + " at position " + pos + ")");
	}
}
