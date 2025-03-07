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

package com.sqlapp.data.db.dialect.sqlserver;

import static com.sqlapp.data.db.datatype.DataType.CHAR;
import static com.sqlapp.data.db.datatype.DataType.DATETIME;
import static com.sqlapp.data.db.datatype.DataType.NCHAR;
import static com.sqlapp.data.db.datatype.DataType.NVARCHAR;
import static com.sqlapp.data.db.datatype.DataType.SMALLDATETIME;
import static com.sqlapp.data.db.datatype.DataType.VARCHAR;
import static com.sqlapp.util.CommonUtils.LEN_1GB;
import static com.sqlapp.util.CommonUtils.LEN_2GB;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.size;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.DefaultCase;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2000CatalogReader;
import com.sqlapp.data.db.dialect.sqlserver.sql.SqlServerSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;

/**
 * SqlServer2000固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class SqlServer2000 extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 3840025482658828284L;

	protected SqlServer2000(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(8000);
		// VARCHAR
		getDbDataTypes().addVarchar(8000);
		// LONGVARCHAR
		getDbDataTypes().addLongVarchar("TEXT", LEN_2GB - 1)
				.setCreateFormat("TEXT").setFormats("TEXT").setDefaultLength(LEN_2GB - 1).setFixedLength(false);
		// NCHAR
		getDbDataTypes().addNChar(4000);
		// NVARCHAR
		getDbDataTypes().addNVarchar(4000);
		// LONGVARCHAR
		getDbDataTypes().addLongNVarchar("NTEXT", LEN_1GB - 1)
				.setCreateFormat("NTEXT").setFormats("NTEXT")
				.addFormats("NATIONAL\\s+TEXT").setDefaultLength(LEN_1GB - 1).setFixedLength(false);
		// BINARY
		getDbDataTypes().addBinary(8000).setLiteral("0x", "");
		// VARBINARY
		getDbDataTypes().addVarBinary(8000).setLiteral("0x", "");
		// BLOB
		getDbDataTypes().addBlob("IMAGE", LEN_2GB - 1).setCreateFormat("IMAGE")
				.setFormats("IMAGE").setLiteral("0x", "");
		// Bit
		getDbDataTypes().addBit();
		// SByte
		getDbDataTypes().addTinyInt().addFormats("TINYINT IDENTITY");
		// SMALLINT
		getDbDataTypes().addSmallInt().addFormats("SMALLINT IDENTITY");
		// INT
		getDbDataTypes().addInt().addFormats("INT IDENTITY");
		// Int64
		getDbDataTypes().addBigInt().addFormats("BIGINT IDENTITY");
		// GUID
		getDbDataTypes().addUUID("UNIQUEIDENTIFIER").setLiteral("'", "'")
				.setDefaultValueLiteral("NEWID()");
		// Single
		getDbDataTypes().addReal();
		// Single
		getDbDataTypes().addFloat(53);
		// SmallDateTime
		getDbDataTypes().addSmallDateTime().setLiteral("{ts '", "'}")
				.setCreateFormat("SMALLDATETIME")
				.setDefaultValueLiteral(getCurrentDateTimeFunction());
		// DateTime
		getDbDataTypes().addDateTime().setLiteral("{ts '", "'}")
				.setCreateFormat("DATETIME")
				.setDefaultValueLiteral(getCurrentDateTimeFunction());
		// SmallMoney
		getDbDataTypes().addSmallMoney("SMALLMONEY");
		// Money
		getDbDataTypes().addMoney("MONEY");
		// Decimal
		getDbDataTypes().addDecimal().setMaxPrecision(38)
				.setDefaultPrecision(19).setDefaultScale(5)
				.addPrecisionScaleFormat("DEC");
		// Numeric
		getDbDataTypes().addNumeric().setMaxPrecision(38)
				.setDefaultPrecision(19).setDefaultScale(5);
		// 行バージョン型
		getDbDataTypes().addRowVersion("TIMESTAMP").setLiteral("0x", "");
		// SYSNAME型
		getDbDataTypes().addSqlIdentifierType("SYSNAME");
		//ANYDATA
		getDbDataTypes().addAnyData("VARIANT");
		// 推奨される型の登録
		getDbDataTypes().registerRecommend(CHAR, NVARCHAR);
		getDbDataTypes().registerRecommend(NCHAR, NVARCHAR);
		getDbDataTypes().registerRecommend(VARCHAR, NVARCHAR);
		getDbDataTypes().registerRecommend(SMALLDATETIME, DATETIME);
	}

	/**
	 * DB製品名
	 */
	@Override
	public String getProductName() {
		return "Microsoft SQL Server";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "sqlserver";
	}

	@Override
	public boolean supportsWith() {
		return false;
	}

	/**
	 * TOP句のサポート
	 */
	@Override
	public boolean supportsTop() {
		return true;
	}

	@Override
	public String getIdentitySelectString() {
		return "select SCOPE_IDENTITY()";
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	@Override
	public String getIdentityColumnString() {
		return "IDENTITY NOT NULL";
	}

	/**
	 * DBカタログのサポート
	 */
	@Override
	public boolean supportsCatalog() {
		return true;
	}

	/**
	 * DBスキーマのサポート
	 */
	@Override
	public boolean supportsSchema() {
		return false;
	}

	@Override
	public boolean recommendsNTypeChar() {
		return true;
	}

	@Override
	public char getCloseQuote() {
		return ']';
	}

	@Override
	public char getOpenQuote() {
		return '[';
	}

	@Override
	public DefaultCase getDefaultCase() {
		return DefaultCase.NonConvert;
	}

	@Override
	public String getCurrentDateFunction() {
		return "CAST(CONVERT(VARCHAR(10),CURRENT_TIMESTAMP,121) AS DATETIME)";
	}

	/**
	 * 現在日時の取得関数
	 */
	@Override
	public String getCurrentDateTimeFunction() {
		return "CURRENT_TIMESTAMP";
	}

	/**
	 * 現在日時(Timestamp)の取得関数
	 */
	@Override
	public String getCurrentTimestampFunction() {
		return "CURRENT_TIMESTAMP";
	}

	/**
	 * 現在日時(Timestamp)タイムゾーン付きの取得関数
	 */
	@Override
	public String getCurrentTimestampWithTimeZoneFunction() {
		return "CURRENT_TIMESTAMP";
	}

	@Override
	public String defaultSchema() {
		return "dbo";
	}

	@Override
	public boolean supportsCascadeDelete() {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return true;
	}

	@Override
	public boolean supportsDropCascade() {
		return true;
	}

	@Override
	public boolean supportsFunctionOverload() {
		return false;
	}

	@Override
	public boolean supportsProcedureOverload() {
		return false;
	}

	@Override
	public boolean supportsRuleOnDelete(final CascadeRule rule) {
		if (rule == CascadeRule.None || rule == CascadeRule.SetNull
		// || rule == Rule.SetDefault
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
		// || rule == Rule.SetDefault
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#storesMixedCaseIdentifiers()
	 */
	@Override
	public boolean storesMixedCaseIdentifiers() {
		return true;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.Dialect#isOptimisticLockColumn(com.sqlapp.data.schemas.Column)
	 */
	@Override
	public boolean isOptimisticLockColumn(final Column column) {
		if (column.getDataType().isBinary()
				&& column.getName().equalsIgnoreCase("TIMESTAMP")) {
			return true;
		}
		return super.isOptimisticLockColumn(column);
	}

	@Override
	public int hashCode() {
		return getProductName().hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new SqlServer2000CatalogReader(this);
	}

	@Override
	public String getObjectFullName(final String catalogName,
			final String schemaName, final String objectName) {
		final StringBuilder builder = new StringBuilder(size(catalogName)
				+ size(schemaName) + size(objectName) + 2);
		if (!isEmpty(catalogName)) {
			builder.append(catalogName);
			builder.append('.');
			if (!isEmpty(schemaName)) {
				builder.append(schemaName);
			}
			builder.append('.');
		} else {
			if (!isEmpty(schemaName)) {
				builder.append(schemaName);
				builder.append('.');
			}
		}
		builder.append(objectName);
		return builder.toString();
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SqlServerSqlFactoryRegistry(this);
	}
	
	@Override
	public SqlServerSqlBuilder createSqlBuilder(){
		return new SqlServerSqlBuilder(this);
	}
	
	@Override
	public SqlServerSqlSplitter createSqlSplitter(){
		return new SqlServerSqlSplitter(this);
	}
	
	@Override
	protected String doQuote(final String target){
		final StringBuilder builder = new StringBuilder(target.length() + 2);
		builder.append(getOpenQuote()).append(target.replace("]", "]]")).append(getCloseQuote());
		return builder.toString();
	}

	@Override
	public void setChangeAndResetSqlDelimiter(final SqlOperation operation){
		if (!operation.getSqlText().contains(";")){
			return;
		}
		operation.setTerminator("GO");
		operation.setEndStatementTerminator("GO");
	}

	@Override
	public boolean isDdlRollbackable(){
		return true;
	}
}
