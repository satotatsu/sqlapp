/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import java.math.BigInteger;
import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.hsql.Hsql;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounterProvider;

/**
 * HSQLDB raises an identity past explicitly inserted larger keys by itself, so
 * nothing needs to be synchronized.
 */
public class HsqlGeneratedKeyCounterProvider implements GeneratedKeyCounterProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Hsql;
	}

	@Override
	public boolean followsInsertedKeys() {
		return true;
	}

	@Override
	public boolean hasGenerator(final Connection connection, final Dialect dialect, final Table table,
			final Column column) {
		return true;
	}

	@Override
	public BigInteger currentNext(final Connection connection, final Dialect dialect, final Table table,
			final Column column) {
		return null;
	}

	@Override
	public List<String> restart(final Connection connection, final Dialect dialect, final Table table,
			final Column column, final BigInteger next) {
		return List.of();
	}
}
