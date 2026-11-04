package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.EventReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Read-only Scheduler job inventory using the 10g catalog baseline. */
public class Oracle10gEventReader extends EventReader {
	protected Oracle10gEventReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Event> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		Map<List<String>, Event> result = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString("schedulerJobs10g.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var key = java.util.Arrays.asList(getString(rs, "OWNER"), getString(rs, "JOB_NAME"));
				Event event = result.get(key);
				if (event == null) {
					event = createEvent(rs);
					event.getSpecifics().put("oracle.scheduler.argumentCount", "0");
					result.put(key, event);
				}
				addArgument(rs, event);
			}
		});
		List<Event> events = new ArrayList<>(result.values());
		readPrograms(connection, events);
		readSchedules(connection, events);
		return events;
	}

	private Map<List<String>, List<Event>> dependencyTargets(List<Event> events, String kind) {
		Map<List<String>, List<Event>> targets = new LinkedHashMap<>();
		String prefix = "oracle.scheduler." + kind.toLowerCase(java.util.Locale.ROOT) + ".";
		for (Event event : events) {
			String name = event.getSpecifics().get("oracle.scheduler." + kind + "_NAME");
			if (name == null || name.isBlank())
				continue;
			String owner = event.getSpecifics().get("oracle.scheduler." + kind + "_OWNER");
			if (owner == null || owner.isBlank()) {
				event.getSpecifics().put(prefix + "status", "UNRESOLVED");
				continue;
			}
			event.getSpecifics().put(prefix + "status", "UNAVAILABLE");
			targets.computeIfAbsent(List.of(owner, name), key -> new ArrayList<>()).add(event);
		}
		return targets;
	}

	private ParametersContext dependencyContext(Map<List<String>, List<Event>> targets, String nameKey) {
		ParametersContext context = new ParametersContext();
		context.put("schemaName", targets.keySet().stream().map(key -> key.get(0)).distinct().toList());
		context.put(nameKey, targets.keySet().stream().map(key -> key.get(1)).distinct().toList());
		return context;
	}

	private void fields(ExResultSet rs, Map<String, String> values, String prefix, String... columns)
			throws SQLException {
		for (String column : columns) {
			String value = rs.getString(column);
			if (value != null)
				values.put(prefix + column, value);
		}
	}

	private void readPrograms(Connection connection, List<Event> events) {
		var targets = dependencyTargets(events, "PROGRAM");
		if (targets.isEmpty())
			return;
		String prefix = "oracle.scheduler.program.";
		Map<List<String>, Map<String, String>> snapshots = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString("schedulerPrograms10g.sql"),
				dependencyContext(targets, "programName"), new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						var key = java.util.Arrays.asList(getString(rs, "OWNER"), getString(rs, "PROGRAM_NAME"));
						if (!targets.containsKey(key))
							return;
						Map<String, String> values = snapshots.get(key);
						if (values == null) {
							values = new LinkedHashMap<>();
							values.put(prefix + "status", "AVAILABLE");
							values.put(prefix + "argumentCount", "0");
							String enabled = getString(rs, "ENABLED");
							if (!"TRUE".equals(enabled) && !"FALSE".equals(enabled))
								throw new SQLException(
										"Invalid Oracle Scheduler Program ENABLED value: " + key + ": " + enabled);
							fields(rs, values, prefix, "PROGRAM_TYPE", "PROGRAM_ACTION", "NUMBER_OF_ARGUMENTS",
									"ENABLED", "COMMENTS");
							snapshots.put(key, values);
						}
						String rawPosition = getString(rs, "ARGUMENT_POSITION");
						if (rawPosition == null)
							return;
						int position;
						try {
							position = Integer.parseInt(rawPosition);
						} catch (NumberFormatException error) {
							throw new SQLException("Invalid Scheduler Program argument position: " + rawPosition,
									error);
						}
						if (position < 1)
							throw new SQLException(
									"Scheduler Program argument positions must be positive: " + rawPosition);
						String argPrefix = prefix + "argument." + position + ".";
						if (values.containsKey(argPrefix + "hasDefaultAnydataValue"))
							throw new SQLException(
									"Duplicate Scheduler Program argument position: " + key + ": " + position);
						String anydata = getString(rs, "HAS_DEFAULT_ANYDATA_VALUE");
						if (!"TRUE".equals(anydata) && !"FALSE".equals(anydata))
							throw new SQLException("Invalid Scheduler Program ANYDATA presence flag: " + anydata);
						values.put(argPrefix + "hasDefaultAnydataValue", anydata);
						for (String[] field : new String[][] { { "ARGUMENT_NAME", "name" }, { "ARGUMENT_TYPE", "type" },
								{ "DEFAULT_VALUE", "defaultValue" }, { "METADATA_ATTRIBUTE", "metadataAttribute" },
								{ "OUT_ARGUMENT", "outArgument" } }) {
							String value = rs.getString(field[0]);
							if (value != null)
								values.put(argPrefix + field[1], value);
						}
						int count = Integer.parseInt(values.get(prefix + "argumentCount"));
						values.put(prefix + "argumentCount", Integer.toString(count + 1));
					}
				});
		for (var entry : snapshots.entrySet())
			for (Event event : targets.get(entry.getKey()))
				event.getSpecifics().putAll(entry.getValue());
	}

	private void readSchedules(Connection connection, List<Event> events) {
		var targets = dependencyTargets(events, "SCHEDULE");
		if (targets.isEmpty())
			return;
		String prefix = "oracle.scheduler.schedule.";
		Map<List<String>, Map<String, String>> snapshots = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString("schedulerSchedules10g.sql"),
				dependencyContext(targets, "scheduleName"), new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						var key = java.util.Arrays.asList(getString(rs, "OWNER"), getString(rs, "SCHEDULE_NAME"));
						if (!targets.containsKey(key))
							return;
						if (snapshots.containsKey(key))
							throw new SQLException("Duplicate Oracle Scheduler Schedule: " + key);
						Map<String, String> values = new LinkedHashMap<>();
						values.put(prefix + "status", "AVAILABLE");
						fields(rs, values, prefix, "START_DATE_TEXT", "END_DATE_TEXT", "REPEAT_INTERVAL", "COMMENTS");
						snapshots.put(key, values);
					}
				});
		for (var entry : snapshots.entrySet())
			for (Event event : targets.get(entry.getKey()))
				event.getSpecifics().putAll(entry.getValue());
	}

	private void addArgument(ExResultSet rs, Event event) throws SQLException {
		String rawPosition = getString(rs, "ARGUMENT_POSITION");
		if (rawPosition == null)
			return; // LEFT JOIN for a job with no catalog arguments.
		int position;
		try {
			position = Integer.parseInt(rawPosition);
		} catch (NumberFormatException error) {
			throw new SQLException("Invalid Oracle Scheduler argument position: " + rawPosition, error);
		}
		if (position < 1)
			throw new SQLException("Oracle Scheduler argument positions must be positive: " + rawPosition);
		String prefix = "oracle.scheduler.argument." + position + ".";
		if (event.getSpecifics().containsKey(prefix + "hasAnydataValue"))
			throw new SQLException("Duplicate Oracle Scheduler argument position for " + event.getSchemaName() + "."
					+ event.getName() + ": " + position);
		String anydata = getString(rs, "HAS_ANYDATA_VALUE");
		if (!"TRUE".equals(anydata) && !"FALSE".equals(anydata))
			throw new SQLException("Invalid Oracle Scheduler ANYDATA presence flag: " + anydata);
		event.getSpecifics().put(prefix + "hasAnydataValue", anydata);
		for (String[] field : new String[][] { { "ARGUMENT_NAME", "name" }, { "ARGUMENT_TYPE", "type" },
				{ "ARGUMENT_VALUE", "value" }, { "OUT_ARGUMENT", "outArgument" } }) {
			String value = rs.getString(field[0]);
			if (value != null)
				event.getSpecifics().put(prefix + field[1], value);
		}
		int count = Integer.parseInt(event.getSpecifics().get("oracle.scheduler.argumentCount"));
		event.getSpecifics().put("oracle.scheduler.argumentCount", Integer.toString(count + 1));
	}

	protected Event createEvent(ExResultSet rs) throws SQLException {
		String name = getString(rs, "JOB_NAME"), owner = getString(rs, "OWNER");
		if (name == null || name.isBlank() || owner == null || owner.isBlank())
			throw new SQLException("Oracle Scheduler job owner and name are required");
		Event event = new Event(name);
		event.setSchemaName(owner);
		event.setStatement(rs.getString("JOB_ACTION"));
		event.setRemarks(rs.getString("COMMENTS"));
		String enabled = getString(rs, "ENABLED");
		if (!"TRUE".equalsIgnoreCase(enabled) && !"FALSE".equalsIgnoreCase(enabled))
			throw new SQLException("Invalid Oracle Scheduler ENABLED value for " + event.getName() + ": " + enabled);
		event.setEnable("TRUE".equalsIgnoreCase(enabled));
		event.getSpecifics().put("oracle.scheduler.kind", "JOB");
		for (String column : List.of("JOB_CREATOR", "JOB_TYPE", "PROGRAM_OWNER", "PROGRAM_NAME", "NUMBER_OF_ARGUMENTS",
				"SCHEDULE_OWNER", "SCHEDULE_NAME", "REPEAT_INTERVAL", "JOB_CLASS", "AUTO_DROP", "RESTARTABLE", "STATE",
				"JOB_PRIORITY", "MAX_RUNS", "MAX_FAILURES", "DESTINATION", "NLS_ENV", "START_DATE_TEXT",
				"END_DATE_TEXT", "LAST_START_DATE_TEXT", "NEXT_RUN_DATE_TEXT")) {
			String value = rs.getString(column);
			if (value != null)
				event.getSpecifics().put("oracle.scheduler." + column, value);
		}
		// Do not translate Oracle calendar expressions into MySQL-style interval
		// fields, or discard a time zone by populating java.sql.Timestamp fields.
		return event;
	}
}
