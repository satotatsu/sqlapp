/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounterProvider;
import com.sqlapp.data.db.dialect.mysql.MySql;

/**
 * MySQL and MariaDB raise AUTO_INCREMENT past explicitly inserted keys by
 * themselves, so nothing needs to be synchronized.
 */
public class MySqlGeneratedKeyCounterProvider implements GeneratedKeyCounterProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof MySql;
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
