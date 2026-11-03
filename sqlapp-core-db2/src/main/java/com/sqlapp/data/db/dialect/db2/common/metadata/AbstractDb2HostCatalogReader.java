/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcCatalogReader;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcSchemaReader;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Bulk row mapping for host catalogs. SQL resources belong to the concrete
 * product module.
 */
public abstract class AbstractDb2HostCatalogReader extends JdbcCatalogReader {

	private final Class<?> resources;

	protected AbstractDb2HostCatalogReader(Dialect dialect, Class<?> resources) {
		super(dialect);
		this.resources = resources;
	}

	@Override
	protected List<Catalog> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		return new ArrayList<>(List.of(new Catalog()));
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new HostSchemaReader(getDialect(), resources);
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return null;
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return null;
	}

	private static class HostSchemaReader extends JdbcSchemaReader {
		private final Class<?> resources;

		HostSchemaReader(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<Schema> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			List<Schema> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("schemas.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							result.add(new Schema(identifier(rs, "SCHEMA_NAME")));
						}
					});
			return result;
		}

		@Override
		protected TableReader newTableReader() {
			return new HostTableReader(getDialect(), resources);
		}

		@Override
		protected ViewReader newViewReader() {
			return null;
		}

		@Override
		protected DomainReader newDomainReader() {
			return null;
		}

		@Override
		protected SynonymReader newSynonymReader() {
			return null;
		}

		@Override
		protected FunctionReader newFunctionReader() {
			return null;
		}

		@Override
		protected ProcedureReader newProcedureReader() {
			return null;
		}

		@Override
		protected TypeReader newTypeReader() {
			return null;
		}
	}

	private static class HostTableReader extends JdbcTableReader {
		private final Class<?> resources;

		HostTableReader(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<Table> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			List<Table> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("tables.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							Table table = new Table(identifier(rs, "TABLE_NAME"));
							table.setSchemaName(identifier(rs, "SCHEMA_NAME"));
							table.setRemarks(rs.getString("REMARKS"));
							result.add(table);
						}
					});
			return result;
		}

		@Override
		protected void setFilter(List<Table> tables, ParametersContext context) {
			context.put("schemaName", tables.stream().map(Table::getSchemaName).distinct().toList());
			context.put("tableName", tables.stream().map(Table::getName).distinct().toList());
		}

		@Override
		protected ColumnReader newColumnReader() {
			return new HostColumnReader(getDialect(), resources);
		}

		@Override
		protected IndexReader newIndexReader() {
			return null;
		}

		@Override
		protected UniqueConstraintReader newUniqueConstraintReader() {
			return new HostUniqueReader(getDialect(), resources);
		}

		@Override
		protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
			return new HostForeignReader(getDialect(), resources);
		}
	}

	private static class HostColumnReader extends ColumnReader {
		private final Class<?> resources;

		HostColumnReader(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<Column> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			List<Column> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("columns.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							Column column = new Column(identifier(rs, "COLUMN_NAME"));
							column.setSchemaName(identifier(rs, "SCHEMA_NAME"));
							column.setTableName(identifier(rs, "TABLE_NAME"));
							String type = identifier(rs, "TYPE_NAME");
							type = switch (type) {
							case "VARGRAPH" -> "VARGRAPHIC";
							case "VARBIN" -> "VARBINARY";
							case "TIMESTMP" -> "TIMESTAMP";
							default -> type;
							};
							if (!getDialect().setDbType(type, rs.getLongValue("TYPE_LENGTH"),
									rs.getInteger("TYPE_SCALE"), column))
								throw new SQLException(
										"Unsupported Db2 host type " + type + " for " + column.getName());
							column.setNullable("Y".equals(rs.getString("NULLABLE")));
							column.setRemarks(rs.getString("REMARKS"));
							String unsupported = rs.getString("UNSUPPORTED_GENERATION");
							if (unsupported != null && !unsupported.isBlank())
								throw new SQLException("Unsupported generated Db2 host column: " + column.getName()
										+ " (" + unsupported + ")");
							column.setDefaultValue(rs.getString("DEFAULT_SQL"));
							String mode = identifier(rs, "IDENTITY_MODE");
							if (mode != null && !mode.isBlank()) {
								if (!"A".equals(mode) && !"D".equals(mode))
									throw new SQLException("Invalid identity mode: " + mode);
								column.setIdentity(true);
								column.setIdentityGenerationType("A".equals(mode) ? IdentityGenerationType.Always
										: IdentityGenerationType.ByDefault);
								column.setSequence(new Sequence());
								// Sequence supports exact DECIMAL(31,0) values, unlike long-valued column
								// attributes.
								var sequence = column.getSequence();
								sequence.setStartValue(rs.getBigDecimal("IDENTITY_START"));
								sequence.setIncrementBy(rs.getBigDecimal("IDENTITY_INCREMENT"));
								sequence.setMinValue(rs.getBigDecimal("IDENTITY_MINIMUM"));
								sequence.setMaxValue(rs.getBigDecimal("IDENTITY_MAXIMUM"));
								if (sequence.getStartValue() == null || sequence.getIncrementBy() == null)
									throw new SQLException(
											"Missing identity sequence metadata for " + column.getName());
								column.setIdentityCycle("Y".equals(rs.getString("IDENTITY_CYCLE")));
								column.setIdentityCacheSize(rs.getInteger("IDENTITY_CACHE"));
								column.setIdentityOrder("Y".equals(rs.getString("IDENTITY_ORDER")));
							}
							result.add(column);
						}
					});
			return result;
		}
	}

	private static class HostUniqueReader extends UniqueConstraintReader {
		private final Class<?> resources;

		HostUniqueReader(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<UniqueConstraint> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			Map<List<String>, UniqueConstraint> result = new LinkedHashMap<>();
			execute(connection, getSqlNodeCache(resources).getString("uniqueKeys.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							String schema = identifier(rs, "SCHEMA_NAME"), table = identifier(rs, "TABLE_NAME"),
									name = identifier(rs, "CONSTRAINT_NAME");
							var key = List.of(schema, table, name);
							var constraint = result.get(key);
							if (constraint == null) {
								constraint = new UniqueConstraint(name, "P".equals(rs.getString("KEY_TYPE")));
								constraint.setSchemaName(schema);
								constraint.setTableName(table);
								result.put(key, constraint);
							}
							constraint.getColumns().add(new Column(identifier(rs, "COLUMN_NAME")));
						}
					});
			return new ArrayList<>(result.values());
		}
	}

	private static class HostForeignReader extends ForeignKeyConstraintReader {
		private final Class<?> resources;

		HostForeignReader(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<ForeignKeyConstraint> doGetAll(Connection connection, ParametersContext context,
				ProductVersionInfo version) {
			Map<List<String>, ForeignKeyConstraint> result = new LinkedHashMap<>();
			Map<List<String>, List<Column>> columns = new LinkedHashMap<>(), references = new LinkedHashMap<>();
			execute(connection, getSqlNodeCache(resources).getString("foreignKeys.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							String schema = identifier(rs, "SCHEMA_NAME"), table = identifier(rs, "TABLE_NAME"),
									name = identifier(rs, "CONSTRAINT_NAME");
							var key = List.of(schema, table, name);
							String refSchema = identifier(rs, "REF_SCHEMA_NAME"),
									refTable = identifier(rs, "REF_TABLE_NAME"),
									refColumn = identifier(rs, "REF_COLUMN_NAME");
							if (refSchema == null || refTable == null || refColumn == null)
								throw new SQLException("Unresolved referenced key: " + key);
							var constraint = result.get(key);
							if (constraint == null) {
								constraint = new ForeignKeyConstraint(name);
								constraint.setSchemaName(schema);
								constraint.setTableName(table);
								constraint.setEnable("Y".equals(rs.getString("ENFORCED")));
								constraint.setDeleteRule(rule(rs.getString("DELETE_RULE")));
								constraint.setUpdateRule(rule(rs.getString("UPDATE_RULE")));
								result.put(key, constraint);
								columns.put(key, new ArrayList<>());
								references.put(key, new ArrayList<>());
							}
							columns.get(key).add(createColumn(null, schema, table, identifier(rs, "COLUMN_NAME")));
							references.get(key).add(createColumn(null, refSchema, refTable, refColumn));
						}
					});
			for (var entry : result.entrySet()) {
				entry.getValue().setColumns(columns.get(entry.getKey()).toArray(Column[]::new));
				var related = references.get(entry.getKey());
				var parent = new Table(related.get(0).getTableName());
				parent.setSchemaName(related.get(0).getSchemaName());
				parent.getColumns().addAll(related);
				entry.getValue().setRelatedColumns(related.toArray(Column[]::new));
			}
			return new ArrayList<>(result.values());
		}
	}

	private static String identifier(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		return value == null ? null : value.stripTrailing();
	}

	private static CascadeRule rule(String value) throws SQLException {
		CascadeRule rule = CascadeRule.parse(value);
		if (rule == null)
			throw new SQLException("Unknown referential action: " + value);
		return rule;
	}
}
