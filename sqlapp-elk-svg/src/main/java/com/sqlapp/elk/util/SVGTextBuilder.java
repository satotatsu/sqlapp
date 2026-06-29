/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk.util;

import com.sqlapp.util.CommonUtils;

public class SVGTextBuilder {
	private String[] args;
	private double maxLength;
	private int startCount;
	private int count;
	private String text;

	public SVGTextBuilder(String[] args) {
		this.args = args;
		MaxLengthCalculator calc = new MaxLengthCalculator(0.0, 12);
		calc.add(args);
		maxLength = calc.calc();
		text = build();
	}

	private String build() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			append(builder, arg);
		}
		return builder.toString();
	}

	private void append(StringBuilder builder, String arg) {
		if (CommonUtils.isEmpty(arg)) {
			return;
		}
		int second = (int) TextWidthUtils.estimateTextWidth("a"); // 2段目以降を少し下げるでデフォルト
		if (builder.length() == 0) {
			builder.append(EscapeUtils.escapeXml(arg));
			startCount = (int) TextWidthUtils.estimateTextWidth(arg);
			count++;
			return;
		}
		builder.append("\n");
		builder.append(String.format("<tspan dx=\"-%sem\" dy=\"%sem\">", startCount - second, 1.2 * count));
		builder.append(EscapeUtils.escapeXml(arg));
		builder.append("</tspan>");
		count++;
	}

	public double getMaxLength() {
		return maxLength;
	}

	public int getCount() {
		return count;
	}

	public String getText() {
		return text;
	}

}
