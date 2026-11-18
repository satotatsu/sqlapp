/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recursive transport for native values combining UNION tags and VARIANT types.
 */
final class DuckDbTaggedJsonCodec {
	private record Field(String name, Node type) {
	}

	private record Node(String sql, String kind, List<Field> fields) {
	}

	private DuckDbTaggedJsonCodec() {
	}

	static String read(String expression, String type) {
		return encode(parse(type), expression, 0);
	}

	static String write(String expression, String type) {
		// The original expression (and therefore a JDBC parameter) occurs once.
		return "(SELECT " + decode(parse(type), "_sqlapp_tagged.v", 0) + " FROM (VALUES (CAST(" + expression
				+ " AS JSON))) AS _sqlapp_tagged(v))";
	}

	private static Node parse(String sql) {
		final String type = sql.strip();
		if (type.endsWith("]")) {
			final int open = type.lastIndexOf('[');
			if (open < 0)
				throw new IllegalArgumentException("Invalid DuckDB array type: " + type);
			return new Node(type, "LIST", List.of(new Field("", parse(type.substring(0, open)))));
		}
		final int open = type.indexOf('(');
		final String kind = (open < 0 ? type : type.substring(0, open)).strip().toUpperCase(Locale.ROOT);
		if (!List.of("STRUCT", "UNION", "MAP").contains(kind))
			return new Node(type, kind, List.of());
		if (!type.endsWith(")"))
			throw new IllegalArgumentException("Invalid DuckDB nested type: " + type);
		final var parts = DuckDbDdlParser.splitTopLevel(type.substring(open + 1, type.length() - 1));
		final List<Field> fields = new ArrayList<>();
		for (String part : parts) {
			if (kind.equals("MAP")) {
				fields.add(new Field("", parse(part)));
				continue;
			}
			int end = 0;
			String name;
			if (part.startsWith("\"")) {
				final StringBuilder decoded = new StringBuilder();
				end = 1;
				while (end < part.length()) {
					char c = part.charAt(end++);
					if (c == '"') {
						if (end < part.length() && part.charAt(end) == '"')
							end++;
						else
							break;
					}
					decoded.append(c);
				}
				name = decoded.toString();
			} else {
				while (end < part.length() && !Character.isWhitespace(part.charAt(end)))
					end++;
				name = part.substring(0, end);
			}
			if (end >= part.length())
				throw new IllegalArgumentException("Missing nested member type: " + part);
			fields.add(new Field(name, parse(part.substring(end))));
		}
		if (kind.equals("MAP") && fields.size() != 2)
			throw new IllegalArgumentException("Invalid DuckDB MAP type: " + type);
		return new Node(type, kind, List.copyOf(fields));
	}

	private static String encode(Node node, String value, int depth) {
		final String variable = "_sqlapp_e" + depth;
		final var fields = node.fields();
		final String body;
		switch (node.kind()) {
		case "VARIANT":
			final String bytes = "CAST(variant_to_parquet_variant(" + value + ") AS STRUCT(metadata BLOB, value BLOB))";
			return "to_json(to_hex((" + bytes + ").metadata || (" + bytes + ").value))";
		case "LIST":
			return "to_json(list_transform(" + value + ", lambda " + variable + ": "
					+ encode(fields.getFirst().type(), variable, depth + 1) + "))";
		case "MAP":
			return "to_json(list_transform(map_entries(" + value + "), lambda " + variable + ": json_object('key', "
					+ encode(fields.get(0).type(), variable + ".key", depth + 1) + ", 'value', "
					+ encode(fields.get(1).type(), variable + ".value", depth + 1) + ")))";
		case "STRUCT":
			final List<String> members = new ArrayList<>();
			for (var field : fields)
				members.add(literal(field.name()) + ", "
						+ encode(field.type(), "(" + value + ")." + identifier(field.name()), depth + 1));
			body = "json_object(" + String.join(", ", members) + ")";
			break;
		case "UNION":
			final StringBuilder choice = new StringBuilder("CASE CAST(union_tag(").append(value)
					.append(") AS VARCHAR)");
			for (var field : fields)
				choice.append(" WHEN ").append(literal(field.name())).append(" THEN ").append(
						encode(field.type(), "union_extract(" + value + ", " + literal(field.name()) + ")", depth + 1));
			choice.append(" END");
			body = "json_object('tag', CAST(union_tag(" + value + ") AS VARCHAR), 'value', " + choice + ")";
			break;
		default:
			return "to_json(CAST(" + value + " AS VARCHAR))";
		}
		return "CASE WHEN " + value + " IS NULL THEN NULL ELSE " + body + " END";
	}

	private static String decode(Node node, String json, int depth) {
		final String variable = "_sqlapp_d" + depth;
		final var fields = node.fields();
		final String body;
		switch (node.kind()) {
		case "VARIANT":
			return "variant_bytes_to_variant(from_hex(json_extract_string(" + json + ", '$')))";
		case "LIST":
			body = "list_transform(CAST(" + json + " AS JSON[]), lambda " + variable + ": "
					+ decode(fields.getFirst().type(), variable, depth + 1) + ")";
			break;
		case "MAP":
			body = "map_from_entries(list_transform(CAST(" + json + " AS JSON[]), lambda " + variable
					+ ": struct_pack(key := " + decode(fields.get(0).type(), extract(variable, "key"), depth + 1)
					+ ", value := " + decode(fields.get(1).type(), extract(variable, "value"), depth + 1) + ")))";
			break;
		case "STRUCT":
			final List<String> members = new ArrayList<>();
			for (var field : fields)
				members.add(identifier(field.name()) + " := "
						+ decode(field.type(), extract(json, field.name()), depth + 1));
			body = "struct_pack(" + String.join(", ", members) + ")";
			break;
		case "UNION":
			final StringBuilder choice = new StringBuilder("CASE json_extract_string(").append(json)
					.append(", '/tag')");
			for (var field : fields)
				choice.append(" WHEN ").append(literal(field.name())).append(" THEN CAST(union_value(")
						.append(identifier(field.name())).append(" := ")
						.append(decode(field.type(), extract(json, "value"), depth + 1)).append(") AS ")
						.append(node.sql()).append(")");
			body = choice.append(" ELSE error('Invalid SQLapp DuckDB UNION tag') END").toString();
			break;
		default:
			return "CAST(json_extract_string(" + json + ", '$') AS " + node.sql() + ")";
		}
		return "CASE WHEN " + json + " IS NULL OR json_type(" + json + ") = 'NULL' THEN NULL::" + node.sql()
				+ " ELSE CAST(" + body + " AS " + node.sql() + ") END";
	}

	private static String extract(String json, String name) {
		return "json_extract(" + json + ", " + literal("/" + name.replace("~", "~0").replace("/", "~1")) + ")";
	}

	private static String literal(String text) {
		return "'" + text.replace("'", "''") + "'";
	}

	private static String identifier(String text) {
		return "\"" + text.replace("\"", "\"\"") + "\"";
	}
}
