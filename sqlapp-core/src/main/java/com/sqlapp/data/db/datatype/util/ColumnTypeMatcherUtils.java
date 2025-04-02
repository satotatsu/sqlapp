package com.sqlapp.data.db.datatype.util;

public class ColumnTypeMatcherUtils {

	public static String join(String... args) {
		final StringBuilder builder = new StringBuilder();
		builder.append("(?<dataTypeName>");
		for (int i = 0; i < args.length; i++) {
			if (i > 0) {
				builder.append("|");
			}
			builder.append(args[i]);
		}
		builder.append(")");
		return builder.toString();
	}
}
