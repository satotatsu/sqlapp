package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Captures native Scheduler scripts on qualified modern engines without
 * executing them.
 */
public class Oracle23aiEventReader extends Oracle10gEventReader {
	protected Oracle23aiEventReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected String programSql() {
		return "schedulerPrograms23ai.sql";
	}

	@Override
	protected void readProgramDefaultRepresentation(ExResultSet rs, Map<String, String> values, String prefix)
			throws SQLException {
		if ("TRUE".equals(values.get(prefix + "hasDefaultAnydataValue"))) {
			String text = values.get(prefix + "defaultValue");
			values.put(prefix + "defaultRepresentation",
					text != null && text.equals(rs.getString("DEFAULT_ANYDATA_TEXT")) ? "TEXT" : "UNSUPPORTED");
		}
	}

	@Override
	protected String jobSql() {
		return "schedulerJobs23ai.sql";
	}

	@Override
	protected void readArgumentRepresentation(ExResultSet rs, Event event, String prefix) throws SQLException {
		String representation = rs.getString("ARGUMENT_REPRESENTATION");
		if (!"TEXT".equals(representation) && !"UNSUPPORTED".equals(representation))
			throw new SQLException("Invalid Scheduler argument representation: " + representation);
		event.getSpecifics().put(prefix + "representation", representation);
	}

	private record Destination(Event event, String prefix) {
	}

	private void target(Map<List<String>, List<Destination>> targets, String kind, String owner, String name,
			Event event, String prefix) {
		if (owner == null || owner.isBlank() || name == null || name.isBlank())
			throw new IllegalArgumentException("Scheduler DDL capture requires owner/name: " + kind);
		event.getSpecifics().put(prefix + "definitionStatus", "UNAVAILABLE");
		targets.computeIfAbsent(List.of(kind, owner, name), key -> new ArrayList<>())
				.add(new Destination(event, prefix));
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Event> events)
			throws SQLException {
		if (events == null || events.isEmpty())
			return;
		Map<List<String>, List<Destination>> targets = new LinkedHashMap<>();
		for (Event event : events) {
			// A failed refresh must not expose scripts from an earlier successful read.
			event.getDefinition().clear();
			Map<String, String> retained = new LinkedHashMap<>(event.getSpecifics());
			for (String prefix : List.of("oracle.scheduler.", "oracle.scheduler.program.",
					"oracle.scheduler.schedule.")) {
				for (String suffix : List.of("definition", "definitionStatus", "definitionContext", "definitionOwner",
						"definitionName", "definitionFingerprint"))
					retained.keySet().removeIf(key -> key.equalsIgnoreCase(prefix + suffix));
			}
			event.getSpecifics().clear();
			event.getSpecifics().putAll(retained);
			target(targets, "JOB", event.getSchemaName(), event.getName(), event, "oracle.scheduler.");
			for (String kind : List.of("PROGRAM", "SCHEDULE")) {
				String prefix = "oracle.scheduler." + kind.toLowerCase(java.util.Locale.ROOT) + ".";
				if ("AVAILABLE".equals(event.getSpecifics().get(prefix + "status")))
					target(targets, kind, event.getSpecifics().get("oracle.scheduler." + kind + "_OWNER"),
							event.getSpecifics().get("oracle.scheduler." + kind + "_NAME"), event, prefix);
			}
		}
		Map<List<String>, String> definitions = new LinkedHashMap<>();
		try {
			execute(connection, getSqlNodeCache().getString("schedulerDefinitions23ai.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							var key = java.util.Arrays.asList(getString(rs, "OBJECT_KIND"), getString(rs, "OWNER"),
									getString(rs, "OBJECT_NAME"));
							if (!targets.containsKey(key))
								return;
							String ddl = rs.getString("OBJECT_DEFINITION");
							if (ddl == null || ddl.isBlank())
								throw new IllegalStateException("Oracle Scheduler DDL unavailable: " + key);
							if (definitions.putIfAbsent(key, ddl) != null)
								throw new IllegalStateException("Duplicate Oracle Scheduler DDL: " + key);
						}
					});
		} catch (RuntimeException error) {
			if (!OracleMetadataAccess.isDenied(error))
				throw error;
			for (var destinations : targets.values())
				for (Destination destination : destinations)
					destination.event().getSpecifics().put(destination.prefix() + "definitionStatus", "INACCESSIBLE");
			logger.warn(
					"Oracle Scheduler DBMS_METADATA access denied or object inaccessible; native scripts skipped, inventory retained.");
			return;
		}
		for (var key : targets.keySet())
			if (!definitions.containsKey(key))
				throw new IllegalStateException("Oracle Scheduler DDL unavailable: " + key);
		// Publish only after the complete batch has been fetched and validated.
		for (var entry : targets.entrySet()) {
			String ddl = definitions.get(entry.getKey());
			for (Destination destination : entry.getValue()) {
				// PROCOBJ omits argument values/defaults on qualified engines. It is
				// a native base script, not a complete executable Event definition.
				destination.event().getSpecifics().put(destination.prefix() + "definition", ddl);
				destination.event().getSpecifics().put(destination.prefix() + "definitionStatus", "AVAILABLE");
				destination.event().getSpecifics().put(destination.prefix() + "definitionContext", "SOURCE_OWNER");
				destination.event().getSpecifics().put(destination.prefix() + "definitionOwner", entry.getKey().get(1));
				destination.event().getSpecifics().put(destination.prefix() + "definitionName", entry.getKey().get(2));
			}
		}
		for (Event event : events)
			event.getSpecifics().put(com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerDefinition.FINGERPRINT,
					com.sqlapp.data.db.dialect.oracle.util.OracleSchedulerDefinition.fingerprint(event));
	}
}
