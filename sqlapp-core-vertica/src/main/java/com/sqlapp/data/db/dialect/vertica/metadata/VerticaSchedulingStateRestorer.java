package com.sqlapp.data.db.dialect.vertica.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.schemas.Schema;

/**
 * Applies an exported Schema scheduling snapshot to already-created objects.
 * The caller must hold an exclusive maintenance window and stop the scheduler.
 * This class never creates objects or changes global configuration.
 */
public final class VerticaSchedulingStateRestorer {
	public record Change(String kind, String schema, String name, boolean enabled) {
	}

	public record Result(List<Change> applied, int unchanged) {
		public Result {
			applied = List.copyOf(applied);
		}
	}

	/**
	 * Scheduling functions are not transactional; exposes successfully completed
	 * changes. A failed JDBC call can have an uncertain outcome; inspect the target
	 * before retrying.
	 */
	public static final class RestorationException extends SQLException {
		private static final long serialVersionUID = 1L;
		private final Result completed;
		private final Change failedChange;

		RestorationException(SQLException cause, Result completed, Change failedChange) {
			super("Vertica scheduling state restoration failed; completed changes are available via getCompleted()",
					cause.getSQLState(), cause.getErrorCode(), cause);
			this.completed = completed;
			this.failedChange = failedChange;
		}

		public Result getCompleted() {
			return completed;
		}

		public Change getFailedChange() {
			return failedChange;
		}
	}

	/**
	 * Validates the whole target set, applies only differences, then bulk-verifies.
	 */
	public Result restore(Connection connection, Schema schema) throws SQLException {
		Objects.requireNonNull(connection, "connection");
		Objects.requireNonNull(schema, "schema");
		if (!schema.getSpecifics().containsKey("VERTICA_SCHEDULING_STATES"))
			throw new IllegalArgumentException(
					"Schema has no VERTICA_SCHEDULING_STATES snapshot; export it before restoration");
		var desired = parse(schema.getSpecifics().get("VERTICA_SCHEDULING_STATES"), schema.getName());
		requireStoppedScheduler(connection, schema);
		return apply(connection, schema, desired);
	}

	static void requireStoppedScheduler(Connection connection, Schema schema) throws SQLException {
		if (!connection.getAutoCommit())
			throw new SQLException("Scheduling state restoration requires a dedicated auto-commit connection");
		if (schema.getCatalogName() != null && !schema.getCatalogName().isBlank()
				&& !schema.getCatalogName().equalsIgnoreCase(connection.getCatalog()))
			throw new SQLException("Scheduling target catalog differs from the connected database");
		try (var statement = connection
				.prepareStatement("SELECT GET_CONFIG_PARAMETER('EnableStoredProcedureScheduler')");
				var rows = statement.executeQuery()) {
			if (!rows.next())
				throw new SQLException("Cannot determine scheduler state");
			String value = rows.getString(1);
			if (value == null || !(value.equals("0") || value.equalsIgnoreCase("false")) || rows.next())
				throw new SQLException(
						"Stop EnableStoredProcedureScheduler in an exclusive maintenance window before restoring states");
		}
	}

	private Result apply(Connection connection, Schema schema, Map<Key, Change> desired) throws SQLException {
		var current = read(connection, schema.getName());
		if (!desired.keySet().equals(current.keySet()))
			throw new SQLException(
					"Scheduling target set does not exactly match the exported snapshot; no states were changed");
		var changes = new ArrayList<Change>();
		int unchanged = 0;
		for (var entry : desired.entrySet()) {
			if (entry.getValue().enabled() == current.get(entry.getKey()).enabled())
				unchanged++;
			else
				changes.add(entry.getValue());
		}
		// Triggers first, schedules last. The stopped scheduler must remain stopped.
		changes.sort(Comparator.comparing(c -> c.kind().equals("TRIGGER") ? 0 : 1));
		var applied = new ArrayList<Change>();
		for (Change change : changes) {
			try (var statement = connection.prepareStatement("SELECT ENABLE_" + change.kind() + "(?, ?)")) {
				statement.setString(1, quote(change.schema()) + "." + quote(change.name()));
				statement.setBoolean(2, change.enabled());
				try (var rows = statement.executeQuery()) {
					while (rows.next()) {
						/* consume function result */ }
				}
				applied.add(change);
			} catch (SQLException failure) {
				throw new RestorationException(failure, new Result(applied, unchanged), change);
			}
		}
		try {
			var verified = read(connection, schema.getName());
			if (!desired.keySet().equals(verified.keySet()) || desired.entrySet().stream()
					.anyMatch(e -> e.getValue().enabled() != verified.get(e.getKey()).enabled()))
				throw new SQLException("Scheduling states differ after restoration");
		} catch (SQLException failure) {
			throw new RestorationException(failure, new Result(applied, unchanged), null);
		}
		return new Result(applied, unchanged);
	}

	private Map<Key, Change> read(Connection connection, String schema) throws SQLException {
		return parse(new VerticaScheduleStateExporter().export(connection, schema), schema);
	}

	private record Key(String kind, String schema, String name) {
	}

	static Map<Key, Change> parse(String snapshot, String schema) {
		if (schema == null || schema.isBlank())
			throw new IllegalArgumentException("Scheduling restore requires a schema name");
		// An explicitly present empty specifics value is read back from XML as
		// null. Public callers separately require the key, so absence is not guessed.
		if (snapshot == null)
			snapshot = "";
		var result = new LinkedHashMap<Key, Change>();
		var cursor = new SnapshotCursor(snapshot);
		while (!cursor.end()) {
			String kind = cursor.word();
			if (!(kind.equals("TRIGGER") || kind.equals("SCHEDULE")))
				throw new IllegalArgumentException("Unknown scheduling snapshot kind");
			String actualSchema = cursor.identifier();
			cursor.expect('.');
			String name = cursor.identifier();
			if (!schema.equals(actualSchema))
				throw new IllegalArgumentException("Scheduling snapshot belongs to another schema");
			if (!cursor.word().equals("ENABLED"))
				throw new IllegalArgumentException("Missing scheduling enabled state");
			cursor.expect('=');
			String value = cursor.word();
			if (!(value.equals("true") || value.equals("false")))
				throw new IllegalArgumentException("Invalid scheduling enabled state");
			cursor.expect(';');
			var change = new Change(kind, actualSchema, name, Boolean.parseBoolean(value));
			var key = new Key(kind, actualSchema.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
			if (result.putIfAbsent(key, change) != null)
				throw new IllegalArgumentException("Duplicate scheduling snapshot identity");
		}
		return result;
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	private static final class SnapshotCursor {
		private final String text;
		private int position;

		SnapshotCursor(String text) {
			this.text = text;
		}

		void skip() {
			while (position < text.length() && Character.isWhitespace(text.charAt(position)))
				position++;
		}

		boolean end() {
			skip();
			return position == text.length();
		}

		void expect(char c) {
			skip();
			if (position == text.length() || text.charAt(position++) != c)
				throw new IllegalArgumentException("Malformed scheduling snapshot");
		}

		String word() {
			skip();
			int start = position;
			while (position < text.length() && Character.isLetter(text.charAt(position)))
				position++;
			if (start == position)
				throw new IllegalArgumentException("Missing scheduling snapshot token");
			return text.substring(start, position);
		}

		String identifier() {
			expect('"');
			var name = new StringBuilder();
			while (position < text.length()) {
				char c = text.charAt(position++);
				if (c == '"') {
					if (position < text.length() && text.charAt(position) == '"') {
						position++;
						name.append('"');
					} else {
						if (name.isEmpty())
							throw new IllegalArgumentException("Empty scheduling identifier");
						return name.toString();
					}
				} else if (c == '\\') {
					if (position == text.length())
						throw new IllegalArgumentException("Truncated scheduling identifier escape");
					name.append(switch (text.charAt(position++)) {
					case '\\' -> '\\';
					case 'n' -> '\n';
					case 'r' -> '\r';
					case 't' -> '\t';
					default -> throw new IllegalArgumentException("Unknown scheduling identifier escape");
					});
				} else
					name.append(c);
			}
			throw new IllegalArgumentException("Unclosed scheduling identifier");
		}
	}
}
