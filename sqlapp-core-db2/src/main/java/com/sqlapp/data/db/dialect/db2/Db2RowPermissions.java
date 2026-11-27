/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.util.JsonConverter;

/** Typed Db2 LUW 10.1+ row permissions and table access-control activation. */
public final class Db2RowPermissions {
	public static final String SPECIFICS_KEY = "db2.rowPermissions.v1";

	private Db2RowPermissions() {
	}

	public record Permission(String schemaName, String name, String tableSchemaName, String tableName, String condition,
			String correlationName, Boolean enabled, Boolean valid, String qualifier, String functionPath,
			String remarks) {
		public Permission {
			Db2CreationContext.qualified(schemaName, name);
			Db2CreationContext.qualified(tableSchemaName, tableName);
			if (condition == null || condition.isBlank())
				throw new IllegalArgumentException("Db2 permission requires a condition");
			if (enabled == null || valid == null)
				throw new IllegalArgumentException("Db2 permission requires enabled and valid states");
			if (correlationName != null && !correlationName.isBlank())
				Db2CreationContext.identifier(correlationName);
			Db2CreationContext.sql(qualifier, functionPath);
		}
	}

	public record AccessControl(String tableSchemaName, String tableName, Boolean rowActive, Boolean columnActive) {
		public AccessControl {
			Db2CreationContext.qualified(tableSchemaName, tableName);
			if (rowActive == null || columnActive == null)
				throw new IllegalArgumentException("Db2 table requires explicit row/column activation states");
		}
	}

	public record Snapshot(int format, List<Permission> permissions, List<AccessControl> tables) {
		public Snapshot {
			if (format != 1 || permissions == null || tables == null)
				throw new IllegalArgumentException("Invalid Db2 row-permission snapshot");
			permissions = List.copyOf(permissions);
			tables = List.copyOf(tables);
			var ids = new HashSet<List<String>>();
			for (Permission permission : permissions) {
				if (!ids.add(List.of(permission.schemaName(), permission.name())))
					throw new IllegalArgumentException("Duplicate Db2 permission: " + permission.name());
			}
			ids.clear();
			for (AccessControl table : tables) {
				if (!ids.add(List.of(table.tableSchemaName(), table.tableName())))
					throw new IllegalArgumentException("Duplicate Db2 access-control table: " + table.tableName());
			}
		}
	}

	public static Snapshot get(Schema schema) {
		String text = schema.getSpecifics().get(SPECIFICS_KEY);
		if (text == null)
			return new Snapshot(1, List.of(), List.of());
		Snapshot snapshot = new JsonConverter().getObjectMapper().readerFor(Snapshot.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(text);
		if (snapshot == null)
			throw new IllegalArgumentException("Invalid Db2 row-permission snapshot");
		validateScope(schema, snapshot);
		return snapshot;
	}

	public static void set(Schema schema, List<Permission> permissions, List<AccessControl> tables) {
		Snapshot snapshot = new Snapshot(1, permissions, tables);
		validateScope(schema, snapshot);
		if (permissions.isEmpty() && tables.isEmpty()) {
			var retained = new com.sqlapp.data.schemas.DbInfo();
			schema.getSpecifics().forEach((key, value) -> {
				if (!SPECIFICS_KEY.equalsIgnoreCase(key))
					retained.put(key, value);
			});
			schema.setSpecifics(retained);
		} else
			schema.getSpecifics().put(SPECIFICS_KEY, new JsonConverter().toJsonString(snapshot));
	}

	private static void validateScope(Schema schema, Snapshot snapshot) {
		for (Permission permission : snapshot.permissions()) {
			if (!Objects.equals(schema.getName(), permission.schemaName()))
				throw new IllegalArgumentException(
						"Db2 permission belongs to a different schema: " + permission.name());
		}
		for (AccessControl table : snapshot.tables()) {
			if (!Objects.equals(schema.getName(), table.tableSchemaName()))
				throw new IllegalArgumentException(
						"Db2 access-control table belongs to a different schema: " + table.tableName());
		}
	}

	/**
	 * Includes explicit SET CURRENT SCHEMA/PATH operations when captured by
	 * metadata.
	 */
	public static List<String> createSql(Schema schema) {
		List<String> result = new ArrayList<>(permissionSql(schema));
		result.addAll(activationSql(schema));
		return List.copyOf(result);
	}

	public static List<String> permissionSql(Schema schema) {
		List<String> result = new ArrayList<>();
		for (Permission permission : get(schema).permissions()) {
			if (!permission.valid())
				throw new IllegalArgumentException("Cannot recreate invalid Db2 permission: " + permission.name());
			result.addAll(Db2CreationContext.sql(permission.qualifier(), permission.functionPath()));
			String ddl = "CREATE PERMISSION " + Db2CreationContext.qualified(permission.schemaName(), permission.name())
					+ " ON " + Db2CreationContext.qualified(permission.tableSchemaName(), permission.tableName());
			if (permission.correlationName() != null && !permission.correlationName().isBlank())
				ddl += " AS " + Db2CreationContext.identifier(permission.correlationName());
			ddl += " FOR ROWS WHERE " + permission.condition() + " ENFORCED FOR ALL ACCESS "
					+ (permission.enabled() ? "ENABLE" : "DISABLE");
			result.add(ddl);
			if (permission.remarks() != null)
				result.add("COMMENT ON PERMISSION "
						+ Db2CreationContext.qualified(permission.schemaName(), permission.name()) + " IS '"
						+ permission.remarks().replace("'", "''") + "'");
		}
		return List.copyOf(result);
	}

	public static List<String> activationSql(Schema schema) {
		List<String> result = new ArrayList<>();
		for (AccessControl table : get(schema).tables()) {
			String prefix = "ALTER TABLE " + Db2CreationContext.qualified(table.tableSchemaName(), table.tableName())
					+ " ACTIVATE ";
			if (table.rowActive())
				result.add(prefix + "ROW ACCESS CONTROL");
			if (table.columnActive())
				result.add(prefix + "COLUMN ACCESS CONTROL");
		}
		return List.copyOf(result);
	}

	public record ChangePlan(List<String> beforeSql, List<String> afterSql) {
		public ChangePlan {
			beforeSql = List.copyOf(beforeSql);
			afterSql = List.copyOf(afterSql);
		}
	}

	/**
	 * Removed/replaced permissions precede native DDL; explicit activation changes
	 * follow it.
	 */
	public static ChangePlan diff(Schema original, Schema target) {
		Snapshot before = get(original), after = get(target);
		var oldPermissions = new java.util.LinkedHashMap<List<String>, Permission>();
		var newPermissions = new java.util.LinkedHashMap<List<String>, Permission>();
		before.permissions().forEach(p -> oldPermissions.put(List.of(p.schemaName(), p.name()), p));
		after.permissions().forEach(p -> newPermissions.put(List.of(p.schemaName(), p.name()), p));
		List<String> drops = new ArrayList<>(), creates = new ArrayList<>();
		oldPermissions.forEach((id, permission) -> {
			if (!Objects.equals(permission, newPermissions.get(id))) {
				drops.add(
						"DROP PERMISSION " + Db2CreationContext.qualified(permission.schemaName(), permission.name()));
			}
		});
		newPermissions.forEach((id, permission) -> {
			if (!Objects.equals(permission, oldPermissions.get(id))) {
				Schema owner = new Schema(permission.schemaName());
				set(owner, List.of(permission), List.of());
				creates.addAll(permissionSql(owner));
			}
		});
		var oldTables = new java.util.LinkedHashMap<List<String>, AccessControl>();
		var newTables = new java.util.LinkedHashMap<List<String>, AccessControl>();
		before.tables().forEach(t -> oldTables.put(List.of(t.tableSchemaName(), t.tableName()), t));
		after.tables().forEach(t -> newTables.put(List.of(t.tableSchemaName(), t.tableName()), t));
		oldTables.forEach((id, table) -> {
			if ((table.rowActive() || table.columnActive()) && !newTables.containsKey(id)) {
				boolean dropped = original.getTables().contains(table.tableName())
						&& !target.getTables().contains(table.tableName());
				if (!dropped)
					throw new IllegalArgumentException(
							"Db2 access-control removal requires explicit target activation states: "
									+ table.tableName());
			}
		});
		newTables.forEach((id, table) -> {
			AccessControl previous = oldTables.get(id);
			String prefix = "ALTER TABLE " + Db2CreationContext.qualified(table.tableSchemaName(), table.tableName())
					+ " ";
			if (table.rowActive() != (previous != null && previous.rowActive())) {
				creates.add(prefix + (table.rowActive() ? "ACTIVATE" : "DEACTIVATE") + " ROW ACCESS CONTROL");
			}
			if (table.columnActive() != (previous != null && previous.columnActive())) {
				creates.add(prefix + (table.columnActive() ? "ACTIVATE" : "DEACTIVATE") + " COLUMN ACCESS CONTROL");
			}
		});
		return new ChangePlan(drops, creates);
	}
}
