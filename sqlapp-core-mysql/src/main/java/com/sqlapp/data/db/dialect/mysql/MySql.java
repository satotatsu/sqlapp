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
import java.util.regex.Matcher;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.DefaultCase;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlCatalogReader;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlSplitter;
import com.sqlapp.data.db.dialect.util.GeometryUtils;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.data.schemas.properties.SpecificsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.function.TriConsumer;

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

	private static final String WIDTH_PATTERN="(\\(\\s*[0-9]+\\s*\\))?";

	private static final String ZEROFILL_PATTERN="\\s*(ZEROFILL)?\\s*";
	
	private static final String UNSIGNED="\\s*UNSIGNED\\s*";
	
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
		getDbDataTypes().addLongVarchar("TINYTEXT", 255)
				.setCreateFormat("TINYTEXT").addFormats("TINYTEXT");
		getDbDataTypes().addLongVarchar("TEXT", LEN_64KB - 1)
				.setCreateFormat("TEXT").addFormats("TEXT");
		getDbDataTypes().addLongVarchar("MEDIUMTEXT", LEN_16MB)
				.setCreateFormat("MEDIUMTEXT");
		getDbDataTypes().addLongVarchar("LONGTEXT", LEN_4GB).setCreateFormat(
				"LONGTEXT");
		// BINARY
		getDbDataTypes().addBinary(255).addFormats(
				"CHAR\\s*\\(\\s*([0-9]+)\\s*\\)\\s+BINARY");
		// VARBINARY
		getDbDataTypes().addVarBinary(65535).addFormats(
				"VARCHAR\\s*\\(\\s*([0-9]+)\\s*\\)\\s+BINARY");
		// BLOB
		getDbDataTypes().addBlob("TINYBLOB", 255).setCreateFormat("BLOB");
		getDbDataTypes().addBlob("BLOB", LEN_64KB - 1);
		getDbDataTypes().addBlob("MEDIUMBLOB", LEN_16MB - 1).setCreateFormat(
				"MEDIUMBLOB");
		getDbDataTypes().addBlob("LONGBLOB", LEN_4GB - 1).setCreateFormat(
				"LONGBLOB");
		// Bit
		getDbDataTypes().addBit("BIT", "0");

		final TriConsumer<DbDataType<?>, Matcher, DataTypeLengthProperties<?>> parseAndSetConsumer=(own, m,column)->{
			if (!(column instanceof SpecificsProperty)) {
				return;
			}
			SchemaUtils.setDataTypeNameInternal(null, column);
			final SpecificsProperty<?> specificsProperty=(SpecificsProperty<?>)column;
			final int groupCount=m.groupCount();
			for(int i=groupCount;i>0;i--) {
				String value=m.group(i);
				if (!CommonUtils.isEmpty(value)) {
					if ("ZEROFILL".equalsIgnoreCase(value)){
						specificsProperty.getSpecifics().put("zerofill", true);
					} else {
						value=CommonUtils.trim(CommonUtils.unwrap(value, "(", ")"));
						specificsProperty.getSpecifics().put("width", value);
					}
				}
			}
		};
		// Int8
		getDbDataTypes().addTinyInt().addFormats("TINYINT\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN)
				.addFormats("INT1\\s*"+ZEROFILL_PATTERN).addFormats("INT1\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// Int16
		getDbDataTypes().addSmallInt()
				.addFormats("SMALLINT\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).addFormats("INT2\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// Int24
		getDbDataTypes().addMediumInt()
				.addFormats("MEDIUMINT\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).addFormats("INT3\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// Int32
		getDbDataTypes().addInt().addFormats("INT\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).addFormats("INT4\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// Int64
		getDbDataTypes().addBigInt().addFormats("BIGINT\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).addFormats("INT8\\s*"+WIDTH_PATTERN+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// UTINYINT
		getDbDataTypes().addUTinyInt()
				.addFormats("TINYINT\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN)
				.addFormats("INT1\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// UInt16
		getDbDataTypes().addUSmallInt()
				.addFormats("SMALLINT\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN)
				.addFormats("INT2\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// UInt24
		getDbDataTypes().addUMediumInt()
				.addFormats("MEDIUMINT\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN)
				.addFormats("INT3\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// UInt32
		getDbDataTypes().addUInt()
				.addFormats("INT\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN)
				.addFormats("INT4\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// UInt64
		getDbDataTypes().addUBigInt()
				.addFormats("BIGINT\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN)
				.addFormats("INT8\\s*"+WIDTH_PATTERN+UNSIGNED+ZEROFILL_PATTERN).setParseAndSet(parseAndSetConsumer);
		// GUID
		getDbDataTypes().addUUID("UUID").addFormats("BINARY(16)")
				.setAsBinaryType();
		// Single
		getDbDataTypes().addReal("FLOAT").addFormats("FLOAT4");
		// Single
		getDbDataTypes().addFloat(53);
		// Double
		getDbDataTypes().addDouble().addFormats("DOUBLE PRECISION")
				.addFormats("FLOAT8");
		// Date
		getDbDataTypes().addDate().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentDateFunction());
		// DateTime
		getDbDataTypes().addTimestamp("DATETIME").setLiteral("'", "'").setCreateFormat("DATETIME")
				.setDefaultValueLiteral(getCurrentDateTimeFunction()).setFixedPrecision(false).setConverter(Converters.getDefault().getConverter(java.util.Date.class));
		// Time
		getDbDataTypes().addTime().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimeFunction()).setFixedPrecision(false);
		// Timestamp
		getDbDataTypes().addTimestampVersion("TIMESTAMP").setLiteral("'", "'").setCreateFormat("TIMESTAMP")
				.setDefaultValueLiteral(getCurrentTimestampFunction()).setFixedPrecision(false);
		// Decimal
		getDbDataTypes().addDecimal().setMaxPrecision(65).setMaxScale(30);
		// Numeric
		getDbDataTypes().addNumeric().setMaxPrecision(65).setMaxScale(30);
		GeometryUtils.run(new Runnable(){
			@Override
			public void run() {
				// GEOMETRY
				getDbDataTypes().addGeometry("GEOMETRY")
						.setJdbcTypeHandler(new MySqlGeometryJdbcTypeHandler())
						.setJdbcType(java.sql.JDBCType.ARRAY);
			}
		});
		// ENUM
		getDbDataTypes().addEnum();
		// SET
		getDbDataTypes().addSet();
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
		if (rule == CascadeRule.None || rule == CascadeRule.SetNull
				|| rule == CascadeRule.Cascade) {
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
		if (rule == CascadeRule.None || rule == CascadeRule.SetNull
				|| rule == CascadeRule.Cascade) {
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
	public MySqlSqlBuilder createSqlBuilder(){
		return new MySqlSqlBuilder(this);
	}

	@Override
	public MySqlSqlSplitter createSqlSplitter(){
		return new MySqlSqlSplitter(this);
	}
	
	private static String[] DELIMITERS=new String[]{"@", "$", "%", "/", "!"};

	/**
	 * set a change SQL Delimiter text;
	 * @param operation
	 */
	@Override
	public void setChangeAndResetSqlDelimiter(final SqlOperation operation){
		if (!operation.getSqlText().contains(";")){
			return;
		}
		final String del=getDelimiter(operation.getSqlText(), DELIMITERS);
		operation.setStartStatementTerminator("delimiter "+del);
		operation.setTerminator(del);
		operation.setEndStatementTerminator("delimiter ;");
	}
}
