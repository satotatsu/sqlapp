/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;

/** Temporary vendor read settings for a SQLapp-owned statement, restored after execution. */
@FunctionalInterface
public interface JdbcReadScope extends AutoCloseable {
	JdbcReadScope NONE = () -> {};
	@Override void close() throws SQLException;
	static JdbcReadScope prepare(PreparedStatement statement, Table table, Dialect dialect) throws SQLException {
		var scopes = new ArrayList<JdbcReadScope>();
		var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<JdbcTypeHandler, Boolean>());
		try {
			for (var column : table.getColumns()) {
				var type = dialect.getDbDataType(column);
				if (type != null && seen.add(type.getJdbcTypeHandler())) scopes.add(type.getJdbcTypeHandler().prepareRead(statement));
			}
		} catch (SQLException | RuntimeException | Error failure) {
			try { closeAll(scopes); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
			throw failure;
		}
		return () -> closeAll(scopes);
	}
	private static void closeAll(List<JdbcReadScope> scopes) throws SQLException {
		SQLException failure = null;
		for (var scope : scopes.reversed()) {
			try { scope.close(); } catch (SQLException problem) {
				if (failure == null) failure = problem; else failure.addSuppressed(problem);
			}
		}
		if (failure != null) throw failure;
	}
}
