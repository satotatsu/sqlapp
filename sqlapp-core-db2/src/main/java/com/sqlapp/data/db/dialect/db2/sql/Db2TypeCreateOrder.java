/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.schemas.*;

/** Stable dependency ordering for the canonical Db2 Type and Domain models. */
final class Db2TypeCreateOrder {
	private Db2TypeCreateOrder() {
	}

	/**
	 * Orders whole schemas when their modeled type dependencies admit that order.
	 */
	static List<Schema> schemas(List<Schema> schemas) {
		Map<String, Schema> pending = new LinkedHashMap<>();
		Map<String, Set<String>> prerequisites = new LinkedHashMap<>();
		List<AbstractSchemaObject<?>> allTypes = new ArrayList<>();
		for (var schema : schemas) {
			com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.identifier(schema.getName());
			if (pending.putIfAbsent(schema.getName(), schema) != null)
				throw new IllegalArgumentException("Duplicate Db2 schema identity: " + schema.getName());
			Set<String> required = new LinkedHashSet<>();
			List<AbstractSchemaObject<?>> types = new ArrayList<>();
			types.addAll(schema.getTypes());
			types.addAll(schema.getDomains());
			allTypes.addAll(types);
			for (var type : types)
				for (var dependency : dependencies(type))
					required.add(dependency.getFirst());
			for (var type : schema.getTypes()) {
				if (type.getDefinition().isEmpty() && type.getMetaType() == Type.MetaType.Cursor
						&& "S".equals(type.getSpecifics().get("DB2_CURSOR_KIND"))) {
					String target = type.getSpecifics().get("DB2_CURSOR_ROW_SCHEMA");
					if (target != null)
						required.add(target);
				}
			}
			required.remove(schema.getName());
			prerequisites.put(schema.getName(), required);
		}
		sort(allTypes); // Distinguish impossible type cycles from schema interleaving requirements.
		List<Schema> result = new ArrayList<>();
		while (!pending.isEmpty()) {
			boolean progressed = false;
			for (var iterator = pending.entrySet().iterator(); iterator.hasNext();) {
				var entry = iterator.next();
				if (prerequisites.get(entry.getKey()).stream().anyMatch(pending::containsKey))
					continue;
				result.add(entry.getValue());
				iterator.remove();
				progressed = true;
				break;
			}
			if (!progressed)
				throw new IllegalArgumentException(
						"Db2 type dependencies cross schemas in a cycle; use an explicit interleaved creation plan: "
								+ pending.keySet());
		}
		return result;
	}

	static List<AbstractSchemaObject<?>> sort(List<? extends AbstractSchemaObject<?>> objects) {
		Map<List<String>, AbstractSchemaObject<?>> pending = new LinkedHashMap<>();
		for (var object : objects) {
			com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(object.getSchemaName(), object.getName());
			var identity = List.of(object.getSchemaName(), object.getName());
			if (pending.putIfAbsent(identity, object) != null)
				throw new IllegalArgumentException("Duplicate Db2 type identity: " + identity);
		}
		List<AbstractSchemaObject<?>> result = new ArrayList<>();
		while (!pending.isEmpty()) {
			boolean progressed = false;
			for (var iterator = pending.entrySet().iterator(); iterator.hasNext();) {
				var entry = iterator.next();
				if (dependencies(entry.getValue()).stream().anyMatch(pending::containsKey))
					continue;
				result.add(entry.getValue());
				iterator.remove();
				progressed = true;
				break;
			}
			if (!progressed)
				throw new IllegalArgumentException(
						"Cyclic Db2 type dependencies require a complete ordered definition: " + pending.keySet());
		}
		return result;
	}

	private static List<List<String>> dependencies(AbstractSchemaObject<?> object) {
		List<List<String>> result = new ArrayList<>();
		if (object instanceof Type type) {
			if (!type.getDefinition().isEmpty())
				return result;
			if (type.getMetaType() == Type.MetaType.Row)
				for (var field : type.getColumns()) {
					var fields = field.getSpecifics();
					add(result, fields.get("DB2_TYPE_SCHEMA"), fields.get("DB2_TYPE_MODULE"), fields.get("TYPENAME"));
				}
			if (type.getMetaType() == Type.MetaType.Cursor && "S".equals(type.getSpecifics().get("DB2_CURSOR_KIND"))) {
				var fields = type.getSpecifics();
				add(result, fields.get("DB2_CURSOR_ROW_SCHEMA"), fields.get("DB2_CURSOR_ROW_MODULE"),
						fields.get("DB2_CURSOR_ROW_NAME"));
			}
		} else if (object instanceof Domain type && type.getDefinition().isEmpty()) {
			var fields = type.getSpecifics();
			add(result, fields.get("SOURCESCHEMA"), fields.get("SOURCEMODULENAME"), fields.get("SOURCENAME"));
		}
		return result;
	}

	private static void add(List<List<String>> result, String schema, String module, String name) {
		if (schema != null && name != null && module == null && !"SYSIBM".equals(schema))
			result.add(List.of(schema, name));
	}
}
