/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.dialect.db2.Db2Nickname;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.Table;

/**
 * Native nickname DDL keeps remote index specifications out of physical DDL.
 */
final class Db2NicknameSql {
	private Db2NicknameSql() {
	}

	static List<String> create(Table table, java.util.function.Supplier<Db2SqlBuilder> builders) {
		var metadata = Db2Nickname.get(table);
		if (!com.sqlapp.util.CommonUtils.isEmpty(table.getDefinition())) {
			if (table.getDefinition().stream().anyMatch(sql -> sql == null || sql.isBlank()))
				throw new IllegalArgumentException("Db2 nickname definition statements must not be blank");
			return List.copyOf(table.getDefinition());
		}
		String message = "Db2 nickname requires a complete executable CREATE definition for complex/nonrelational mappings: "
				+ table.getName();
		if (!"R".equals(metadata.wrapperType()) || metadata.serverName() == null || metadata.remoteTable() == null
				|| !metadata.options().isEmpty() || !table.getConstraints().isEmpty())
			throw new IllegalArgumentException(message);
		for (var entry : metadata.columnOptions().entrySet()) {
			if (!table.getColumns().contains(entry.getKey())
					|| entry.getValue().keySet().stream().anyMatch(key -> !"REMOTE_COLUMN_NAME".equals(key)))
				throw new IllegalArgumentException(message);
		}
		String target = Db2CreationContext.identifier(metadata.serverName()) + ".";
		if (metadata.remoteSchema() != null)
			target += Db2CreationContext.identifier(metadata.remoteSchema()) + ".";
		target += Db2CreationContext.identifier(metadata.remoteTable());
		String name = name(table);
		List<String> result = new ArrayList<>();
		result.add("CREATE NICKNAME " + name + " FOR " + target);
		var remoteNames = new java.util.LinkedHashMap<String, String>();
		var usedNames = new java.util.HashSet<String>();
		for (var column : table.getColumns()) {
			String remote = metadata.columnOptions().getOrDefault(column.getName(), Map.of())
					.getOrDefault("REMOTE_COLUMN_NAME", column.getName());
			// Db2 can generate local names for remote identifiers. Without contacting
			// the remote server, ordinary uppercase names have a certain initial spelling.
			if (!remote.matches("[A-Z_][A-Z0-9_@$#]*") || remote.length() > 30 || !usedNames.add(remote)
					|| column.getDataType() == null || column.getDataTypeName() != null)
				throw new IllegalArgumentException(message);
			remoteNames.put(column.getName(), remote);
		}
		usedNames.addAll(remoteNames.keySet());
		var temporaryNames = new java.util.LinkedHashMap<String, String>();
		int ordinal = 0;
		for (var entry : remoteNames.entrySet()) {
			if (entry.getKey().equals(entry.getValue()))
				continue;
			String temporary;
			do {
				temporary = "SQLAPP_NICKNAME_" + ordinal++;
			} while (!usedNames.add(temporary));
			temporaryNames.put(entry.getKey(), temporary);
			result.add("ALTER NICKNAME " + name + " ALTER COLUMN " + Db2CreationContext.identifier(entry.getValue())
					+ " LOCAL NAME " + Db2CreationContext.identifier(temporary));
		}
		for (var entry : temporaryNames.entrySet())
			result.add("ALTER NICKNAME " + name + " ALTER COLUMN " + Db2CreationContext.identifier(entry.getValue())
					+ " LOCAL NAME " + Db2CreationContext.identifier(entry.getKey()));
		for (var column : table.getColumns()) {
			var builder = builders.get();
			builder.typeDefinition(column.getDataType(), column.getDataTypeName(), column.getLength(),
					column.getScale());
			result.add("ALTER NICKNAME " + name + " ALTER COLUMN " + Db2CreationContext.identifier(column.getName())
					+ " LOCAL TYPE " + builder.toString());
		}
		if (!List.of("Y", "N").contains(metadata.cachingAllowed() == null ? "" : metadata.cachingAllowed()))
			throw new IllegalArgumentException(message);
		result.add("ALTER NICKNAME " + name
				+ ("Y".equals(metadata.cachingAllowed()) ? " ALLOW CACHING" : " DISALLOW CACHING"));
		return List.copyOf(result);
	}

	static String name(Table table) {
		return Db2CreationContext.qualified(table.getSchemaName(), table.getName());
	}
}
