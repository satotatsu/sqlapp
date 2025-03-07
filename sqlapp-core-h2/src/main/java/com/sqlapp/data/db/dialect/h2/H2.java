/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2;

import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.h2.metadata.H2CatalogReader;
import com.sqlapp.data.db.dialect.h2.sql.H2SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.dialect.h2.util.H2SqlSplitter;
import com.sqlapp.data.db.dialect.util.GeometryUtils;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;

/**
 * H2
 * 
 * @author satoh
 * 
 */
public class H2 extends Dialect {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6420560614725995097L;

	private static final int CHAR_SIZE_MAX = 200;
	private static final long SIZE_MAX = LEN_2GB - 1;
	/**
	 * システム予約スキーマ
	 */
	private final String[] SYSTEM_SCHEMA = new String[] { "INFORMATION_SCHEMA" };

	protected H2(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// ARRAY
		getDbDataTypes().addArray();
		// CHAR
		getDbDataTypes().addChar(CHAR_SIZE_MAX);
		// VARCHAR
		getDbDataTypes().addVarchar(SIZE_MAX).addSizeFormat("VARCHAR2")
				.addSizeFormat("VARCHAR_CASESENSITIVE");
		// VARCHAR IGNORECASE
		getDbDataTypes().addVarcharIgnoreCase(SIZE_MAX);
		// LONG VARCHAR
		getDbDataTypes().addLongVarchar(SIZE_MAX);
		// CLOB
		getDbDataTypes().addClob("CLOB", SIZE_MAX);
		// NCHAR(CHARと同じ)
		getDbDataTypes().addNChar(CHAR_SIZE_MAX).setLiteral("'", "'");
		// NVARCHAR(VARCHARと同じ)
		getDbDataTypes().addNVarchar(CHAR_SIZE_MAX).setLiteral("'", "'")
				.addSizeFormat("NVARCHAR2");
		// NCLOB(CLOBと同じ)
		getDbDataTypes().addNClob("NCLOB", SIZE_MAX).setLiteral("'", "'");
		// BINARY
		getDbDataTypes().addBinary("BINARY", SIZE_MAX).setLiteral("X'", "'");
		// VARBINARY
		getDbDataTypes().addVarBinary("VARBINARY", SIZE_MAX).setLiteral("X'",
				"'");
		// LONGVARBINARY
		getDbDataTypes().addLongVarBinary("LONGVARBINARY", SIZE_MAX)
				.addFormats("RAW\\s*\\(\\s*([0-9]+)\\s*\\)")
				.addFormats("BYTEA\\s*\\(\\s*([0-9]+)\\s*\\)")
				.setLiteral("X'", "'");
		// BLOB
		getDbDataTypes().addBlob("BLOB", LEN_2GB - 1).addFormats("BIT")
				.addFormats("BOOL").setLiteral("X'", "'")
				.setDefaultValueLiteral("FALSE");
		// Boolean
		getDbDataTypes().addBoolean();
		// Byte
		getDbDataTypes().addTinyInt();
		// Int16
		getDbDataTypes().addSmallInt().addFormats("INT2");
		// Int32
		getDbDataTypes().addInt("INTEGER").addFormats("INT4");
		// Int64
		getDbDataTypes().addBigInt().addFormats("INT8");
		// BigSerial
		getDbDataTypes().addBigSerial("IDENTITY");
		// GUID
		getDbDataTypes().addUUID("UUID").setLiteral("'", "'")
				.setDefaultValueLiteral("RANDOM_UUID(");
		// Real
		getDbDataTypes().addReal().addFormats("FLOAT4");
		// Double
		getDbDataTypes().addDouble().addFormats("FLOAT8");
		// Date
		getDbDataTypes().addDate().setDefaultValueLiteral(
				getCurrentDateFunction());
		// Time
		getDbDataTypes().addTime().setDefaultValueLiteral(
				getCurrentTimeFunction());
		// SamllDateTime(TIMESTAMPと同じ)
		getDbDataTypes().addSmallDateTime().setDefaultValueLiteral(
				getCurrentTimestampFunction());
		// DateTime(TIMESTAMPと同じ)
		getDbDataTypes().addDateTime().setDefaultValueLiteral(
				getCurrentTimestampFunction());
		// Timestamp
		getDbDataTypes().addTimestamp().setDefaultValueLiteral(
				getCurrentTimestampFunction());
		// Decimal
		getDbDataTypes().addDecimal().addPrecisionScaleFormat("DEC")
				.addPrecisionScaleFormat("NUMBER");
		// Numeric
		getDbDataTypes().addNumeric();
		// GEOMETRY
		GeometryUtils.run(new Runnable(){
			@Override
			public void run() {
				// GEOMETRY
				getDbDataTypes().addGeometry().setJdbcTypeHandler(
						new H2GeometryJdbcTypeHandler());
			}
		});
	}

	/**
	 * DB製品名
	 */
	@Override
	public String getProductName() {
		return "H2";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "h2";
	}

	/**
	 * TOP句のサポート
	 */
	@Override
	public boolean supportsTop() {
		return true;
	}

	@Override
	public String getSequenceNextValString(final String sequenceName) {
		return "select " + sequenceName + ".nextval from dual";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#supportsSequence()
	 */
	@Override
	public boolean supportsSequence() {
		return true;
	}

	@Override
	public String getIdentitySelectString() {
		return "CALL IDENTITY()";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#supportsIdentity()
	 */
	@Override
	public boolean supportsIdentity() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#supportsDomain()
	 */
	@Override
	public boolean supportsDomain() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.DbDialect#domainCheckConstraintColumnName()
	 */
	@Override
	public String domainCheckConstraintColumnName() {
		return "VALUE";
	}

	@Override
	public char getCloseQuote() {
		return ']';
	}

	@Override
	public char getOpenQuote() {
		return '[';
	}

	/**
	 * 現在日付の取得関数
	 */
	@Override
	public String getCurrentDateFunction() {
		return null;
	}

	/**
	 * 現在日時の取得関数
	 */
	@Override
	public String getCurrentDateTimeFunction() {
		return null;
	}

	/**
	 * 現在日時(Timestamp)の取得関数
	 */
	@Override
	public String getCurrentTimestampFunction() {
		return null;
	}

	/**
	 * 現在日時(Timestamp)タイムゾーン付きの取得関数
	 */
	@Override
	public String getCurrentTimestampWithTimeZoneFunction() {
		return null;
	}

	/**
	 * 現在日時(Timestamp)タイムゾーン付きの取得関数
	 */
	@Override
	public String getCurrentTimeFunction() {
		return null;
	}

	@Override
	public boolean supportsDropCascade() {
		return true;
	}

	@Override
	public boolean supportsCascadeDelete() {
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
		return true;
	}

	@Override
	public boolean supportsCascadeUpdate() {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(final CascadeRule rule) {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return false;
	}

	/**
	 * 既定のスキーマ
	 * 
	 */
	@Override
	public String defaultSchema() {
		return "PUBLIC";
	}

	/**
	 * システム予約しているスキーマ
	 * 
	 */
	@Override
	public String[] getSystemSchema() {
		return SYSTEM_SCHEMA;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#hashCode()
	 */
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
		return new H2CatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new H2SqlFactoryRegistry(this);
	}
	
	@Override
	public H2SqlBuilder createSqlBuilder(){
		return new H2SqlBuilder(this);
	}
	
	@Override
	public H2SqlSplitter createSqlSplitter(){
		return new H2SqlSplitter(this);
	}
}
