/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;

/**
 * Dialect resolver for SQL Server
 * 
 * @author satoh
 * 
 */
public class SqlServerDialectResolver extends ProductNameDialectResolver {

	public SqlServerDialectResolver() {
		super("(Microsoft *SQL *Server.*|MS *SQL)", new SqlServerVersionResolver());
	}

	public static final SqlServerDialectResolver instance = new SqlServerDialectResolver();

	public static SqlServerDialectResolver getInstance() {
		return instance;
	}

	/**
	 * SQL Server 2016 SP1のビルド番号
	 */
	static final int SQL_SERVER_2016_SP1_BUILD = 4001;

	/**
	 * Azure SQL Database / Azure SQL Managed Instanceが返すメジャーバージョン
	 */
	private static final int AZURE_REPORTED_MAJOR_VERSION = 12;

	/**
	 * SERVERPROPERTY('EngineEdition'): Azure SQL Database
	 */
	static final int ENGINE_EDITION_AZURE_SQL_DATABASE = 5;

	/**
	 * SERVERPROPERTY('EngineEdition'): Azure SQL Managed Instance
	 */
	static final int ENGINE_EDITION_AZURE_SQL_MANAGED_INSTANCE = 8;

	/**
	 * Azure SQL Database / Managed Instanceは製品バージョンとして常に12.xを返すため、
	 * EngineEditionで判定して最新のDialectを返します。 それ以外はnullを返し、バージョンによる判定に委ねます。
	 */
	@Override
	public Dialect resolveDatabaseMetaData(final DatabaseMetaData metadata) {
		try {
			if (!match(metadata.getDatabaseProductName())) {
				return null;
			}
			if (metadata.getDatabaseMajorVersion() != AZURE_REPORTED_MAJOR_VERSION) {
				return null;
			}
			final var connection = metadata.getConnection();
			if (connection == null) {
				// オフライン解決ではEngineEditionを確認できないため、バージョンで判定する
				return null;
			}
			try (var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT CAST(SERVERPROPERTY('EngineEdition') AS INT)")) {
				if (!rows.next()) {
					throw new IllegalStateException("SERVERPROPERTY('EngineEdition') returned no row");
				}
				final int engineEdition = rows.getInt(1);
				if (engineEdition == ENGINE_EDITION_AZURE_SQL_DATABASE
						|| engineEdition == ENGINE_EDITION_AZURE_SQL_MANAGED_INSTANCE) {
					return DialectHolder.defaultDialect2022;
				}
				return null;
			}
		} catch (final SQLException e) {
			throw new IllegalStateException("Failed to identify SQL Server engine edition from JDBC metadata", e);
		}
	}

	static class SqlServerVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * コンストラクタ
		 */
		public SqlServerVersionResolver() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sqlapp.data.db.dialect.resolver.VersionResolver#getDialect(int, int,
		 * java.lang.Integer)
		 */
		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion, final Integer revision) {
			if (majorVersion >= 16) {
				return DialectHolder.defaultDialect2022;
			} else if (majorVersion >= 15) {
				return DialectHolder.defaultDialect2019;
			} else if (majorVersion >= 14) {
				return DialectHolder.defaultDialect2017;
			} else if (majorVersion >= 13) {
				// SQL Server 2016 SP1 is build 13.0.4001. The minor version is always 0.
				if (revision != null && revision.intValue() >= SQL_SERVER_2016_SP1_BUILD) {
					return DialectHolder.defaultDialect2016Sp1;
				}
				return DialectHolder.defaultDialect2016;
			} else if (majorVersion >= 12) {
				return DialectHolder.defaultDialect2014;
			} else if (majorVersion >= 11) {
				return DialectHolder.defaultDialect2012;
			} else if (majorVersion >= 10) {
				if (minorVersion >= 50) {
					return DialectHolder.defaultDialect2008R2;
				}
				return DialectHolder.defaultDialect2008;
			} else if (majorVersion >= 9) {
				return DialectHolder.defaultDialect2005;
			} else if (majorVersion >= 8) {
				return DialectHolder.defaultDialect2000;
			}
			return DialectHolder.defaultDialect2000;
		}
	}

}
