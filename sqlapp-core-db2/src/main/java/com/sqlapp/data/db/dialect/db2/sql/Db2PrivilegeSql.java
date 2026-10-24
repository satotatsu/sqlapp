/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;

final class Db2PrivilegeSql {
	private Db2PrivilegeSql() {
	}

	static void target(Db2SqlBuilder builder, String schema, String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Db2 privilege requires an object name");
		}
		if (schema != null)
			builder.name(schema)._add(".");
		builder.name(name);
	}

	static void grantee(Db2SqlBuilder builder, String name, String type) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Db2 privilege requires a grantee");
		}
		if ("PUBLIC".equals(name)) {
			builder._add("PUBLIC");
			return;
		}
		if (type == null) {
			throw new IllegalArgumentException("Db2 privilege requires GRANTEE_TYPE U (user), G (group) or R (role)");
		}
		builder._add(switch (type) {
		case "U" -> "USER";
		case "G" -> "GROUP";
		case "R" -> "ROLE";
		default -> throw new IllegalArgumentException("Unsupported Db2 grantee type: " + type);
		}).space().name(name);
	}
}
