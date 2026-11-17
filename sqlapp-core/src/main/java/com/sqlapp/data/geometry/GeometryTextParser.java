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
package com.sqlapp.data.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * 幾何型の文字列表現の解析。
 *
 * PostgreSQL の幾何型の入力構文に合わせ、入力全体を構文どおりに読み切ったことと、座標数・点数を検証する。 1点あたりの座標数(dimension)は 2 または
 * 3 を指定できる。
 *
 * <pre>
 * 点        : "(" x "," y ")" | x "," y
 * 点の列    : "[" 点 ("," 点)* "]"           … 開いた列(path, lseg, line のみ)
 *           | "(" 点 ("," 点)* ")"           … 外側の括弧
 *           | 点 ("," 点)*
 *           | "(" x1 "," y1 "," ... ")"     … 座標の平坦な列を括弧で囲んだもの
 * 円        : "&lt;" 点 "," r "&gt;" | "(" 点 "," r ")" | 点 "," r
 * 直線係数  : "{" A "," B "," C "}"
 * </pre>
 *
 * 数値は Java の浮動小数点表記に加え、PostgreSQL の NaN / Infinity / inf(大文字小文字を区別しない)を受け付ける。
 * PostgreSQL は開き括弧と閉じ括弧の種類が一致しない入力(例 "[(0,0),(1,1))")も受理するが、ここでは拒否する。
 */
final class GeometryTextParser {

	/** 点の列を囲む括弧 */
	enum Enclosure {
		NONE, PAREN, BRACKET
	}

	/** 点の列の解析結果 */
	static final class PointList {
		final double[][] points;
		final Enclosure enclosure;

		PointList(final double[][] points, final Enclosure enclosure) {
			this.points = points;
			this.enclosure = enclosure;
		}

		/** 座標を1次元配列にしたもの */
		double[] flatten(final int dimension) {
			final double[] ret = new double[points.length * dimension];
			for (int i = 0; i < points.length; i++) {
				System.arraycopy(points[i], 0, ret, i * dimension, dimension);
			}
			return ret;
		}
	}

	private final String text;
	private final String typeName;
	private int pos;

	private GeometryTextParser(final String text, final String typeName) {
		if (text == null) {
			throw new IllegalArgumentException("Invalid " + typeName + " text: null");
		}
		this.text = text;
		this.typeName = typeName;
	}

	/**
	 * 単一の点を解析します。
	 */
	static double[] parsePoint(final String text, final int dimension, final String typeName) {
		final GeometryTextParser parser = new GeometryTextParser(text, typeName);
		parser.skipWhitespace();
		final double[] ret = parser.readPoint(dimension);
		parser.expectEnd();
		return ret;
	}

	/**
	 * 点の列を解析します。
	 *
	 * @param minPoints     最小の点数
	 * @param maxPoints     最大の点数(上限なしは {@link Integer#MAX_VALUE})
	 * @param allowBracket  "[...]" を受け付けるか
	 */
	static PointList parsePoints(final String text, final int dimension, final int minPoints, final int maxPoints,
			final boolean allowBracket, final String typeName) {
		final GeometryTextParser parser = new GeometryTextParser(text, typeName);
		final PointList ret = parser.readPoints(dimension, allowBracket);
		parser.expectEnd();
		if (ret.points.length < minPoints || ret.points.length > maxPoints) {
			throw parser.error("point count must be " + countDescription(minPoints, maxPoints) + " but was "
					+ ret.points.length);
		}
		return ret;
	}

	/**
	 * 円を解析します。戻り値は中心の座標と半径を並べたもの。
	 *
	 * 先頭の "(" が外側の括弧("((x,y),r)" / "(x,y,r)")か中心点の括弧("(x,y),r")かは、 両方の解釈を試して決める。
	 */
	static double[] parseCircle(final String text, final int dimension, final String typeName) {
		try {
			return parseCircle(text, dimension, typeName, true);
		} catch (final IllegalArgumentException e) {
			try {
				return parseCircle(text, dimension, typeName, false);
			} catch (final IllegalArgumentException e2) {
				throw e;
			}
		}
	}

	private static double[] parseCircle(final String text, final int dimension, final String typeName,
			final boolean outerParen) {
		final GeometryTextParser parser = new GeometryTextParser(text, typeName);
		parser.skipWhitespace();
		char close = 0;
		if (parser.peek() == '<') {
			parser.pos++;
			close = '>';
		} else if (parser.peek() == '(' && outerParen) {
			parser.pos++;
			close = ')';
		}
		parser.skipWhitespace();
		final double[] center = parser.readPoint(dimension);
		parser.skipWhitespace();
		parser.expect(',');
		parser.skipWhitespace();
		final double radius = parser.readNumber();
		parser.skipWhitespace();
		if (close != 0) {
			parser.expect(close);
		}
		parser.expectEnd();
		final double[] ret = new double[dimension + 1];
		System.arraycopy(center, 0, ret, 0, dimension);
		ret[dimension] = radius;
		return ret;
	}

	/**
	 * 文字列が直線の係数表現 "{A,B,C}" で始まるか
	 */
	static boolean isCoefficientForm(final String text) {
		if (text == null) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (!Character.isWhitespace(c)) {
				return c == '{';
			}
		}
		return false;
	}

	/**
	 * 直線の係数表現 "{A,B,C}" を解析します。
	 */
	static double[] parseCoefficients(final String text, final String typeName) {
		final GeometryTextParser parser = new GeometryTextParser(text, typeName);
		parser.skipWhitespace();
		parser.expect('{');
		final double[] ret = new double[3];
		for (int i = 0; i < ret.length; i++) {
			parser.skipWhitespace();
			if (i > 0) {
				parser.expect(',');
				parser.skipWhitespace();
			}
			ret[i] = parser.readNumber();
		}
		parser.skipWhitespace();
		parser.expect('}');
		parser.expectEnd();
		return ret;
	}

	private PointList readPoints(final int dimension, final boolean allowBracket) {
		skipWhitespace();
		Enclosure enclosure = Enclosure.NONE;
		if (peek() == '[') {
			if (!allowBracket) {
				throw error("'[' is not allowed");
			}
			pos++;
			enclosure = Enclosure.BRACKET;
		} else if (peek() == '(') {
			final int save = pos;
			pos++;
			skipWhitespace();
			if (peek() == '(' || text.indexOf('(', save + 1) < 0) {
				// "((x1,y1),...)" または平坦な "(x1,y1,...)"
				enclosure = Enclosure.PAREN;
			} else {
				// "(x1,y1),(x2,y2)" の先頭の括弧は最初の点のもの
				pos = save;
			}
		}
		final List<double[]> points = new ArrayList<>();
		while (true) {
			skipWhitespace();
			points.add(readPoint(dimension));
			skipWhitespace();
			if (peek() == ',') {
				pos++;
				continue;
			}
			break;
		}
		if (enclosure == Enclosure.BRACKET) {
			expect(']');
		} else if (enclosure == Enclosure.PAREN) {
			expect(')');
		}
		return new PointList(points.toArray(new double[0][]), enclosure);
	}

	/**
	 * "(x,y)" または "x,y" を読みます。
	 */
	private double[] readPoint(final int dimension) {
		boolean paren = false;
		if (peek() == '(') {
			pos++;
			paren = true;
		}
		final double[] ret = new double[dimension];
		for (int i = 0; i < dimension; i++) {
			skipWhitespace();
			if (i > 0) {
				expect(',');
				skipWhitespace();
			}
			ret[i] = readNumber();
		}
		skipWhitespace();
		if (paren) {
			expect(')');
		}
		return ret;
	}

	private double readNumber() {
		final int start = pos;
		if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
			pos++;
		}
		final int bodyStart = pos;
		while (pos < text.length() && Character.isLetter(text.charAt(pos))) {
			pos++;
		}
		if (pos > bodyStart) {
			final String word = text.substring(bodyStart, pos).toLowerCase();
			final boolean negative = text.charAt(start) == '-';
			switch (word) {
			case "nan":
				return Double.NaN;
			case "inf":
			case "infinity":
				return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
			default:
				pos = start;
				throw error("number expected");
			}
		}
		int digits = 0;
		while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
			pos++;
			digits++;
		}
		if (pos < text.length() && text.charAt(pos) == '.') {
			pos++;
			while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
				pos++;
				digits++;
			}
		}
		if (digits == 0) {
			pos = start;
			throw error("number expected");
		}
		if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
			final int save = pos;
			pos++;
			if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
				pos++;
			}
			int expDigits = 0;
			while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
				pos++;
				expDigits++;
			}
			if (expDigits == 0) {
				pos = save;
				throw error("invalid exponent");
			}
		}
		return Double.parseDouble(text.substring(start, pos));
	}

	private char peek() {
		return pos < text.length() ? text.charAt(pos) : 0;
	}

	private void skipWhitespace() {
		while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
			pos++;
		}
	}

	private void expect(final char c) {
		if (peek() != c) {
			throw error("'" + c + "' expected");
		}
		pos++;
	}

	private void expectEnd() {
		skipWhitespace();
		if (pos != text.length()) {
			throw error("unexpected character");
		}
	}

	private IllegalArgumentException error(final String message) {
		return new IllegalArgumentException(
				"Invalid " + typeName + " text: \"" + text + "\" (" + message + " at position " + pos + ")");
	}

	private static String countDescription(final int min, final int max) {
		if (min == max) {
			return String.valueOf(min);
		}
		if (max == Integer.MAX_VALUE) {
			return min + " or more";
		}
		return min + " to " + max;
	}
}
