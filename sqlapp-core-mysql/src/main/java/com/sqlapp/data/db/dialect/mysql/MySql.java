/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql;

import static com.sqlapp.util.CommonUtils.LEN_16MB;
import static com.sqlapp.util.CommonUtils.LEN_4GB;
import static com.sqlapp.util.CommonUtils.LEN_64KB;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.dialect.DefaultCase;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.db.datatype.util.MysqlNumberColumnTypeMatcher;
import com.sqlapp.data.db.dialect.mysql.db.datatype.util.MysqlUnsignedNumberColumnTypeMatcher;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlCatalogReader;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlSplitter;
import com.sqlapp.data.db.dialect.util.GeometryUtils;
import com.sqlapp.data.db.dialect.util.SqlTerminator;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;

/**
 * MySql
 * 
 * @author SATOH
 * 
 */
public class MySql extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3303539585921104825L;

	protected MySql(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(255);
		// VARCHAR
		getDbDataTypes().addVarchar(65535);
		// LONGVARCHAR
		getDbDataTypes().addLongVarchar("TINYTEXT", 255, type -> {
			type.setCreateFormat("TINYTEXT");
		});
		getDbDataTypes().addLongVarchar("TEXT", LEN_64KB - 1, type -> {
			type.setCreateFormat("TEXT");
		});
		getDbDataTypes().addLongVarchar("MEDIUMTEXT", LEN_16MB, type -> {
			type.setCreateFormat("MEDIUMTEXT");
		});
		getDbDataTypes().addLongVarchar("LONGTEXT", LEN_4GB, type -> {
			type.setCreateFormat("LONGTEXT");
		});
		// BINARY
		getDbDataTypes().addBinary(255, type -> {
		});
		// VARBINARY
		getDbDataTypes().addVarBinary(65535, type -> {
		});
		// BLOB
		getDbDataTypes().addBlob("TINYBLOB", 255, type -> {
			type.setCreateFormat("BLOB");
		});
		getDbDataTypes().addBlob("BLOB", LEN_64KB - 1, type -> {
		});
		getDbDataTypes().addBlob("MEDIUMBLOB", LEN_16MB - 1, type -> {
			type.setCreateFormat("MEDIUMBLOB");
		});
		getDbDataTypes().addBlob("LONGBLOB", LEN_4GB - 1, type -> {
			type.setCreateFormat("LONGBLOB");
		});
		getDbDataTypes().addBit("BIT", 64, type -> {
			type.setLiteral("b'", "'").setDefaultValueLiteral("b'0'");
		});

		// Int8
		getDbDataTypes().addTinyInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("TINYINT"));
		});
		// Int16
		getDbDataTypes().addSmallInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("SMALLINT"));
		});
		// Int24
		getDbDataTypes().addMediumInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("MEDIUMINT"));
		});
		// Int32
		getDbDataTypes().addInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("INT", "INTEGER"));
		});
		// Int64
		getDbDataTypes().addBigInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("BIGINT"));
		});
		// UTINYINT
		getDbDataTypes().addUTinyInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("UTINYINT"));
			type.addColumnTypeMatcher(createUnsignedNumberColumnTypeMatcher("TINYINT"));
		});
		// UInt16
		getDbDataTypes().addUSmallInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("USMALLINT"));
			type.addColumnTypeMatcher(createUnsignedNumberColumnTypeMatcher("SMALLINT"));
		});
		// UInt24
		getDbDataTypes().addUMediumInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("UMEDIUMINT"));
			type.addColumnTypeMatcher(createUnsignedNumberColumnTypeMatcher("MEDIUMINT"));
		});
		// UInt32
		getDbDataTypes().addUInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("UINT", "UINTEGER"));
			type.addColumnTypeMatcher(createUnsignedNumberColumnTypeMatcher("INT", "INTEGER"));
			type.addColumnTypeMatcher(createUnsignedNumberColumnTypeMatcher("INTEGER"));
		});
		// UInt64
		getDbDataTypes().addUBigInt(type -> {
			type.clearColumnTypeMatchers();
			type.addColumnTypeMatcher(createNumberColumnTypeMatcher("UBIGINT"));
			type.addColumnTypeMatcher(createUnsignedNumberColumnTypeMatcher("BIGINT"));
		});
		// GUID
		getDbDataTypes().addUUID("UUID", type -> {
			type.setAsBinaryType();
		});
		// Single
		getDbDataTypes().addReal("FLOAT", type -> {
		});
		// Single
		getDbDataTypes().addFloat(53);
		// Double
		getDbDataTypes().addDouble(type -> {
		});
		// Date
		getDbDataTypes().addDate(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentDateFunction());
		});
		// DateTime
		getDbDataTypes().addTimestamp("DATETIME", type -> {
			type.setLiteral("'", "'").setCreateFormat("DATETIME").setDefaultValueLiteral(getCurrentDateTimeFunction())
					.setFixedPrecision(false).setConverter(Converters.getDefault().getConverter(java.util.Date.class));
		});
		// Time
		getDbDataTypes().addTime(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentTimeFunction()).setFixedPrecision(false);
		});
		// Timestamp
		getDbDataTypes().addTimestampVersion("TIMESTAMP", type -> {
			type.setLiteral("'", "'").setCreateFormat("TIMESTAMP")
					.setDefaultValueLiteral(getCurrentTimestampFunction());
		});
		// Decimal
		getDbDataTypes().addDecimal(type -> {
			type.setMaxPrecision(65).setMaxScale(30);
		});
		// Numeric
		getDbDataTypes().addNumeric(type -> {
			type.setMaxPrecision(65).setMaxScale(30);
		});
		GeometryUtils.run(new Runnable() {
			@Override
			public void run() {
				// GEOMETRY
				getDbDataTypes().addGeometry(type -> {
					type.setJdbcTypeHandler(new MySqlGeometryJdbcTypeHandler()).setJdbcType(java.sql.JDBCType.ARRAY);
				});
			}
		});
		// ENUM
		getDbDataTypes().addEnum();
		// SET
		getDbDataTypes().addSet();
	}

	protected ColumnTypeMatcher createNumberColumnTypeMatcher(String... dataTypeName) {
		return new MysqlNumberColumnTypeMatcher(dataTypeName);
	}

	protected ColumnTypeMatcher createUnsignedNumberColumnTypeMatcher(String... dataTypeName) {
		return new MysqlUnsignedNumberColumnTypeMatcher(dataTypeName);
	}

	/**
	 * DB名
	 */
	@Override
	public String getProductName() {
		return "MySQL";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "mysql";
	}

	@Override
	public String getIdentitySelectString() {
		return "SELECT LAST_INSERT_ID()";
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	@Override
	public String getIdentityColumnString() {
		return "NOT NULL AUTO_INCREMENT";
	}

	@Override
	public char getCloseQuote() {
		return '`';
	}

	@Override
	public char getOpenQuote() {
		return '`';
	}

	/**
	 * LIMIT句のサポート
	 */
	@Override
	public boolean supportsLimit() {
		return true;
	}

	/**
	 * Offset句のサポート
	 */
	@Override
	public boolean supportsLimitOffset() {
		return true;
	}

	@Override
	public boolean supportsCascadeDelete() {
		return true;
	}

	@Override
	public String getCurrentDateFunction() {
		return "current_date";
	}

	/**
	 * 現在日時の取得関数
	 */
	@Override
	public String getCurrentTimeFunction() {
		return "current_time";
	}

	/**
	 * 現在日時の取得関数
	 */
	@Override
	public String getCurrentDateTimeFunction() {
		return "current_timestamp";
	}

	/**
	 * 現在日時(Timestamp)の取得関数
	 */
	@Override
	public String getCurrentTimestampFunction() {
		return "current_timestamp";
	}

	/**
	 * 現在日時(Timestamp)タイムゾーン付きの取得関数
	 */
	@Override
	public String getCurrentTimestampWithTimeZoneFunction() {
		return null;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return false;
	}

	@Override
	public boolean supportsRuleOnDelete(final CascadeRule rule) {
		if (rule == CascadeRule.None || rule == CascadeRule.SetNull || rule == CascadeRule.Cascade) {
			return true;
		}
		return false;
	}

	@Override
	public boolean supportsCascadeUpdate() {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(final CascadeRule rule) {
		if (rule == CascadeRule.None || rule == CascadeRule.SetNull || rule == CascadeRule.Cascade) {
			return true;
		}
		return false;
	}

	/**
	 * インデックス名のテーブルスコープ
	 */
	@Override
	public boolean supportsIndexNameTableScope() {
		return true;
	}

	/**
	 * executeBatch実行時に生成されたキーを戻すことが出来るか?
	 */
	@Override
	public boolean supportsBatchExecuteGeneratedKeys() {
		return true;
	}

	/**
	 * 入力された文字をDBの既定の文字に変換
	 * 
	 */
	@Override
	public String nativeCaseString(final String value) {
		if (isEmpty(value)) {
			return value;
		}
		if (isQuoted(value)) {
			return value;
		}
		return value.toLowerCase();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new MySqlCatalogReader(this);
	}

	@Override
	public int hashCode() {
		return getProductName().hashCode();
	}

	/**
	 * 同値判定
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}

	/**
	 * DBが入力された文字を大文字、小文字で扱う方法
	 * 
	 */
	@Override
	public DefaultCase getDefaultCase() {
		return DefaultCase.LowerCase;
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new MySqlSqlFactoryRegistry(this);
	}

	@Override
	public MySqlSqlBuilder createSqlBuilder() {
		return new MySqlSqlBuilder(this);
	}

	@Override
	public MySqlSqlSplitter createSqlSplitter() {
		return new MySqlSqlSplitter(this);
	}

	private static String[] DELIMITERS = new String[] { "@", "$", "%", "/", "!" };

	@Override
	public void setChangeAndResetSqlDelimiter(final String sql, final SqlTerminator sqlTerminator) {
		if (!sql.contains(";")) {
			return;
		}
		final String del = getDelimiter(sql, DELIMITERS);
		sqlTerminator.setStartStatementTerminator("delimiter " + del);
		sqlTerminator.setTerminator(del);
		sqlTerminator.setEndStatementTerminator("delimiter ;");
	}

	@Override
	public boolean supportsRowValueComparison() {
		return true;
	}

	@Override
	public boolean supportsRowValueComparisonIn() {
		return true;
	}
}
