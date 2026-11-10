/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.ServiceLoader;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;

/** Dialect-owned transaction/locking boundary for staged, DELETE-based replacement. */
public interface AtomicTableReplacementProvider {
	boolean supports(Dialect dialect);
	void validate(Connection connection) throws SQLException;
	/** Check existing relations for behavior that would escape the replacement scope. */
	void validateTables(Connection connection, List<Table> tables) throws SQLException;
	List<String> lockSql(Dialect dialect, List<Table> tables);

	static AtomicTableReplacementProvider resolve(Dialect dialect) {
		var providers = ServiceLoader.load(AtomicTableReplacementProvider.class).stream()
				.map(ServiceLoader.Provider::get).filter(provider -> provider.supports(dialect)).toList();
		if (providers.size() != 1)
			throw new IllegalArgumentException("Staged atomic replacement requires exactly one supported transaction provider for " + dialect.getProductName());
		return providers.getFirst();
	}
}
