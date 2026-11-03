package com.sqlapp.data.db.dialect.virtica.metadata;

import java.util.*;
import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;

/**
 * Internal preflight for a fresh schema, keeping scheduling DDL behind the
 * executor.
 */
final class VirticaSchemaRestorePlan {
	record Plan(List<String> statements, int generatedCheckpoint, Set<String> flexTables) {
	}

	private record Name(int start, int end, List<String> parts) {
	}

	static List<String> statements(Schema schema) {
		return create(schema).statements();
	}

	static Plan create(Schema schema) {
		if (schema.getDefinition() == null || schema.getDefinition().isEmpty())
			throw new IllegalArgumentException("Schema restore requires an exported complete definition");
		if (!schema.getSpecifics().containsKey("VERTICA_SCHEDULING_STATES"))
			throw new IllegalArgumentException(
					"Schema restore requires an exported VERTICA_SCHEDULING_STATES snapshot");
		var desired = VirticaSchedulingStateRestorer.parse(schema.getSpecifics().get("VERTICA_SCHEDULING_STATES"),
				schema.getName());
		var dialect = DialectHolder.defaultDialect12_0_4;
		var input = dialect.createSqlSplitter().parse(String.join("\n", schema.getDefinition())).stream()
				.filter(s -> !s.getTextType().isComment() && !s.getText().isBlank()).map(s -> s.getText().strip())
				.toList();
		if (input.isEmpty())
			throw new IllegalArgumentException("Schema restore definition is empty");
		var first = new VirticaProjectionDdlMapper.Cursor(input.get(0));
		first.expect("CREATE");
		first.expect("SCHEMA");
		if (!first.identifier().equalsIgnoreCase(schema.getName()) || !first.atEnd())
			throw new IllegalArgumentException(
					"Restore requires an initial unconditional CREATE SCHEMA matching the target");
		var ordinary = new ArrayList<String>();
		ordinary.add(input.get(0));
		var schedules = new LinkedHashMap<String, String>();
		var triggers = new LinkedHashMap<String, String>();
		var procedures = new HashSet<String>();
		int lastFlex = -1;
		Set<String> flexTables = new LinkedHashSet<>();
		for (String sql : input.subList(1, input.size())) {
			var cursor = new VirticaProjectionDdlMapper.Cursor(sql);
			if (cursor.keyword("ALTER")) {
				cursor.expect("TABLE");
				own(read(cursor), schema);
				cursor.expect("ADD");
				cursor.expect("CONSTRAINT");
				ordinary.add(sql);
				continue;
			}
			cursor.expect("CREATE");
			if (cursor.keyword("OR")) {
				cursor.expect("REPLACE");
			}
			String kind;
			if (cursor.keyword("SCHEMA"))
				throw new IllegalArgumentException("Restore cannot create additional schemas");
			if (cursor.keyword("FLEX") || cursor.keyword("FLEXIBLE")) {
				cursor.expect("TABLE");
				kind = "FLEX_TABLE";
				if (lastFlex >= 0 && ordinary.size() != lastFlex + 1)
					throw new IllegalArgumentException(
							"Flex CREATEs must form a contiguous batch before generated child DDL");
				lastFlex = ordinary.size();
			} else if (cursor.keyword("SCHEDULE"))
				kind = "SCHEDULE";
			else if (cursor.keyword("TRIGGER"))
				kind = "TRIGGER";
			else if (cursor.keyword("TEXT")) {
				cursor.expect("INDEX");
				kind = "INDEX";
			} else if (cursor.keyword("EXTERNAL")) {
				cursor.expect("TABLE");
				kind = "TABLE";
			} else {
				kind = null;
				for (String candidate : List.of("TABLE", "VIEW", "FUNCTION", "PROCEDURE", "SEQUENCE", "PROJECTION"))
					if (cursor.keyword(candidate)) {
						kind = candidate;
						break;
					}
				if (kind == null)
					throw new IllegalArgumentException("Unsupported statement in fresh-schema restore: "
							+ sql.substring(0, Math.min(40, sql.length())));
			}
			Name object = read(cursor);
			own(object, schema);
			String name = object.parts().get(object.parts().size() - 1).toLowerCase(Locale.ROOT);
			if (kind.equals("FLEX_TABLE"))
				VirticaFlexRawColumns.requireSourceMetadata(schema, name);
			if (kind.equals("FLEX_TABLE") && !flexTables.add(name))
				throw new IllegalArgumentException("Duplicate Flex table CREATE");
			if (kind.equals("SCHEDULE")) {
				if (schedules.putIfAbsent(name, sql) != null)
					throw new IllegalArgumentException("Duplicate schedule CREATE");
			} else if (kind.equals("TRIGGER")) {
				if (triggers.putIfAbsent(name,
						qualifyTrigger(sql, cursor, schema, object, schedules.keySet(), procedures)) != null)
					throw new IllegalArgumentException("Duplicate trigger CREATE");
			} else {
				ordinary.add(sql);
				if (kind.equals("PROCEDURE"))
					procedures.add(name);
			}
		}
		var expected = new HashSet<String>();
		schedules.keySet().forEach(n -> expected.add("SCHEDULE:" + n));
		triggers.keySet().forEach(n -> expected.add("TRIGGER:" + n));
		var actual = new HashSet<String>();
		desired.values().forEach(c -> actual.add(c.kind() + ":" + c.name().toLowerCase(Locale.ROOT)));
		if (!expected.equals(actual))
			throw new IllegalArgumentException("Scheduling CREATEs do not exactly match the exported state snapshot");
		// Compose the normal Schema SQL factory for non-scheduling DDL. Its existing
		// definition validation and statement splitting remain the common path.
		var result = new ArrayList<String>();
		int checkpoint = lastFlex < 0 ? 0 : lastFlex + 1;
		if (checkpoint > 0 && !schema.getName().matches("[A-Za-z_][A-Za-z0-9_$]*"))
			throw new IllegalArgumentException(
					"Flex generated-object verification requires a simple schema export scope");
		if (checkpoint > 0) {
			result.addAll(factoryStatements(schema, ordinary.subList(0, checkpoint)));
			checkpoint = result.size();
			result.addAll(factoryStatements(schema, ordinary.subList(lastFlex + 1, ordinary.size())));
		} else
			result.addAll(factoryStatements(schema, ordinary));
		result.addAll(schedules.values());
		result.addAll(triggers.values());
		return new Plan(List.copyOf(result), checkpoint, Set.copyOf(flexTables));
	}

	private static List<String> factoryStatements(Schema schema, List<String> statements) {
		if (statements.isEmpty())
			return List.of();
		Schema ddl = new Schema(schema.getName());
		ddl.setDefinition(String.join(";\n", statements) + ";");
		return DialectHolder.defaultDialect12_0_4.createSqlFactoryRegistry().createSql(ddl, SqlType.CREATE).stream()
				.map(o -> o.getSqlText()).toList();
	}

	private static String qualifyTrigger(String sql, VirticaProjectionDdlMapper.Cursor cursor, Schema schema,
			Name trigger, Set<String> schedules, Set<String> procedures) {
		cursor.expect("ON");
		cursor.expect("SCHEDULE");
		Name schedule = read(cursor);
		cursor.expect("EXECUTE");
		cursor.expect("PROCEDURE");
		Name procedure = read(cursor);
		if (!schedules.contains(schedule.parts().get(schedule.parts().size() - 1).toLowerCase(Locale.ROOT)))
			throw new IllegalArgumentException("Trigger requires a preceding schedule CREATE in the same definition");
		String procedureSchema = null;
		if (procedure.parts().size() == 1) {
			String triggerName = trigger.parts().get(trigger.parts().size() - 1);
			var event = schema.getEvents().stream().filter(e -> e.getName().equalsIgnoreCase(triggerName)).findFirst()
					.orElseThrow(() -> new IllegalArgumentException(
							"Unqualified procedure reference requires Event.PROCEDURE_SCHEMA_NAME for trigger "
									+ triggerName));
			procedureSchema = event.getSpecifics().get("PROCEDURE_SCHEMA_NAME", String.class);
			if (procedureSchema == null || procedureSchema.isBlank())
				throw new IllegalArgumentException("Set Event specifics PROCEDURE_SCHEMA_NAME for trigger "
						+ triggerName + "; procedure schema is not inferred");
		}
		if (schedule.parts().size() > 1)
			own(schedule, schema);
		String resolvedSchema = procedureSchema == null ? procedure.parts().get(procedure.parts().size() - 2)
				: procedureSchema;
		if (resolvedSchema.equalsIgnoreCase(schema.getName())
				&& !procedures.contains(procedure.parts().get(procedure.parts().size() - 1).toLowerCase(Locale.ROOT)))
			throw new IllegalArgumentException("Trigger requires a preceding procedure CREATE in the new schema");
		if (procedureSchema != null)
			sql = replace(sql, procedure, procedureSchema);
		if (schedule.parts().size() == 1)
			sql = replace(sql, schedule, schema.getName());
		return sql;
	}

	private static String replace(String sql, Name name, String schema) {
		return sql.substring(0, name.start()) + quote(schema) + "." + quote(name.parts().get(0)) + " "
				+ sql.substring(name.end());
	}

	private static void own(Name name, Schema schema) {
		if (name.parts().size() < 2 || !name.parts().get(name.parts().size() - 2).equalsIgnoreCase(schema.getName()))
			throw new IllegalArgumentException("Restore DDL objects must be qualified within the new schema");
		if (name.parts().size() == 3
				&& (schema.getCatalogName() == null || !name.parts().get(0).equalsIgnoreCase(schema.getCatalogName())))
			throw new IllegalArgumentException("Restore DDL catalog does not match the Schema model");
	}

	private static Name read(VirticaProjectionDdlMapper.Cursor cursor) {
		int start = cursor.offset();
		var parts = new ArrayList<String>();
		parts.add(cursor.identifier());
		while (cursor.dot()) {
			if (parts.size() == 3)
				throw new IllegalArgumentException("Too many restore DDL qualifiers");
			parts.add(cursor.identifier());
		}
		return new Name(start, cursor.offset(), parts);
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
