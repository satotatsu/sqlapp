/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird;

import java.util.HashSet;
import java.util.List;

/**
 * Database-wide RDB$DEFAULT publication policy, independent of server transport
 * configuration.
 */
public record FirebirdPublication(boolean active, boolean autoEnable, List<String> tables) {
	public FirebirdPublication {
		tables = List.copyOf(tables);
		var names = new HashSet<String>();
		for (String name : tables) {
			if (name.isBlank() || name.indexOf('\0') >= 0 || !names.add(name))
				throw new IllegalArgumentException("Invalid or duplicate Firebird publication table: " + name);
		}
		tables = tables.stream().sorted().toList();
	}
}
