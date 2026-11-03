package com.sqlapp.data.db.dialect.virtica.metadata;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * One schema-scoped query preserves independent schedule/trigger enable states.
 */
final class VirticaScheduleStateExporter {
	String export(Connection connection, String schema) throws SQLException {
		String sql = "SELECT 'TRIGGER',schema_name,trigger_name,enabled FROM v_catalog.stored_proc_triggers WHERE schema_name=? "
				+ "UNION ALL SELECT 'SCHEDULE',schema_name,schedule_name,enabled FROM v_catalog.user_schedules WHERE schema_name=? "
				+ "ORDER BY 1 DESC,3";
		StringBuilder script = new StringBuilder();
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, schema);
			statement.setString(2, schema);
			try (var result = statement.executeQuery()) {
				while (result.next()) {
					String kind = result.getString(1);
					String actualSchema = result.getString(2);
					String name = result.getString(3);
					boolean enabled = result.getBoolean(4);
					if (result.wasNull() || !schema.equals(actualSchema) || name == null || name.isBlank()
							|| !("SCHEDULE".equals(kind) || "TRIGGER".equals(kind)))
						throw new SQLException("Invalid schema-scoped scheduling state row");
					script.append(kind).append(' ').append(quote(schema)).append('.').append(quote(name))
							.append(" ENABLED=").append(enabled).append("; ");
				}
			}
		}
		return script.toString();
	}

	private String quote(String name) {
		return "\"" + name.replace("\\", "\\\\").replace("\"", "\"\"").replace("\n", "\\n").replace("\r", "\\r")
				.replace("\t", "\\t") + "\"";
	}
}
