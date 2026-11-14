/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.driver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Well-known JDBC drivers that are published to Maven Central and not bundled
 * with sqlapp. Any other driver can be downloaded by its Maven coordinate.
 */
public final class JdbcDriverCatalog {
	private JdbcDriverCatalog() {
	}

	/**
	 * @param name        display name
	 * @param groupId     Maven group
	 * @param artifactId  Maven artifact
	 * @param driverClass JDBC driver class
	 * @param urlPrefix   JDBC URL prefix the driver accepts, e.g.
	 *                    {@code jdbc:oracle:}
	 */
	public record Entry(String name, String groupId, String artifactId, String driverClass, String urlPrefix) {
		public MavenCoordinate coordinate(final String version) {
			return new MavenCoordinate(groupId, artifactId, version);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private static final List<Entry> ENTRIES = List
			.of(new Entry("Oracle", "com.oracle.database.jdbc", "ojdbc11", "oracle.jdbc.OracleDriver", "jdbc:oracle:"),
					new Entry("DuckDB", "org.duckdb", "duckdb_jdbc", "org.duckdb.DuckDBDriver", "jdbc:duckdb:"),
					new Entry("MariaDB", "org.mariadb.jdbc", "mariadb-java-client", "org.mariadb.jdbc.Driver",
							"jdbc:mariadb:"),
					new Entry("H2", "com.h2database", "h2", "org.h2.Driver", "jdbc:h2:"),
					new Entry("IBM Db2", "com.ibm.db2", "jcc", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2:"),
					new Entry("Firebird", "org.firebirdsql.jdbc", "jaybird", "org.firebirdsql.jdbc.FBDriver",
							"jdbc:firebirdsql:"),
					new Entry("Snowflake", "net.snowflake", "snowflake-jdbc",
							"net.snowflake.client.jdbc.SnowflakeDriver", "jdbc:snowflake:"));

	public static List<Entry> entries() {
		return ENTRIES;
	}

	/** The catalog driver for a JDBC URL, if any. */
	public static Optional<Entry> forUrl(final String jdbcUrl) {
		if (jdbcUrl == null)
			return Optional.empty();
		final String url = jdbcUrl.trim().toLowerCase(Locale.ROOT);
		return ENTRIES.stream().filter(entry -> url.startsWith(entry.urlPrefix())).findFirst();
	}

	/** The catalog driver with this Maven module, if any. */
	public static Optional<Entry> forModule(final String groupId, final String artifactId) {
		return ENTRIES.stream()
				.filter(entry -> entry.groupId().equals(groupId) && entry.artifactId().equals(artifactId)).findFirst();
	}
}
