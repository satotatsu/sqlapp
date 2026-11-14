/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DomainReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads named DuckDB ENUM types ({@code CREATE TYPE ... AS ENUM}) as domains
 * with {@link DataType#ENUM} and their labels in declaration order.
 */
public class DuckDbEnumReader extends DomainReader {

	protected DuckDbEnumReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("enums.sql");
		final List<Domain> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String catalog = getString(rs, "domain_catalog");
				final String schema = getString(rs, "domain_schema");
				final String name = getString(rs, DOMAIN_NAME);
				Domain domain = result.isEmpty() ? null : result.get(result.size() - 1);
				if (domain == null || !Objects.equals(domain.getCatalogName(), catalog)
						|| !Objects.equals(domain.getSchemaName(), schema) || !Objects.equals(domain.getName(), name)) {
					domain = new Domain(name);
					domain.setDialect(getDialect());
					domain.setCatalogName(catalog);
					domain.setSchemaName(schema);
					domain.setRemarks(getString(rs, REMARKS));
					domain.setDataType(DataType.ENUM);
					result.add(domain);
				}
				domain.getValues().add(getString(rs, "label"));
			}
		});
		return result;
	}
}
