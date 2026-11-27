/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.util;

import java.util.Locale;
import java.util.Set;
import com.sqlapp.data.db.dialect.mysql.MySql800;
import com.sqlapp.data.schemas.Column;

/**
 * MySQL column SRID restriction; this does not create a spatial reference
 * system.
 */
public final class MySqlSpatialReference {
	public static final String SRID = "mysql_srid";
	private static final Set<String> TYPES = Set.of("GEOMETRY", "POINT", "LINESTRING", "POLYGON", "MULTIPOINT",
			"MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION", "GEOMCOLLECTION");

	private MySqlSpatialReference() {
	}

	public static boolean isSpatialType(String type) {
		return type != null && TYPES.contains(type.trim().toUpperCase(Locale.ROOT));
	}

	/** Use the spelling accepted by older MySQL and MariaDB releases as well. */
	public static String sqlTypeName(String type) {
		String name = type.trim().toUpperCase(Locale.ROOT);
		return "GEOMCOLLECTION".equals(name) ? "GEOMETRYCOLLECTION" : name;
	}

	public static String normalize(String value) {
		if (value == null || !value.matches("[0-9]{1,10}") || Long.parseLong(value) > 4294967295L)
			throw new IllegalArgumentException("MySQL column SRID must be an unsigned 32-bit integer (0..4294967295)");
		return Long.toString(Long.parseLong(value));
	}

	public static void append(MySqlSqlBuilder builder, Column column) {
		String value = column.getSpecifics().get(SRID);
		if (value == null)
			return;
		if (!(builder.getDialect() instanceof MySql800)
				|| !"MySQL".equalsIgnoreCase(builder.getDialect().getProductName()))
			throw new IllegalArgumentException(
					"MySQL column SRID requires MySQL 8.0.3 or later; this dialect does not support it");
		String type = column.getDataTypeName();
		if (type == null && column.getDataType() != null)
			type = column.getDataType().getTypeName();
		if (!isSpatialType(type))
			throw new IllegalArgumentException("MySQL column SRID requires a spatial data type: " + column.getName());
		builder.space()._add("SRID ")._add(normalize(value));
	}
}
