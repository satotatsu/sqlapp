/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.common.Db2HostTables;
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
		return new Db2HostProgrammableReaders.ObjectGrants(getDialect(), resources);
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return new Db2HostProgrammableReaders.ColumnGrants(getDialect(), resources);
	}

	@Override
	protected RoutinePrivilegeReader newRoutinePrivilegeReader() {
		return new Db2HostProgrammableReaders.RoutineGrants(getDialect(), resources);
	}

	/** Normal full-catalog loading includes schema grants automatically. */
	public com.sqlapp.data.db.dialect.db2.metadata.Db2SchemaPrivilegeReader getSchemaPrivilegeReader() {
		var reader = new Db2HostProgrammableReaders.SchemaGrants(getDialect(), resources);
		setReaderParameter(reader);
		return reader;
	}

	@Override
	protected void setCommonAfter(Connection connection, Catalog catalog) {
		super.setCommonAfter(connection, catalog);
		load(connection, getSchemaPrivilegeReader(), catalog);
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
			return new Db2HostSupplementalReaders.Views(getDialect(), resources);
		}

		@Override
		protected SequenceReader newSequenceReader() {
			return new Db2HostSupplementalReaders.Sequences(getDialect(), resources);
		}

		@Override
		protected DomainReader newDomainReader() {
			return new Db2HostDomainReader(getDialect(), resources);
		}

		@Override
		protected SynonymReader newSynonymReader() {
			return new Db2HostSynonymReader(getDialect(), resources);
		}

		@Override
		protected FunctionReader newFunctionReader() {
			return new Db2HostProgrammableReaders.Functions(getDialect(), resources);
		}

		@Override
		protected ProcedureReader newProcedureReader() {
			return new Db2HostProgrammableReaders.Procedures(getDialect(), resources);
		}

		@Override
		protected TriggerReader newTriggerReader() {
			return new Db2HostProgrammableReaders.Triggers(getDialect(), resources);
		}

		@Override
		protected TypeReader newTypeReader() {
			return null;
		}

		@Override
		protected MviewReader newMviewReader() {
			return new Db2HostMviewReader(getDialect(), resources);
		}

		@Override
		protected MaskReader newMaskReader() {
			return new Db2HostSecurityReaders.Masks(getDialect(), resources);
		}

		@Override
		protected void setSchemaAfter(Connection connection, Schema schema) {
			super.setSchemaAfter(connection, schema);
			var permissions = new ArrayList<com.sqlapp.data.db.dialect.db2.Db2RowPermissions.Permission>();
			var context = new ParametersContext();
			context.put("schemaName", schema.getName());
			execute(connection, getSqlNodeCache(resources).getString("permissions.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							permissions.add(Db2HostSecurityReaders.permission(rs));
						}
					});
			Db2HostSecurityReaders.apply(schema, permissions);
		}
	}

	private static class HostTableReader extends JdbcTableReader {
		private final Class<?> resources;
		/** Attributes completed after the columns are attached. */
		private final Map<Table, Db2HostTableAssembler.Pending> pending = new java.util.IdentityHashMap<>();

		HostTableReader(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		private com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect.Platform platform() {
			return ((com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect) getDialect()).getPlatform();
		}

		@Override
		protected List<Table> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			Map<List<String>, Table> result = new LinkedHashMap<>();
			var platform = platform();
			execute(connection, getSqlNodeCache(resources).getString("tables.sql"), context,
					new ResultSetNextHandler() {
						@Override
						public void handleResultSetNext(ExResultSet rs) throws SQLException {
							String schema = identifier(rs, "SCHEMA_NAME"), name = identifier(rs, "TABLE_NAME");
							var key = java.util.Arrays.asList(schema, name);
							Table table = result.get(key);
							if (table == null) {
								table = new Table(name);
								table.setSchemaName(schema);
								table.setRemarks(rs.getString("REMARKS"));
								pending.put(table, Db2HostTableAssembler.readTable(rs, table, platform));
								result.put(key, table);
							}
							// Partitioned tables return one row per partition.
							Db2HostTableAssembler.readPartition(rs, table, pending.get(table), platform);
						}
					});
			return new ArrayList<>(result.values());
		}

		@Override
		protected void setMetadataDetail(Connection connection, ParametersContext context, List<Table> tableList)
				throws SQLException {
			super.setMetadataDetail(connection, context, tableList);
			var platform = platform();
			for (Table table : tableList)
				Db2HostTableAssembler.complete(table, pending.remove(table), platform);
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
			return new Db2HostSupplementalReaders.Indexes(getDialect(), resources);
		}

		@Override
		protected CheckConstraintReader newCheckConstraintReader() {
			return new Db2HostSupplementalReaders.Checks(getDialect(), resources);
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
		protected void doGetAllAfter(Connection connection, List<Column> columns) {
			// Built-ins are resolved and DISTINCT references have explicit schema/name
			// identities from the column query. No all-domain/type lookup is needed.
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
							String udtSchema = identifier(rs, "UDT_SCHEMA"), udtName = identifier(rs, "UDT_NAME");
							boolean named = "DISTINCT".equals(type) || (udtSchema != null && !udtSchema.isBlank())
									|| (udtName != null && !udtName.isBlank());
							if (named) {
								if (udtSchema == null || udtSchema.isBlank() || udtName == null || udtName.isBlank()
										|| "ARRAY".equals(type))
									throw new SQLException(
											"Missing/unsupported host DISTINCT column identity: " + column.getName());
								column.setDataType(com.sqlapp.data.db.datatype.DataType.OTHER);
								column.setDomain(new Domain(udtName).setSchemaName(udtSchema));
							} else if (!getDialect().setDbType(type, rs.getLongValue("TYPE_LENGTH"),
									rs.getInteger("TYPE_SCALE"), column))
								throw new SQLException(
										"Unsupported Db2 host type " + type + " for " + column.getName());
							column.setNullable("Y".equals(rs.getString("NULLABLE")));
							column.setRemarks(rs.getString("REMARKS"));
							String unsupported = rs.getString("UNSUPPORTED_GENERATION");
							if (unsupported != null && !unsupported.isBlank())
								throw new SQLException("Unsupported generated Db2 host column: " + column.getName()
										+ " (" + unsupported + ")");
							String defaultSql = rs.getString("DEFAULT_SQL");
							if (named && "Y".equals(identifier(rs, "UDT_DEFAULT_CAST"))) {
								if (defaultSql == null || defaultSql.isBlank())
									throw new SQLException("Missing DISTINCT default literal: " + column.getName());
								defaultSql = com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(udtSchema,
										udtName) + "(" + defaultSql + ")";
							}
							column.setDefaultValue(defaultSql);
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
							readHostAttributes(rs, column, named);
							result.add(column);
						}
					});
			return result;
		}

		private void readHostAttributes(ExResultSet rs, Column column, boolean named) throws SQLException {
			var platform = ((com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect) getDialect()).getPlatform();
			String generation = Db2HostSupplementalReaders.optional(rs, "GENERATION_KIND");
			if (generation != null && !generation.isBlank()) {
				if (column.isIdentity() || column.getDefaultValue() != null)
					throw new SQLException(
							"Generated column cannot also be identity or have DEFAULT: " + column.getName());
				switch (generation) {
				case "ROW_BEGIN", "ROW_END", "TRANSACTION_START_ID" ->
					column.getSpecifics().put(Db2HostTables.GENERATION, generation);
				case "ROW_CHANGE_ALWAYS" -> column.getSpecifics().put(Db2HostTables.ROW_CHANGE, "A");
				case "ROW_CHANGE_DEFAULT" -> column.getSpecifics().put(Db2HostTables.ROW_CHANGE, "D");
				default ->
					throw new SQLException("Unsupported generated column kind " + generation + ": " + column.getName());
				}
			}
			String hidden = Db2HostSupplementalReaders.optional(rs, "HIDDEN_FLAG");
			if (hidden != null && !hidden.isBlank() && !"N".equals(hidden)) {
				if (!"Y".equals(hidden))
					throw new SQLException("Unsupported hidden column attribute: " + column.getName());
				column.getSpecifics().put(Db2HostTables.HIDDEN, "Y");
			}
			String period = Db2HostSupplementalReaders.optional(rs, "PERIOD_ROLE");
			if (period != null && !period.isBlank()) {
				if (!List.of("S", "T", "B", "C").contains(period))
					throw new SQLException("Unsupported period column role " + period + ": " + column.getName());
				column.getSpecifics().put(Db2HostTables.TMP_PERIOD, period);
			}
			if (Db2HostSupplementalReaders.has(rs, "PARTITION_KEY_SEQ")) {
				Integer seq = rs.getInteger("PARTITION_KEY_SEQ");
				if (seq != null && seq > 0) {
					String order = identifier(rs, "PARTITION_KEY_ORDER");
					if (!"A".equals(order) && !"D".equals(order))
						throw new SQLException("Unsupported partition key order " + order + ": " + column.getName());
					column.getSpecifics().put(Db2HostTables.TMP_PARTITION_KEY_SEQ, String.valueOf(seq));
					column.getSpecifics().put(Db2HostTables.TMP_PARTITION_KEY_ORDER, order);
				}
			}
			if (named)
				return;
			var type = column.getDataType();
			boolean fixedOrVarying = type == com.sqlapp.data.db.datatype.DataType.CHAR
					|| type == com.sqlapp.data.db.datatype.DataType.VARCHAR;
			boolean character = fixedOrVarying || type == com.sqlapp.data.db.datatype.DataType.CLOB;
			boolean graphic = type == com.sqlapp.data.db.datatype.DataType.NCHAR
					|| type == com.sqlapp.data.db.datatype.DataType.NVARCHAR
					|| type == com.sqlapp.data.db.datatype.DataType.NCLOB;
			String subtype = Db2HostSupplementalReaders.optional(rs, "CHARACTER_SUBTYPE");
			if (platform == com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect.Platform.ZOS && character
					&& subtype != null && !subtype.isBlank()) {
				switch (subtype) {
				case "B" -> {
					if (!fixedOrVarying)
						throw new SQLException("Unsupported FOR BIT DATA column type: " + column.getName());
					column.getSpecifics().put("CODEPAGE", "0");
				}
				case "S", "M" -> column.getSpecifics().put(Db2HostTables.SUBTYPE, subtype);
				default -> throw new SQLException("Unknown character subtype " + subtype + ": " + column.getName());
				}
			}
			if (platform == com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect.Platform.IBM_I
					&& (character || graphic) && Db2HostSupplementalReaders.has(rs, "COLUMN_CCSID")) {
				Integer ccsid = rs.getInteger("COLUMN_CCSID");
				if (ccsid != null && ccsid == 65535) {
					if (!fixedOrVarying)
						throw new SQLException("CCSID 65535 requires CHAR or VARCHAR: " + column.getName());
					column.getSpecifics().put("CODEPAGE", "0");
				} else if (ccsid != null && ccsid > 0)
					column.getSpecifics().put(Db2HostTables.CCSID, String.valueOf(ccsid));
			}
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
							String unsupported = Db2HostSupplementalReaders.optional(rs, "UNSUPPORTED_KEY");
							if (unsupported != null && !unsupported.isBlank())
								throw new SQLException(
										"Unsupported key attributes: " + unsupported + " (" + name + ")");
							var key = List.of(schema, table, name);
							var constraint = result.get(key);
							if (constraint == null) {
								constraint = new UniqueConstraint(name, "P".equals(rs.getString("KEY_TYPE")));
								constraint.setSchemaName(schema);
								constraint.setTableName(table);
								result.put(key, constraint);
							}
							String period = Db2HostSupplementalReaders.optional(rs, "KEY_PERIOD");
							if (period != null && !period.isBlank()) {
								// BUSINESS_TIME WITHOUT OVERLAPS stores the period's start (B) and
								// exclusive end (C) as trailing key columns.
								if (!"B".equals(period) && !"C".equals(period))
									throw new SQLException("Unsupported key period role " + period + ": " + name);
								constraint.getSpecifics().put(
										com.sqlapp.data.db.dialect.db2.common.Db2HostIndexes.BUSINESS_TIME_WITHOUT_OVERLAPS,
										"Y");
								return;
							}
							if (constraint.getSpecifics().get(
									com.sqlapp.data.db.dialect.db2.common.Db2HostIndexes.BUSINESS_TIME_WITHOUT_OVERLAPS) != null)
								throw new SQLException("Key column follows BUSINESS_TIME WITHOUT OVERLAPS: " + name);
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
