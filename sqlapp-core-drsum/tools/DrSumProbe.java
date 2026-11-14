/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Read-only probe that records the Dr.Sum facts the experimental dialect had
 * to assume. It runs SELECT statements and DatabaseMetaData calls only; it
 * never creates, changes or deletes anything.
 *
 * <pre>
 * java -cp dwodsjd4.jar DrSumProbe.java "jdbc:drsum://host:6001;database=SALES" user password [table]
 * </pre>
 *
 * Send the output (after removing anything confidential) so the dialect's
 * assumptions can be replaced by verified behavior.
 */
public class DrSumProbe {

	private static final int SAMPLE_ROWS = 40;

	public static void main(final String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("usage: DrSumProbe <jdbc-url> <user> <password> [table-name]");
			System.exit(2);
		}
		Class.forName("jp.co.dw_sapporo.JDBC.JDBCDriver");
		try (Connection connection = DriverManager.getConnection(args[0], args[1], args[2])) {
			metadata(connection.getMetaData());
			final String table = args.length > 3 ? args[3] : null;
			final String filter = table == null ? "" : " WHERE table_name = '" + table.replace("'", "''") + "'";
			query(connection, "SELECT * FROM __all_tables__" + filter);
			query(connection, "SELECT * FROM __all_indexes__" + filter);
			query(connection, "SELECT * FROM __all_join_indexes__" + filter);
			query(connection, "SELECT * FROM __all_views__" + filter);
			if (table != null) {
				jdbcColumns(connection.getMetaData(), table);
			}
		}
	}

	private static void metadata(final DatabaseMetaData md) throws SQLException {
		System.out.println("## DatabaseMetaData");
		System.out.println("productName=" + md.getDatabaseProductName());
		System.out.println("productVersion=" + md.getDatabaseProductVersion());
		System.out.println("majorVersion=" + safe(() -> md.getDatabaseMajorVersion()));
		System.out.println("minorVersion=" + safe(() -> md.getDatabaseMinorVersion()));
		System.out.println("driver=" + md.getDriverName() + " " + md.getDriverVersion());
		System.out.println("identifierQuote=" + safe(md::getIdentifierQuoteString));
		System.out.println("storesMixedCaseIdentifiers=" + safe(md::storesMixedCaseIdentifiers));
		System.out.println("supportsMixedCaseIdentifiers=" + safe(md::supportsMixedCaseIdentifiers));
		System.out.println("supportsTransactions=" + safe(md::supportsTransactions));
		System.out.println("dataDefinitionCausesTransactionCommit="
				+ safe(md::dataDefinitionCausesTransactionCommit));
		System.out.println("tableTypes:");
		try (ResultSet rs = md.getTableTypes()) {
			dump(rs);
		} catch (final SQLException e) {
			System.out.println("  unsupported: " + e);
		}
		System.out.println("typeInfo:");
		try (ResultSet rs = md.getTypeInfo()) {
			dump(rs);
		} catch (final SQLException e) {
			System.out.println("  unsupported: " + e);
		}
	}

	private static void jdbcColumns(final DatabaseMetaData md, final String table) {
		System.out.println("## DatabaseMetaData.getColumns(" + table + ")");
		try (ResultSet rs = md.getColumns(null, null, table, "%")) {
			dump(rs);
		} catch (final SQLException e) {
			System.out.println("  unsupported: " + e);
		}
	}

	private static void query(final Connection connection, final String sql) {
		System.out.println("## " + sql);
		try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
			dump(rs);
		} catch (final SQLException e) {
			System.out.println("  failed: " + e);
		}
	}

	private static void dump(final ResultSet rs) throws SQLException {
		final ResultSetMetaData md = rs.getMetaData();
		final StringBuilder header = new StringBuilder("  ");
		for (int i = 1; i <= md.getColumnCount(); i++) {
			header.append(i > 1 ? " | " : "").append(md.getColumnLabel(i)).append(':')
					.append(md.getColumnTypeName(i));
		}
		System.out.println(header);
		int count = 0;
		while (rs.next() && count++ < SAMPLE_ROWS) {
			final StringBuilder line = new StringBuilder("  ");
			for (int i = 1; i <= md.getColumnCount(); i++) {
				final Object value = rs.getObject(i);
				line.append(i > 1 ? " | " : "").append(value == null ? "<NULL>" : "'" + value + "'");
			}
			System.out.println(line);
		}
	}

	private interface SqlSupplier {
		Object get() throws SQLException;
	}

	private static Object safe(final SqlSupplier supplier) {
		try {
			return supplier.get();
		} catch (final SQLException | RuntimeException | AbstractMethodError e) {
			return "unsupported: " + e;
		}
	}
}
