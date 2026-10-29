/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcIndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Reads index headers, columns and optional routine identities with bounded
 * catalog queries.
 */
public class InformixIndexReader extends JdbcIndexReader {
	public InformixIndexReader(final Dialect dialect) {
		super(dialect);
	}

	public static final String INFORMIX_CLUSTERED = "INFORMIX_CLUSTERED";
	public static final String INFORMIX_ACCESS_METHOD = "INFORMIX_ACCESS_METHOD";
	public static final String INFORMIX_ACCESS_METHOD_OWNER = "INFORMIX_ACCESS_METHOD_OWNER";
	public static final String INFORMIX_ACCESS_PARAMETERS = "INFORMIX_ACCESS_PARAMETERS";
	public static final String INFORMIX_OPERATOR_CLASS = "INFORMIX_OPERATOR_CLASS";
	public static final String INFORMIX_OPERATOR_CLASS_OWNER = "INFORMIX_OPERATOR_CLASS_OWNER";
	public static final String INFORMIX_INDEX_EXPRESSION = "INFORMIX_INDEX_EXPRESSION";

	record Metadata(Index index, String constraintName, int accessMethod) {
	}

	@Override
	protected List<Index> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		return readIndexes(connection, context, false).stream().map(Metadata::index).toList();
	}

	List<Metadata> readIndexes(final Connection connection, final ParametersContext context,
			final boolean uniqueConstraints) {
		final List<Metadata> result = new ArrayList<>();
		final List<InformixIndexColumns> keys = new ArrayList<>();
		final Set<Integer> routineIds = new LinkedHashSet<>();
		final Set<Integer> operatorClassIds = new LinkedHashSet<>();
		query(connection, uniqueConstraints ? "uniqueConstraints.sql" : "indexes.sql", context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(final ExResultSet rs) throws SQLException {
						final Index index = new Index(getString(rs, INDEX_NAME));
						index.setCatalogName(getString(rs, CATALOG_NAME));
						index.setSchemaName(getString(rs, SCHEMA_NAME));
						index.setTableName(getString(rs, TABLE_NAME));
						final String owner = getString(rs, "index_owner");
						if (owner != null && !owner.equals(index.getSchemaName()))
							index.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER, owner);
						index.setIndexType("C".equalsIgnoreCase(getString(rs, "is_clustered")) ? IndexType.Clustered
								: IndexType.BTree);
						index.setUnique("U".equalsIgnoreCase(getString(rs, "index_kind")));
						if (!uniqueConstraints)
							com.sqlapp.data.db.dialect.informix.util.InformixIndexModes.apply(index,
									getString(rs, "index_state"));
						final int accessMethod = rs.getInt("access_method");
						final String methodName = getString(rs, "access_method_name");
						final String methodOwner = getString(rs, "access_method_owner");
						final String parameters = getString(rs, "access_parameters");
						if (methodName == null || methodName.isBlank() || methodOwner == null
								|| methodOwner.isBlank()) {
							throw new IllegalStateException("Cannot resolve Informix index access method "
									+ accessMethod + " for " + index.getName());
						}
						if (accessMethod != 1 || parameters != null && !parameters.isBlank()) {
							index.getSpecifics().put(INFORMIX_ACCESS_METHOD, methodName);
							index.getSpecifics().put(INFORMIX_ACCESS_METHOD_OWNER, methodOwner);
							if (parameters != null && !parameters.isBlank())
								index.getSpecifics().put(INFORMIX_ACCESS_PARAMETERS, parameters.trim());
							if (index.getIndexType() != IndexType.Clustered)
								index.setIndexType(IndexType.Other);
						}
						final InformixIndexColumns columns = new InformixIndexColumns(getString(rs, "raw_index_keys"));
						columns.routineIds(routineIds);
						columns.operatorClassIds(operatorClassIds, accessMethod);
						keys.add(columns);
						result.add(new Metadata(index, uniqueConstraints ? getString(rs, CONSTRAINT_NAME) : null,
								accessMethod));
					}
				});
		if (result.isEmpty())
			return result;
		final Map<List<String>, Map<Integer, String>> columns = new LinkedHashMap<>();
		final ParametersContext selected = context.clone();
		selected.put("catalogName", result.stream().map(m -> m.index().getCatalogName()).distinct().toList());
		selected.put("schemaName", result.stream().map(m -> m.index().getSchemaName()).distinct().toList());
		selected.put("tableName", result.stream().map(m -> m.index().getTableName()).distinct().toList());
		query(connection, "indexColumns.sql", selected, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final List<String> table = List.of(getString(rs, CATALOG_NAME), getString(rs, SCHEMA_NAME),
						getString(rs, TABLE_NAME));
				columns.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(rs.getInt("column_number"),
						getString(rs, COLUMN_NAME));
			}
		});
		final Map<Integer, InformixIndexColumns.Routine> routines = new LinkedHashMap<>();
		if (!routineIds.isEmpty()) {
			final ParametersContext functions = context.clone();
			functions.put("routineIds", routineIds);
			query(connection, "indexRoutines.sql", functions, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs) throws SQLException {
					routines.put(rs.getInt("routine_id"), new InformixIndexColumns.Routine(
							getString(rs, "routine_schema"), getString(rs, "routine_name")));
				}
			});
		}
		final Map<Integer, InformixIndexColumns.OperatorClass> operatorClasses = new LinkedHashMap<>();
		if (!operatorClassIds.isEmpty()) {
			final ParametersContext classes = context.clone();
			classes.put("operatorClassIds", operatorClassIds);
			query(connection, "indexOperatorClasses.sql", classes, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs) throws SQLException {
					operatorClasses.put(rs.getInt("operator_class_id"),
							new InformixIndexColumns.OperatorClass(rs.getInt("access_method"),
									getString(rs, "operator_class_owner"), getString(rs, "operator_class_name")));
				}
			});
		}

		for (int i = 0; i < result.size(); i++) {
			final Index index = result.get(i).index();
			final List<String> table = List.of(index.getCatalogName(), index.getSchemaName(), index.getTableName());
			keys.get(i).apply(index, columns.getOrDefault(table, Map.of()), routines, operatorClasses,
					result.get(i).accessMethod(), getDialect().createSqlBuilder());
		}
		return result;
	}

	protected void query(final Connection connection, final String resource, final ParametersContext context,
			final ResultSetNextHandler handler) {
		execute(connection, getSqlNodeCache().getString(resource), context, handler);
	}
}
