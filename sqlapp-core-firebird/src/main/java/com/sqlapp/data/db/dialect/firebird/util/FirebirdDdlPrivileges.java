/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.util;

import java.util.Locale;
import java.util.Set;

/** DDL object-class codes differ from individual-object privilege codes. */
public final class FirebirdDdlPrivileges {
	private static final Set<String> TYPES = Set.of("TABLE", "VIEW", "PROCEDURE", "FUNCTION", "PACKAGE", "SEQUENCE",
			"DOMAIN", "EXCEPTION", "ROLE", "CHARACTER SET", "COLLATION", "FILTER");

	private FirebirdDdlPrivileges() {
	}

	public static String fromCatalog(String code, int nativeType, com.sqlapp.data.db.dialect.Dialect dialect) {
		// Firebird 4 inserted obj_privilege at 20, shifting all DDL object classes by
		// one.
		int type = nativeType
				+ (dialect.compareTo(com.sqlapp.data.db.dialect.firebird.DialectHolder.defaultDialect40) < 0 ? 1 : 0);
		String target = switch (type) {
		case 21 -> "DATABASE";
		case 22 -> "TABLE";
		case 23 -> "VIEW";
		case 24 -> "PROCEDURE";
		case 25 -> "FUNCTION";
		case 26 -> "PACKAGE";
		case 27 -> "SEQUENCE";
		case 28 -> "DOMAIN";
		case 29 -> "EXCEPTION";
		case 30 -> "ROLE";
		case 31 -> "CHARACTER SET";
		case 32 -> "COLLATION";
		case 33 -> "FILTER";
		default -> throw new IllegalArgumentException("Unsupported Firebird DDL privilege object type: " + type);
		};
		String action = switch (code) {
		case "C" -> "CREATE";
		case "L" -> type == 21 ? "ALTER" : "ALTER ANY";
		case "O" -> type == 21 ? "DROP" : "DROP ANY";
		default -> throw new IllegalArgumentException("Unsupported Firebird DDL privilege code: " + code);
		};
		return action + " " + target;
	}

	public static String normalize(String value) {
		if (value == null)
			throw new IllegalArgumentException("Firebird DDL privilege is required");
		String result = value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
		for (String action : new String[] { "CREATE", "ALTER ANY", "DROP ANY" })
			for (String type : TYPES)
				if (result.equals(action + " " + type))
					return result;
		if (Set.of("CREATE DATABASE", "ALTER DATABASE", "DROP DATABASE").contains(result))
			return result;
		throw new IllegalArgumentException("Unsupported Firebird DDL privilege: " + value);
	}
}
