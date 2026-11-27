/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.resolver;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.Oracle;
import com.sqlapp.data.db.dialect.oracle.OracleMaxStringSize;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.AbstractConnection;
import com.sqlapp.util.DbUtils;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;

/**
 * Dialect resolver for Oracle
 * 
 * @author satoh
 * 
 */
public class OracleDialectResolver extends ProductNameDialectResolver {

	public OracleDialectResolver() {
		super("Oracle.*", new OracleVersionResolver());
	}

	public ProductNameDialectResolver[] getRelatedProducts() {
		return new ProductNameDialectResolver[] { new TimesTenDialectResolver() };
	}

	/**
	 * MAX_STRING_SIZEを判定するSQL。STANDARDではORA-00910になる。権限は不要。
	 */
	static final String MAX_STRING_SIZE_PROBE_SQL = "SELECT CAST(NULL AS VARCHAR2(32767)) FROM DUAL";

	/**
	 * ORA-00910: specified length too long for its datatype
	 */
	static final int ORA_LENGTH_TOO_LONG = 910;

	/**
	 * 接続先URLごとのMAX_STRING_SIZEの判定結果。MAX_STRING_SIZEの変更にはDBの再起動を伴うためキャッシュする。
	 */
	private static final Map<String, OracleMaxStringSize> DETECTED_MAX_STRING_SIZES = new ConcurrentHashMap<>();

	/**
	 * 12c以降はMAX_STRING_SIZEを考慮してDialectを返します。Dialectオプション{@value OracleMaxStringSize#OPTION_NAME}
	 * が指定されていればその値を、なければ接続先から判定した値を使用します。
	 */
	@Override
	public Dialect resolveDatabaseMetaData(final DatabaseMetaData metadata, final Map<String, String> dialectOptions) {
		final String productName;
		try {
			productName = metadata.getDatabaseProductName();
		} catch (final SQLException e) {
			throw new IllegalStateException("Cannot read the database product name", e);
		}
		if (!match(productName)) {
			return null;
		}
		final OracleMaxStringSize explicit = getMaxStringSize(dialectOptions);
		final ProductVersionInfo version = DbUtils.getProductVersionInfo(metadata);
		if (version.getMajorVersion() == null || version.getMinorVersion() == null) {
			return null;
		}
		final Dialect dialect = getDialect(version.getMajorVersion(), version.getMinorVersion(), version.getRevision());
		if (!(dialect instanceof Oracle) || !((Oracle) dialect).supportsExtendedMaxStringSize()) {
			return applyDialectOptions(dialect, dialectOptions);
		}
		final OracleMaxStringSize size = explicit != null ? explicit : detectMaxStringSize(metadata);
		return DialectHolder.getDialect(dialect, size);
	}

	/**
	 * Dialectオプション{@value OracleMaxStringSize#OPTION_NAME}(STANDARD/EXTENDED)を適用します。
	 */
	@Override
	public Dialect applyDialectOptions(final Dialect dialect, final Map<String, String> dialectOptions) {
		final OracleMaxStringSize size = getMaxStringSize(dialectOptions);
		if (size == null || !(dialect instanceof Oracle)) {
			return dialect;
		}
		if (size == OracleMaxStringSize.STANDARD && !((Oracle) dialect).supportsExtendedMaxStringSize()) {
			return dialect;
		}
		return DialectHolder.getDialect(dialect, size);
	}

	/**
	 * Dialectオプションから{@value OracleMaxStringSize#OPTION_NAME}を取得します。
	 * 
	 * @return 指定がなければnull
	 * @throws IllegalArgumentException Oracleで使用できないオプションや不正な値の場合
	 */
	static OracleMaxStringSize getMaxStringSize(final Map<String, String> dialectOptions) {
		if (dialectOptions == null || dialectOptions.isEmpty()) {
			return null;
		}
		OracleMaxStringSize result = null;
		for (final Map.Entry<String, String> entry : dialectOptions.entrySet()) {
			if (!OracleMaxStringSize.OPTION_NAME.equalsIgnoreCase(entry.getKey())) {
				throw new IllegalArgumentException("Unsupported dialect option for Oracle: " + entry.getKey()
						+ ". Supported options: " + OracleMaxStringSize.OPTION_NAME);
			}
			result = OracleMaxStringSize.parse(entry.getValue());
		}
		return result;
	}

	/**
	 * 接続先のMAX_STRING_SIZEを判定します。接続がない場合はSTANDARD。
	 */
	static OracleMaxStringSize detectMaxStringSize(final DatabaseMetaData metadata) {
		try {
			Connection connection = metadata.getConnection();
			if (connection == null) {
				return OracleMaxStringSize.STANDARD;
			}
			if (connection instanceof AbstractConnection) {
				// 判定SQLのエラーをSQLログに出さない
				connection = ((AbstractConnection) connection).getNativeObject();
			}
			final String url = metadata.getURL();
			if (url != null) {
				final OracleMaxStringSize cached = DETECTED_MAX_STRING_SIZES.get(cacheKey(url));
				if (cached != null) {
					return cached;
				}
			}
			final OracleMaxStringSize size = probeMaxStringSize(connection);
			if (url != null) {
				DETECTED_MAX_STRING_SIZES.put(cacheKey(url), size);
			}
			return size;
		} catch (final SQLException e) {
			throw new IllegalStateException("Cannot determine Oracle MAX_STRING_SIZE. Specify the dialect option "
					+ OracleMaxStringSize.OPTION_NAME + " (STANDARD or EXTENDED).", e);
		}
	}

	static OracleMaxStringSize probeMaxStringSize(final Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(MAX_STRING_SIZE_PROBE_SQL)) {
			return OracleMaxStringSize.EXTENDED;
		} catch (final SQLException e) {
			if (e.getErrorCode() == ORA_LENGTH_TOO_LONG) {
				return OracleMaxStringSize.STANDARD;
			}
			throw e;
		}
	}

	private static String cacheKey(final String url) {
		return url.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * MAX_STRING_SIZEの判定結果のキャッシュを消去します。
	 */
	public static void clearDetectedMaxStringSizes() {
		DETECTED_MAX_STRING_SIZES.clear();
	}

	/**
	 * Oracle用のバージョンResolver
	 * 
	 * @author satoh
	 * 
	 */
	static class OracleVersionResolver implements VersionResolver {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * コンストラクタ
		 */
		public OracleVersionResolver() {
		}

		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion, final Integer revision) {
			if (majorVersion >= 26 || majorVersion == 23 && minorVersion >= 26) {
				return DialectHolder.oracle26aiDialect;
			} else if (majorVersion >= 23) {
				return DialectHolder.oracle23aiDialect;
			} else if (majorVersion >= 21) {
				return DialectHolder.oracle21cDialect;
			} else if (majorVersion >= 19) {
				return DialectHolder.oracle19cDialect;
			} else if (majorVersion >= 18) {
				return DialectHolder.oracle18cDialect;
			} else if (majorVersion >= 12) {
				return DialectHolder.oracle12cDialect;
			} else if (majorVersion >= 11) {
				if (minorVersion > 1) {
					return DialectHolder.oracle11gR2Dialect;
				} else {
					return DialectHolder.oracle11gDialect;
				}
			} else if (majorVersion >= 10) {
				return DialectHolder.oracle10gDialect;
			}
			return DialectHolder.defaultDialect;
		}
	}
}
