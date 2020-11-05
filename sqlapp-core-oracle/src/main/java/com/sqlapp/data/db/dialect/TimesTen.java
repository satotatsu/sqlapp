/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.oracle.metadata.OracleCatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.schemas.CascadeRule;

/**
 * TimesTen固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class TimesTen extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 2977386415263620562L;

	/**
	 * コンストラクタ
	 */
	protected TimesTen(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(8300).addSizeFormat("ORA_CHAR");
		// VARCHAR
		getDbDataTypes().addVarchar("VARCHAR2", 2 ^ 22).addSizeFormat(
				"ORA_VARCHAR2");
		// NCHAR
		getDbDataTypes().addNChar(4150).addSizeFormat("ORA_NCHAR");
		// NVARCHAR
		getDbDataTypes().addNVarchar(2 ^ 21).addSizeFormat("ORA_NVARCHAR2");
		// BINARY
		getDbDataTypes().addBinary(8300).addSizeFormat("TT_BINARY")
				.setLiteral("HEXTORAW('", "')");
		// VARBINARY
		getDbDataTypes().addVarBinary(2 ^ 22).addSizeFormat("TT_VARBINARY")
				.setLiteral("HEXTORAW('", "')");
		// UTINYINT
		getDbDataTypes().addUTinyInt("TT_TINYINT");
		// SMALLINT
		getDbDataTypes().addSmallInt("TT_SMALLINT");
		// INT
		getDbDataTypes().addInt("TT_INT").addFormats("TT_INTEGER");
		// BIGINT
		getDbDataTypes().addBigInt("TT_BIGIN");
		// Single
		getDbDataTypes().addReal("BINARY_FLOAT").addFormats("REAL");
		// Double
		getDbDataTypes().addDouble("BINARY_DOUBLE")
				.addFormats("FLOAT\\s*\\(\\s*126\\s*\\)")
				.addFormats("ORA_FLOAT\\s*\\(\\s*126\\s*\\)");
		// DATETIME
		getDbDataTypes().addDateTime("DATE").setDefaultValueLiteral(
				getCurrentDateTimeFunction());
		// INTERVAL YAER
		getDbDataTypes().addIntervalYear().setCreateFormat("INTERVAL YAER");
		// INTERVAL MONTH
		getDbDataTypes().addIntervalMonth().setCreateFormat("INTERVAL MONTH");
		// INTERVAL DAY
		getDbDataTypes().addIntervalDay().setCreateFormat("INTERVAL DAY");
		// INTERVAL HOUR
		getDbDataTypes().addIntervalHour().setCreateFormat("INTERVAL HOUR");
		// INTERVAL MINUTE
		getDbDataTypes().addIntervalMinute().setCreateFormat("INTERVAL MINUTE");
		// INTERVAL SECOND
		getDbDataTypes().addIntervalMinute().setCreateFormat("INTERVAL SECOND");
		// Decimal
		getDbDataTypes().addDecimal("NUMBER").setMaxPrecision(38)
				.setDefaultScale(0);
		// DATE
		getDbDataTypes().addDate().setDefaultValueLiteral(
				getCurrentDateFunction());
		// DateTime
		getDbDataTypes().addDateTime("TT_TIMESTAMP").setDefaultValueLiteral(
				getCurrentDateTimeFunction());
		// Time
		getDbDataTypes().addTime().setCreateFormat("TIME")
				.setDefaultValueLiteral(getCurrentTimeFunction())
				.setDefaultPrecision(0).setMaxPrecision(0);
		// TIMESTAMP
		getDbDataTypes().addTimestamp().addScaleFormat("ORA_TIMESTAMP")
				.setDefaultValueLiteral(getCurrentTimestampFunction())
				.setDefaultPrecision(6).setMaxPrecision(9);
	}

	/**
	 * DB製品名
	 */
	@Override
	public String getProductName() {
		return "TimesTen";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "timesten";
	}

	@Override
	public String getSequenceNextValString(String sequenceName) {
		return "select " + sequenceName + ".nextval from dual";
	}

	@Override
	public boolean supportsSequence() {
		return true;
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	/**
	 * ROWNUM句のサポート
	 */
	@Override
	public boolean supportsRownum() {
		return true;
	}

	/**
	 * DBカタログのサポート
	 */
	@Override
	public boolean supportsCatalog() {
		return false;
	}

	/**
	 * DBスキーマのサポート
	 */
	@Override
	public boolean supportsSchema() {
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
	public char getCloseQuote() {
		return '"';
	}

	@Override
	public char getOpenQuote() {
		return '"';
	}

	@Override
	public boolean supportsCascadeDelete() {
		return true;
	}

	@Override
	public boolean supportsDropCascade() {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return false;
	}

	/**
	 * executeBatch実行時の成功時件数を 個別に返す事が出来るか?
	 */
	@Override
	public boolean supportsBatchExecuteResult() {
		return false;
	}

	@Override
	public boolean supportsRuleOnDelete(CascadeRule rule) {
		if (rule == CascadeRule.None || rule == CascadeRule.SetNull
				|| rule == CascadeRule.Cascade) {
			return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return getProductName().hashCode();
	}

	/**
	 * 同値判定
	 */
	@Override
	public boolean equals(Object obj) {
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
		return new OracleCatalogReader(this);
	}
}
