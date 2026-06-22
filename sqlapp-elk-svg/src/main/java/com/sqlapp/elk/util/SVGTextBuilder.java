package com.sqlapp.elk.util;

import com.sqlapp.util.CommonUtils;

public class SVGTextBuilder {
	private String[] args;
	private int maxLength;
	private int startCount;
	private int count;
	private String text;

	public SVGTextBuilder(String[] args) {
		this.args = args;
		MaxLengthCalculator calc = new MaxLengthCalculator();
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
		if (builder.length() == 0) {
			builder.append(EscapeUtils.escapeXml(arg));
			startCount = arg.length();
			count++;
			return;
		}
		builder.append("\n");
		builder.append(String.format("<tspan dx=\"-%sem\" dy=\"%sem\">", startCount / 2, 1.2 * count));
		builder.append(EscapeUtils.escapeXml(arg));
		builder.append("</tspan>");
		count++;
	}

	public int getMaxLength() {
		return maxLength;
	}

	public int getCount() {
		return count;
	}

	public String getText() {
		return text;
	}

}
