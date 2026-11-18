/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb.util;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.HexFormat;
import java.util.regex.Pattern;

import com.sqlapp.data.schemas.Column;

/**
 * Portable native values whose ordinary VARCHAR casts discard type information.
 */
public final class DuckDbNativeCodec {
	private static final String PREFIX = "sqlapp-duckdb-variant-v1:";
	private static final Pattern UNION = Pattern.compile("(?i)\\bUNION\\s*\\(");
	private static final Pattern VARIANT = Pattern.compile("(?i)\\bVARIANT\\b");

	private DuckDbNativeCodec() {
	}

	private static boolean variant(Column column) {
		return VARIANT.matcher(column.getDataTypeName() == null ? "" : column.getDataTypeName()).find();
	}

	private static boolean union(Column column) {
		return UNION.matcher(column.getDataTypeName() == null ? "" : column.getDataTypeName()).find();
	}

	public static String readExpression(String expression, Column column) {
		if (variant(column)) {
			if (union(column))
				return DuckDbTaggedJsonCodec.read(expression, column.getDataTypeName());
			return "variant_to_parquet_variant(CAST(" + expression + " AS VARIANT))";
		}
		return union(column) ? "to_json(" + expression + ")" : expression;
	}

	public static String writeExpression(String expression, Column column) {
		if (variant(column)) {
			if (union(column))
				return DuckDbTaggedJsonCodec.write(expression, column.getDataTypeName());
			// A scalar subquery evaluates/binds the supplied expression exactly once.
			return "(SELECT CAST(variant_bytes_to_variant(from_hex(CASE WHEN v IS NULL THEN NULL WHEN "
					+ "regexp_full_match(v, '" + PREFIX + "([0-9a-fA-F]{2})+') THEN substring(v, "
					+ (PREFIX.length() + 1) + ") ELSE error('Expected SQLapp DuckDB VARIANT encoding') END)) AS "
					+ column.getDataTypeName() + ") FROM (VALUES (" + expression + ")) AS _sqlapp_variant(v))";
		}
		if (!union(column))
			return expression;
		final String jsonType = "\"" + column.getDataTypeName().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		return "from_json(" + expression + ", '" + jsonType.replace("'", "''") + "')";
	}

	public static Object read(ResultSet rs, int index, Column column) throws SQLException {
		if (variant(column) && union(column))
			return rs.getString(index);
		if (!variant(column)) {
			if (union(column) && UNION.matcher(rs.getMetaData().getColumnTypeName(index)).find())
				throw new SQLException("UNION requires the SQLapp JDBC read projection to retain its active tag");
			return rs.getString(index);
		}
		final Object value = rs.getObject(index);
		if (value == null)
			return null;
		if (!(value instanceof Struct struct))
			throw new SQLException("VARIANT requires the SQLapp JDBC read projection");
		final Object[] attributes = struct.getAttributes();
		if (attributes.length != 2 || !(attributes[0] instanceof Blob metadata)
				|| !(attributes[1] instanceof Blob data))
			throw new SQLException("Unexpected DuckDB PARQUET_VARIANT representation");
		final var hex = HexFormat.of();
		return PREFIX + hex.formatHex(metadata.getBytes(1, Math.toIntExact(metadata.length())))
				+ hex.formatHex(data.getBytes(1, Math.toIntExact(data.length())));
	}
}
