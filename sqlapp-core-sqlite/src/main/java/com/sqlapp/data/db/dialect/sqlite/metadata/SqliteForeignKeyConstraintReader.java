/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** Reads SQLite foreign keys from {@code PRAGMA foreign_key_list}. */
public class SqliteForeignKeyConstraintReader extends ForeignKeyConstraintReader {
	public SqliteForeignKeyConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		if (SqliteIndexReader.supportsBulkIndexes(productVersionInfo))
			return readBulk(connection, context);
		final String tableName = getTableName(context);
		if (tableName == null) {
			return list();
		}
		final String schemaName = getSchemaName(context) == null ? "main" : getSchemaName(context);
		final String sql = "PRAGMA " + quoteIdentifier(schemaName) + ".foreign_key_list(" + quoteString(tableName)
				+ ")";
		final Map<Integer, ForeignKeyConstraint> constraints = new LinkedHashMap<>();
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				final int id = resultSet.getInt("id");
				final ForeignKeyConstraint constraint = constraints.computeIfAbsent(id,
						key -> createConstraint(context, schemaName, tableName, key, getUnchecked(resultSet, "table"),
								getUnchecked(resultSet, "on_update"), getUnchecked(resultSet, "on_delete")));
				constraint.getColumns().add(new Column(resultSet.getString("from")));
				if (resultSet.getString("to") != null)
					constraint.getRelatedColumns().add(resultSet.getString("to"));
			}
			resolveLegacyParents(connection, schemaName, constraints.values());
			if (!constraints.isEmpty()) {
				try (var ddl = connection.prepareStatement("SELECT sql FROM " + quoteIdentifier(schemaName)
						+ ".sqlite_master WHERE type='table' AND name=?")) {
					ddl.setString(1, tableName);
					try (var rs = ddl.executeQuery()) {
						if (!rs.next())
							throw new IllegalArgumentException("SQLite table definition is missing: " + tableName);
						applyDeclarations(constraints.values(), Map.of(tableName, rs.getString(1)));
					}
				}
			}
			return List.copyOf(constraints.values());
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private List<ForeignKeyConstraint> readBulk(Connection connection, ParametersContext context) {
		Map<String, ForeignKeyConstraint> constraints = new LinkedHashMap<>();
		Map<String, Integer> parentKeyCounts = new LinkedHashMap<>();
		Map<String, String> definitions = new LinkedHashMap<>();
		String schema = getSchemaName(context) == null ? "main" : getSchemaName(context);
		try (var statement = SqliteMetadataQueries.prepare(connection, context,
				"p.*, COALESCE(p.\"to\", r.name) AS referenced_column, "
						+ "(SELECT count(*) FROM pragma_table_info(p.\"table\", ?) WHERE pk>0) AS parent_key_count",
				"JOIN pragma_foreign_key_list(m.name, ?) p LEFT JOIN pragma_table_info(p.\"table\", ?) r "
						+ "ON p.\"to\" IS NULL AND r.pk=p.seq+1",
				", p.id, p.seq"); var rs = statement.executeQuery()) {
			while (rs.next()) {
				String table = rs.getString("table_name");
				definitions.putIfAbsent(table, rs.getString("table_sql"));
				String key = table + "\u0000" + rs.getInt("id");
				var constraint = constraints.get(key);
				if (constraint == null) {
					constraint = createConstraint(context, schema, table, rs.getInt("id"), rs.getString("table"),
							rs.getString("on_update"), rs.getString("on_delete"));
					constraints.put(key, constraint);
				}
				constraint.getColumns().add(new Column(rs.getString("from")));
				String referenced = rs.getString("referenced_column");
				if (referenced == null)
					throw unresolvedParent(constraint);
				constraint.getRelatedColumns().add(referenced);
				if (rs.getString("to") == null)
					parentKeyCounts.put(key, rs.getInt("parent_key_count"));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		parentKeyCounts.forEach((key, count) -> {
			if (count != constraints.get(key).getColumns().size())
				throw unresolvedParent(constraints.get(key));
		});
		applyDeclarations(constraints.values(), definitions);
		return List.copyOf(constraints.values());
	}

	private void applyDeclarations(java.util.Collection<ForeignKeyConstraint> constraints,
			Map<String, String> definitions) {
		Map<String, List<SqliteDdl.ForeignKeyDeclaration>> declarations = new LinkedHashMap<>();
		definitions.forEach((table, sql) -> {
			List<SqliteDdl.ForeignKeyDeclaration> items = new java.util.ArrayList<>();
			SqliteColumnReader.splitColumnDefinitions(sql)
					.forEach(fragment -> items.addAll(SqliteDdl.foreignKeys(fragment)));
			declarations.put(table, items);
		});
		for (var constraint : constraints) {
			var candidates = declarations.get(constraint.getTableName());
			var columns = constraint.getColumns().stream().map(c -> c.getName()).toList();
			var parentColumns = constraint.getRelatedColumns().stream().map(c -> c.getName()).toList();
			var declaration = candidates.stream()
					.filter(item -> sameNames(item.columns(), columns)
							&& item.parent().equalsIgnoreCase(constraint.getRelatedTableName())
							&& (item.parentColumns().isEmpty() || sameNames(item.parentColumns(), parentColumns))
							&& item.updateRule() == constraint.getUpdateRule()
							&& item.deleteRule() == constraint.getDeleteRule())
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException(
							"Cannot match SQLite foreign key to CREATE TABLE definition: "
									+ constraint.getTableName()));
			candidates.remove(declaration);
			constraint.setDeferrability(declaration.deferrability());
			if (declaration.name() != null)
				constraint.setName(declaration.name());
		}
	}

	private boolean sameNames(List<String> left, List<String> right) {
		if (left.size() != right.size())
			return false;
		for (int i = 0; i < left.size(); i++)
			if (!left.get(i).equalsIgnoreCase(right.get(i)))
				return false;
		return true;
	}

	private IllegalArgumentException unresolvedParent(ForeignKeyConstraint constraint) {
		return new IllegalArgumentException("Cannot resolve SQLite foreign key " + constraint.getTableName() + " -> "
				+ constraint.getRelatedTableName()
				+ ": referenced primary key must exist and match the child column count");
	}

	/**
	 * Before 3.16, PRAGMAs cannot be joined; cache each referenced parent's key.
	 */
	private void resolveLegacyParents(Connection connection, String schema,
			java.util.Collection<ForeignKeyConstraint> constraints) throws SQLException {
		Map<String, List<String>> parents = new LinkedHashMap<>();
		for (var constraint : constraints) {
			if (!constraint.getRelatedColumns().isEmpty())
				continue;
			String parent = constraint.getRelatedTableName();
			List<String> columns = parents.get(parent);
			if (columns == null) {
				Map<Integer, String> ordered = new java.util.TreeMap<>();
				try (var statement = connection.createStatement();
						var rs = statement.executeQuery(
								"PRAGMA " + quoteIdentifier(schema) + ".table_info(" + quoteString(parent) + ")")) {
					while (rs.next())
						if (rs.getInt("pk") > 0)
							ordered.put(rs.getInt("pk"), rs.getString("name"));
				}
				columns = List.copyOf(ordered.values());
				parents.put(parent, columns);
			}
			if (columns.isEmpty() || columns.size() != constraint.getColumns().size())
				throw unresolvedParent(constraint);
			columns.forEach(constraint.getRelatedColumns()::add);
		}
	}

	private ForeignKeyConstraint createConstraint(final ParametersContext context, final String schemaName,
			final String tableName, final int id, final String relatedTable, final String updateRule,
			final String deleteRule) {
		final ForeignKeyConstraint constraint = new ForeignKeyConstraint("sqlite_fk_" + tableName + "_" + id);
		constraint.setDialect(getDialect());
		constraint.setCatalogName(getCatalogName(context));
		constraint.setSchemaName(getSchemaName(context));
		constraint.setTableName(tableName);
		constraint.setRelatedTableSchemaName(schemaName);
		constraint.setRelatedTableName(relatedTable);
		constraint.setUpdateRule(CascadeRule.parse(updateRule));
		constraint.setDeleteRule(CascadeRule.parse(deleteRule));
		return constraint;
	}

	private String getUnchecked(final java.sql.ResultSet resultSet, final String name) {
		try {
			return resultSet.getString(name);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private String quoteString(final String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}
