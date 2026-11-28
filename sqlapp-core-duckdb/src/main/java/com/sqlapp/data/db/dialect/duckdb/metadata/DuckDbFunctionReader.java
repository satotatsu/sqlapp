/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbMacros;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.RoutineArgumentReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads DuckDB scalar and table macros from {@code duckdb_functions()} in a
 * single query.
 * <p>
 * All overloads belong to one catalog entry. DuckDB exposes neither parameter
 * defaults nor a flag identifying defaulted parameters. Parameterized macros
 * are therefore marked as incomplete rather than generating a lossy CREATE
 * definition. Supply the original definition or opt in to complete schema
 * snapshot/export extraction to recreate those macros.
 * </p>
 */
public class DuckDbFunctionReader extends FunctionReader {
	private boolean readCompleteDefinitions;

	/**
	 * Opt in to a temporary, data-free schema snapshot/export on an auto-commit
	 * connection.
	 */
	public void setReadCompleteDefinitions(final boolean value) {
		this.readCompleteDefinitions = value;
	}

	public boolean isReadCompleteDefinitions() {
		return readCompleteDefinitions;
	}

	/** Metadata cannot distinguish required parameters from defaulted ones. */
	public static final String INCOMPLETE_SIGNATURE = "duckdb.macro.incompleteSignature";
	/**
	 * Available overloads/bodies for inspection only; parameter defaults are
	 * missing.
	 */
	public static final String PARTIAL_DEFINITION = "duckdb.macro.partialDefinition";

	protected DuckDbFunctionReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Function> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("functions.sql");
		final List<Function> result = list();
		final List<MacroBuilder> builders = new ArrayList<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String catalog = getString(rs, "function_catalog");
				final String schema = getString(rs, "function_schema");
				final String name = getString(rs, FUNCTION_NAME);
				final boolean table = "table_macro".equals(getString(rs, "function_type"));
				MacroBuilder builder = builders.isEmpty() ? null : builders.get(builders.size() - 1);
				if (builder == null || !builder.matches(catalog, schema, name, table)) {
					builder = new MacroBuilder(catalog, schema, name, table, getString(rs, REMARKS));
					builders.add(builder);
				}
				final String key = getString(rs, "overload_key");
				DuckDbMacros.Overload overload = builder.overloads.isEmpty() ? null
						: builder.overloads.get(builder.overloads.size() - 1);
				if (overload == null || !Objects.equals(builder.lastKey, key)) {
					overload = new DuckDbMacros.Overload(new ArrayList<>(), new ArrayList<>(),
							getString(rs, "macro_definition"));
					builder.overloads.add(overload);
					builder.lastKey = key;
				}
				final String parameter = getString(rs, "parameter_name");
				if (parameter != null) {
					overload.parameterNames().add(parameter);
					overload.parameterTypes().add(getString(rs, "parameter_type"));
				}
			}
		});
		for (final MacroBuilder builder : builders) {
			result.add(builder.build());
		}
		markSharedNames(result);
		if (readCompleteDefinitions) {
			if (!(getDialect() instanceof com.sqlapp.data.db.dialect.duckdb.DuckDbVersions.DuckDb20))
				throw new UnsupportedOperationException("Complete macro extraction requires DuckDB 2.0+");
			final Map<String, Map<DuckDbMacroDefinitions.Key, String>> exports = new HashMap<>();
			for (final Function function : result) {
				if (!"true".equals(function.getSpecifics().get(INCOMPLETE_SIGNATURE)))
					continue;
				Map<DuckDbMacroDefinitions.Key, String> definitions = exports.get(function.getCatalogName());
				if (definitions == null) {
					try {
						definitions = DuckDbMacroDefinitions.read(connection, getDialect(), function.getCatalogName());
					} catch (SQLException e) {
						throw new IllegalStateException("Cannot recover complete DuckDB macro definitions", e);
					}
					exports.put(function.getCatalogName(), definitions);
				}
				final String definition = definitions.get(new DuckDbMacroDefinitions.Key(function.getSchemaName(),
						function.getName(), function.getFunctionType() == FunctionType.Table));
				if (definition == null)
					throw new IllegalStateException(
							"Complete DuckDB macro definition was not exported: " + function.getName());
				function.setDefinition(definition);
				function.getSpecifics().remove(INCOMPLETE_SIGNATURE);
				function.getSpecifics().remove(PARTIAL_DEFINITION);
			}
		}
		return result;
	}

	/**
	 * A scalar and a table macro may share a name; the table macro then gets a
	 * distinct specific name so both fit in one collection.
	 */
	private static void markSharedNames(final List<Function> functions) {
		for (final Function function : functions) {
			if (function.getFunctionType() != FunctionType.Table) {
				continue;
			}
			for (final Function other : functions) {
				if (other != function && other.getFunctionType() != FunctionType.Table
						&& Objects.equals(other.getCatalogName(), function.getCatalogName())
						&& Objects.equals(other.getSchemaName(), function.getSchemaName())
						&& Objects.equals(other.getName(), function.getName())) {
					function.setSpecificName(function.getName() + " TABLE");
				}
			}
		}
	}

	private final class MacroBuilder {
		private final String catalog;
		private final String schema;
		private final String name;
		private final boolean table;
		private final String remarks;
		private final List<DuckDbMacros.Overload> overloads = new ArrayList<>();
		private String lastKey;

		private MacroBuilder(final String catalog, final String schema, final String name, final boolean table,
				final String remarks) {
			this.catalog = catalog;
			this.schema = schema;
			this.name = name;
			this.table = table;
			this.remarks = remarks;
		}

		private boolean matches(final String catalog, final String schema, final String name, final boolean table) {
			return Objects.equals(this.catalog, catalog) && Objects.equals(this.schema, schema)
					&& Objects.equals(this.name, name) && this.table == table;
		}

		private Function build() {
			final Function function = new Function(name);
			function.setDialect(getDialect());
			function.setCatalogName(catalog);
			function.setSchemaName(schema);
			function.setRemarks(remarks);
			function.setFunctionType(table ? FunctionType.Table : FunctionType.Scalar);
			if (overloads.size() == 1) {
				final DuckDbMacros.Overload overload = overloads.get(0);
				for (int i = 0; i < overload.parameterNames().size(); i++) {
					final NamedArgument argument = new NamedArgument(overload.parameterNames().get(i));
					final String type = overload.parameterTypes().get(i);
					if (type != null) {
						argument.setDataTypeName(type);
					}
					function.getArguments().add(argument);
				}
				function.setStatement(overload.body());
			}
			if (overloads.stream().anyMatch(overload -> !overload.parameterNames().isEmpty())) {
				function.getSpecifics().put(INCOMPLETE_SIGNATURE, "true");
				function.getSpecifics().put(PARTIAL_DEFINITION,
						DuckDbMacros.createMacro(getDialect(), catalog, schema, name, table, overloads));
			} else {
				function.setDefinition(DuckDbMacros.createMacro(getDialect(), catalog, schema, name, table, overloads));
			}
			return function;
		}
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}
}
