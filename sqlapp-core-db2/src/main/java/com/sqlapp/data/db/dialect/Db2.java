/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import static com.sqlapp.data.db.datatype.DataType.BIT;
import static com.sqlapp.data.db.datatype.DataType.CHAR;
import static com.sqlapp.data.db.datatype.DataType.DECIMAL;
import static com.sqlapp.data.db.datatype.DataType.LONGVARBINARY;
import static com.sqlapp.data.db.datatype.DataType.NCHAR;
import static com.sqlapp.data.db.datatype.DataType.NUMERIC;
import static com.sqlapp.data.db.datatype.DataType.NVARCHAR;
import static com.sqlapp.data.db.datatype.DataType.UUID;
import static com.sqlapp.data.db.datatype.DataType.VARCHAR;
import static com.sqlapp.util.CommonUtils.LEN_1GB;
import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.db2.metadata.Db2CatalogReader;
import com.sqlapp.data.db.dialect.db2.sql.Db2SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.util.CommonUtils;

/**
 * DB2固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Db2 extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7536431030146748711L;

	/**
	 * コンストラクタ
	 * @param nextVersionDialectSupplier
	 */
	public Db2(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(254);
		// VARCHAR(LONGVARCHARの代替にするためにあえて、別途MaxLengthを設定)
		getDbDataTypes().addVarchar(32700).setMaxLength(32672);
		// LONGVARCHAR(非推奨)
		getDbDataTypes().addLongVarchar(32700).setDeprecated(
				getDbDataTypes().getDbType(VARCHAR));
		// CLOB
		getDbDataTypes().addClob("CLOB", LEN_2GB - 1);
		// NCHAR
		getDbDataTypes().addNChar("GRAPHIC", 127).setLiteral("'", "'");
		// NVARCHAR(LONGNVARCHARの代替にするためにあえて、別途MaxLengthを設定)
		getDbDataTypes().addNVarchar("VARGRAPHIC", 16350).setMaxLength(16336)
				.setLiteral("'", "'");
		// LONGNVARCHAR(非推奨)
		getDbDataTypes().addLongNVarchar("LONG VARGRAPHIC", 16350)
				.setDeprecated(getDbDataTypes().getDbType(NVARCHAR));
		// NCLOB
		getDbDataTypes().addNClob("DBCLOB", LEN_1GB - 1).setLiteral("'", "'");
		// UUID
		getDbDataTypes().addUUID("CHAR(16) FOR BIT DATA")
				.setLiteral("X'", "'")
				.setFormats("CHAR\\s*\\(\\s*16\\s*\\)\\s*FOR BIT DATA")
				.addFormats("CHARACTER\\s*\\(\\s*16\\s*\\)\\s*FOR BIT DATA")
				.setAsBinaryType();
		// BIT
		getDbDataTypes().addBit("BIT(CHAR(1) FOR BIT DATA)")
				.setFormats("CHAR\\s*\\(\\s*1\\s*\\)\\s*FOR BIT DATA")
				.setLiteral("X'", "'").setDefaultValueLiteral("X'0'");
		// BINARY
		getDbDataTypes()
				.addBinary("CHAR () FOR BIT DATA", 32672)
				.setCreateFormat("CHAR(", ") FOR BIT DATA")
				.setFormats(
						"CHAR\\s*\\(\\s*([0-9]+){0,1}\\s*\\)\\s*FOR BIT DATA")
				.addFormats(
						"CHARACTER\\s*\\(\\s*([0-9]+){0,1}\\s*\\)\\s*FOR BIT DATA")
				.setLiteral("X'", "'").setDefaultValueLiteral("X'0'")
				.setSizeSarrogation(1, BIT).setSizeSarrogation(16, UUID);
		// VARBINARY
		getDbDataTypes()
				.addVarBinary("VARCHAR () FOR BIT DATA", 32672)
				.setCreateFormat("VARCHAR(", ") FOR BIT DATA")
				.setFormats(
						"VARCHAR\\s*\\(\\s*([0-9]+){0,1}\\s*\\)\\s*FOR BIT DATA")
				.setLiteral("X'", "'").setDefaultValueLiteral("X'0'");
		// LONGVARBINARY(非推奨)
		getDbDataTypes().addVarBinary("LONG VARCHAR FOR BIT DATA", 32700)
				.setDefaultLength(32700)
				.setCreateFormat("LONG VARCHAR FOR BIT DATA")
				.setFormats("LONG VARCHAR FOR BIT DATA").setLiteral("X'", "'")
				.setDefaultValueLiteral("X'0'")
				.setDeprecated(getDbDataTypes().getDbType(LONGVARBINARY));
		// BLOB
		getDbDataTypes().addBlob("BLOB", LEN_2GB - 1);
		// Byte
		getDbDataTypes().addTinyInt();
		// Int16
		getDbDataTypes().addSmallInt();
		// Int32
		getDbDataTypes().addInt("INTEGER");
		// Int64
		getDbDataTypes().addBigInt();
		// XML
		getDbDataTypes().addSqlXml("XML");
		// REAL
		getDbDataTypes().addReal();
		// Double
		getDbDataTypes().addDouble().addFormats("DOUBLE PRECISION");
		// DecimalFloat
		getDbDataTypes().addDecimalFloat(34);
		// Date
		getDbDataTypes().addDate().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentDateFunction());
		// Time
		getDbDataTypes().addTime().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimeFunction());
		// Timestamp
		getDbDataTypes().addTimestamp().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimestampFunction());
		// Decimal
		getDbDataTypes().addDecimal().setDefaultPrecision(19)
				.setDefaultScale(5).setMaxPrecision(31).setMaxScale(31);
		// Numeric
		getDbDataTypes().addNumeric().setDefaultPrecision(19)
				.setDefaultScale(5).setMaxPrecision(31).setMaxScale(31);
		// XML
		getDbDataTypes().addSqlXml("XMLVARCHAR", 32672).setLiteral("'", "'");
		getDbDataTypes().addSqlXml("XMLCLOB", CommonUtils.LEN_2GB).setLiteral("'", "'");
		// 推奨される型の登録
		getDbDataTypes().registerRecommend(CHAR, VARCHAR);
		getDbDataTypes().registerRecommend(NCHAR, NVARCHAR);
		getDbDataTypes().registerRecommend(NUMERIC, DECIMAL);
	}

	/**
	 * DB名
	 */
	@Override
	public String getProductName() {
		return "DB2";
	}

	/**
	 * DB製品名(シンプル名)
	 */
	@Override
	public String getSimpleName() {
		return "db2";
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	@Override
	public String getIdentityInsertString() {
		return "default";
	}

	@Override
	public String getIdentitySelectString() {
		return "select identity_val_local() from sysibm.sysdummy1";
	}

	@Override
	public String getSequenceNextValString(final String sequenceName) {
		return "values nextval for " + sequenceName;
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	@Override
	public String getIdentityColumnString() {
		return "NOT NULL GENERATED BY DEFAULT AS IDENTITY";
	}

	@Override
	public boolean supportsSequence() {
		return true;
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
		return true;
	}

	@Override
	public boolean supportsProcedureOverload() {
		return true;
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
		if (rule == CascadeRule.None) {
			return true;
		}
		return false;
	}

	@Override
	public boolean supportsCascadeRistrict() {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return true;
	}

	/**
	 * 現在日付の取得関数
	 */
	@Override
	public String getCurrentDateFunction() {
		return "CURRENT_DATE";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new Db2CatalogReader(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.getProductName().hashCode();
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

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Db2SqlFactoryRegistry(this);
	}
	
	@Override
	public Db2SqlBuilder createSqlBuilder(){
		return new Db2SqlBuilder(this);
	}
	
	@Override
	public Db2SqlSplitter createSqlSplitter(){
		return new Db2SqlSplitter(this);
	}
	
	private static String[] DELIMITERS=new String[]{"@", "$", "%", "/", "!"};

	@Override
	public void setChangeAndResetSqlDelimiter(final SqlOperation operation){
		if (!operation.getSqlText().contains(";")){
			return;
		}
		final String del=getDelimiter(operation.getSqlText(), DELIMITERS);
		operation.setStartStatementTerminator("--#SET TERMINATOR "+del);
		operation.setTerminator(del);
		operation.setEndStatementTerminator("--#SET TERMINATOR ;");
	}
	
	@Override
	public boolean isDdlRollbackable(){
		return true;
	}
}
