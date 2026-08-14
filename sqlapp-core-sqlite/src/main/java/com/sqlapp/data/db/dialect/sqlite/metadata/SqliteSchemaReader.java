/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcSchemaReader;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.db.metadata.FunctionReader;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.SynonymReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Schema;

/** Reads SQLite databases exposed as schemas by {@code PRAGMA database_list}. */
public class SqliteSchemaReader extends JdbcSchemaReader {
	public SqliteSchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new SqliteTableReader(getDialect());
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

	@Override
	protected List<Schema> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Schema> result = list();
		final String requestedSchema = getSchemaName(context);
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery("PRAGMA database_list")) {
			while (resultSet.next()) {
				final String schemaName = resultSet.getString("name");
				if (requestedSchema == null
						|| requestedSchema.equalsIgnoreCase(schemaName)) {
					result.add(new Schema(schemaName));
				}
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getCurrentSchemaName(final Connection connection) {
		return "main";
	}
}
