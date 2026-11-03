package com.sqlapp.data.db.dialect.vertica.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.vertica.DialectHolder;
import com.sqlapp.data.schemas.*;

/**
 * Captures implicit raw-column attributes not always present in Flex CREATE
 * exports.
 */
final class VerticaFlexRawColumns {
	record Raw(long length, boolean nullable) {
	}

	record Capture(Map<String, String> definitions, Map<String, Raw> columns) {
		void apply(Schema schema) {
			definitions.forEach((name, ddl) -> {
				Table table = find(schema, name);
				if (table == null) {
					table = new Table(name);
					schema.getTables().add(table);
				}
				table.setTableType(Table.TableType.Flex);
				table.setDefinition(ddl);
				Column raw = table.getColumns().get("__raw__");
				if (raw == null) {
					raw = new Column("__raw__");
					table.getColumns().add(raw);
				}
				Raw metadata = columns.get(name);
				raw.setDataType(DataType.LONGVARBINARY).setLength(metadata.length()).setNullable(metadata.nullable());
			});
		}
	}

	static Capture capture(Connection connection, Schema schema, String script) throws SQLException {
		Map<String, String> definitions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (var statement : DialectHolder.defaultDialect12_0_4.createSqlSplitter().parse(script)) {
			if (statement.getTextType().isComment())
				continue;
			var cursor = new VerticaProjectionDdlMapper.Cursor(statement.getText());
			if (!cursor.keyword("CREATE") || !(cursor.keyword("FLEX") || cursor.keyword("FLEXIBLE"))
					|| !cursor.keyword("TABLE"))
				continue;
			var names = new ArrayList<String>();
			names.add(cursor.identifier());
			while (cursor.dot()) {
				if (names.size() == 3)
					throw new SQLException("Too many Flex table qualifiers");
				names.add(cursor.identifier());
			}
			if (names.size() < 2 || !schema.getName().equalsIgnoreCase(names.get(names.size() - 2)))
				throw new SQLException("Flex export escaped schema scope");
			if (names.size() == 3
					&& (schema.getCatalogName() == null || !names.get(0).equalsIgnoreCase(schema.getCatalogName())))
				throw new SQLException("Flex table export catalog does not match the Schema model");
			String name = names.get(names.size() - 1);
			if (definitions.putIfAbsent(name, statement.getText().strip() + "\n;") != null)
				throw new SQLException("Duplicate Flex table CREATE");
		}
		if (definitions.isEmpty())
			return new Capture(definitions, Map.of());
		var all = read(connection, schema.getName());
		Map<String, Raw> columns = new LinkedHashMap<>();
		for (String name : definitions.keySet()) {
			if (schema.getTables().stream().filter(t -> t.getName().equalsIgnoreCase(name)).count() > 1)
				throw new SQLException("Ambiguous Flex table identity in the source model: " + name);
			Raw raw = all.get(name.toLowerCase(Locale.ROOT));
			if (raw == null)
				throw new SQLException("Flex raw-column metadata missing: " + name);
			columns.put(name, raw);
		}
		return new Capture(definitions, columns);
	}

	static Map<String, Raw> read(Connection connection, String schema) throws SQLException {
		String sql = "SELECT t.table_schema,t.table_name,c.character_maximum_length,c.is_nullable "
				+ "FROM v_catalog.tables t JOIN v_catalog.columns c ON c.table_id=t.table_id "
				+ "WHERE t.table_schema=? AND t.is_flextable AND c.column_name='__raw__'";
		Map<String, Raw> result = new LinkedHashMap<>();
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, schema);
			try (var rows = statement.executeQuery()) {
				while (rows.next()) {
					String actualSchema = rows.getString(1), name = rows.getString(2);
					long length = rows.getLong(3);
					boolean nullLength = rows.wasNull();
					boolean nullable = rows.getBoolean(4);
					boolean nullNullable = rows.wasNull();
					if (!schema.equals(actualSchema) || name == null || nullLength || nullNullable || length <= 0)
						throw new SQLException("Invalid Flex raw-column metadata row");
					if (result.putIfAbsent(name.toLowerCase(Locale.ROOT), new Raw(length, nullable)) != null)
						throw new SQLException("Duplicate Flex raw-column metadata");
				}
			}
		}
		return result;
	}

	static void requireSourceMetadata(Schema schema, String tableName) {
		Table table = find(schema, tableName);
		Column raw = table == null ? null : table.getColumns().get("__raw__");
		if (raw == null || raw.getLength() == null || raw.getLength() <= 0
				|| raw.getDataType() != DataType.LONGVARBINARY)
			throw new IllegalArgumentException(
					"Flex restore requires exported __raw__ column metadata for " + tableName);
	}

	static void verify(Connection connection, Schema schema, Set<String> expected) throws SQLException {
		var actual = read(connection, schema.getName());
		if (!actual.keySet().equals(expected))
			throw new SQLException("Generated Flex raw-column set differs from the restore plan");
		for (var entry : actual.entrySet()) {
			Table source = find(schema, entry.getKey());
			Column raw = source == null ? null : source.getColumns().get("__raw__");
			if (raw == null || !Objects.equals(raw.getLength(), entry.getValue().length())
					|| raw.isNotNull() == entry.getValue().nullable())
				throw new SQLException(
						"Generated Flex __raw__ attributes differ from the source model: " + entry.getKey());
		}
	}

	private static Table find(Schema schema, String name) {
		var matches = schema.getTables().stream().filter(t -> t.getName().equalsIgnoreCase(name)).toList();
		return matches.size() == 1 ? matches.get(0) : null;
	}
}
