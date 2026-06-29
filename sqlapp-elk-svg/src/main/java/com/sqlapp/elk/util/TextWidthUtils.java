package com.sqlapp.elk.util;

import java.util.Arrays;

public class TextWidthUtils {
	private static final double DEFAULT_ASCII_RATIO = 0.60;
	private static final double WIDE_CHAR_RATIO = 1.00;
	private static final double[] ASCII_WIDTH = new double[128];

	static {
		// デフォルト
		Arrays.fill(ASCII_WIDTH, DEFAULT_ASCII_RATIO);

		// 制御文字
		ASCII_WIDTH['\t'] = 0.0;
		ASCII_WIDTH['\n'] = 0.0;
		ASCII_WIDTH['\r'] = 0.0;

		// スペース
		ASCII_WIDTH[' '] = 0.35;

		// 記号
		ASCII_WIDTH['!'] = 0.32;
		ASCII_WIDTH['"'] = 0.45;
		ASCII_WIDTH['#'] = 0.70;
		ASCII_WIDTH['$'] = 0.60;
		ASCII_WIDTH['%'] = 0.90;
		ASCII_WIDTH['&'] = 0.75;
		ASCII_WIDTH['\''] = 0.25;
		ASCII_WIDTH['('] = 0.35;
		ASCII_WIDTH[')'] = 0.35;
		ASCII_WIDTH['*'] = 0.45;
		ASCII_WIDTH['+'] = 0.70;
		ASCII_WIDTH[','] = 0.25;
		ASCII_WIDTH['-'] = 0.40;
		ASCII_WIDTH['.'] = 0.25;
		ASCII_WIDTH['/'] = 0.45;
		ASCII_WIDTH[':'] = 0.25;
		ASCII_WIDTH[';'] = 0.25;
		ASCII_WIDTH['<'] = 0.70;
		ASCII_WIDTH['='] = 0.70;
		ASCII_WIDTH['>'] = 0.70;
		ASCII_WIDTH['?'] = 0.55;
		ASCII_WIDTH['@'] = 1.00;

		// 数字
		for (char c = '0'; c <= '9'; c++) {
			ASCII_WIDTH[c] = 0.55;
		}

		// 大文字
		for (char c = 'A'; c <= 'Z'; c++) {
			ASCII_WIDTH[c] = 0.65;
		}

		// 小文字
		for (char c = 'a'; c <= 'z'; c++) {
			ASCII_WIDTH[c] = 0.55;
		}

		// 幅の狭い文字
		ASCII_WIDTH['I'] = 0.32;
		ASCII_WIDTH['i'] = 0.32;
		ASCII_WIDTH['l'] = 0.32;
		ASCII_WIDTH['j'] = 0.42;
		ASCII_WIDTH['f'] = 0.42;
		ASCII_WIDTH['r'] = 0.42;
		ASCII_WIDTH['t'] = 0.42;

		// 幅の広い文字
		ASCII_WIDTH['M'] = 0.92;
		ASCII_WIDTH['W'] = 0.92;
		ASCII_WIDTH['m'] = 0.80;
		ASCII_WIDTH['w'] = 0.80;

		// DB名でよく使う記号
		ASCII_WIDTH['_'] = 0.50;
		ASCII_WIDTH['|'] = 0.32;
		ASCII_WIDTH['['] = 0.35;
		ASCII_WIDTH[']'] = 0.35;
		ASCII_WIDTH['{'] = 0.45;
		ASCII_WIDTH['}'] = 0.45;
		ASCII_WIDTH['\\'] = 0.45;
		ASCII_WIDTH['^'] = 0.55;
		ASCII_WIDTH['`'] = 0.35;
		ASCII_WIDTH['~'] = 0.75;
	}

	public static double estimateTextWidth(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		double width = 0;
		for (char c : text.toCharArray()) {
			width += charWidthRatio(c);
		}
		return Math.ceil(width);
	}

	public static double estimateTextWidth(String text, double fontSize) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		double width = 0;
		for (char c : text.toCharArray()) {
			width += charWidthRatio(c) * fontSize;
		}
		return Math.ceil(width);
	}

	public static double charWidthRatio(char c) {
		return c < ASCII_WIDTH.length ? ASCII_WIDTH[c] : WIDE_CHAR_RATIO;
	}
}
