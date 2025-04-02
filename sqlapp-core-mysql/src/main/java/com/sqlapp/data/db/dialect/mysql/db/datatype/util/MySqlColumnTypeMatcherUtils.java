package com.sqlapp.data.db.dialect.mysql.db.datatype.util;

public class MySqlColumnTypeMatcherUtils {

	public static String joinForNumber(String... args) {
		final StringBuilder builder = new StringBuilder();
		builder.append("((?<dataTypeName>");
		for (int i = 0; i < args.length; i++) {
			if (i > 0) {
				builder.append("|");
			}
			builder.append(args[i]);
		}
		builder.append(")(\\(\\s*(?<width>[0-9]+)\\s*\\))?)");
		return builder.toString();
	}
}