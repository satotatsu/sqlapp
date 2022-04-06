/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.firebird;

import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.metadata.FirebirdCatalogReader;
import com.sqlapp.data.db.dialect.firebird.sql.FirebirdSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.CascadeRule;

/**
 * Firebird固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Firebird extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5526324282610211323L;
	private static final long SIZE_MAX = LEN_2GB - 1;

	/**
	 * コンストラクタ
	 */
	protected Firebird(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(32767);
		// VARCHAR
		getDbDataTypes().addVarchar(32765);
		// CLOB
		getDbDataTypes().addClob("BLOB SUB_TYPE TEXT", SIZE_MAX).addFormats("BLOB SUBTYPE 1")
				.setCreateFormat("BLOB SUB_TYPE TEXT SEGMENT SIZE(", ")");
		// Binary
		getDbDataTypes().addBlob("BLOB", SIZE_MAX).addFormats("BLOB SUBTYPE 0")
				.setCreateFormat("BLOB SUB_TYPE BINARY SEGMENT SIZE(", ")");
		// Decimal
		getDbDataTypes().addDecimal().setDefaultPrecision(18).setDefaultScale(5).setMaxPrecision(18).setMaxScale(18);
		// Numeric
		getDbDataTypes().addNumeric().setDefaultPrecision(18).setDefaultScale(5).setMaxPrecision(18).setMaxScale(18);
		// Boolean
		getDbDataTypes().addBoolean("DECIMAL(1,0)", "DECIMAL(1,0)", "0");
		// Int16
		getDbDataTypes().addSmallInt();
		// Int32
		getDbDataTypes().addInt("INTEGER").setCreateFormat("INTEGER");
		// Int64
		getDbDataTypes().addBigInt();
		// GUID
		getDbDataTypes().addUUID().setAsVarcharType();
		// Single
		getDbDataTypes().addReal("FLOAT");
		// Double
		getDbDataTypes().addDouble().addFormats("DOUBLE PRECISION");
		// Date
		getDbDataTypes().addDate().setLiteral("'", "'").setDefaultValueLiteral(getCurrentDateFunction());
		// Time
		getDbDataTypes().addTime().setLiteral("'", "'").setCreateFormat("TIME")
				.setDefaultValueLiteral(getCurrentTimeFunction());
		// Timestamp
		getDbDataTypes().addTimestamp().setLiteral("'", "'").setCreateFormat("TIMESTAMP")
				.setDefaultValueLiteral(getCurrentTimestampFunction());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getProductName()
	 */
	@Override
	public String getProductName() {
		return "Firebird";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "firebird";
	}

	@Override
	public String getSequenceNextValString(final String sequenceName) {
		return String.format("select gen_id(%s, 1 ) from RDB$DATABASE", sequenceName);
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	@Override
	public boolean supportsLimit() {
		return true;
	}

	@Override
	public boolean supportsLimitOffset() {
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
	public boolean supportsFunctionOverload() {
		return false;
	}

	@Override
	public boolean supportsProcedureOverload() {
		return false;
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
	public int hashCode() {
		return getProductName().hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new FirebirdCatalogReader(this);
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

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new FirebirdSqlFactoryRegistry(this);
	}

	@Override
	public FirebirdSqlBuilder createSqlBuilder() {
		return new FirebirdSqlBuilder(this);
	}

	@Override
	public FirebirdSqlSplitter createSqlSplitter() {
		return new FirebirdSqlSplitter(this);
	}

	private static String[] DELIMITERS = new String[] { "@", "$", "%", "/", "!" };

	/**
	 * set a change SQL Delimiter text;
	 * 
	 * @param operation
	 */
	@Override
	public void setChangeAndResetSqlDelimiter(final SqlOperation operation) {
		if (!operation.getSqlText().contains(";")) {
			return;
		}
		final String del = getDelimiter(operation.getSqlText(), DELIMITERS);
		operation.setTerminator(del);
		operation.setStartStatementTerminator("SET TERM ; " + del);
		operation.setEndStatementTerminator("SET TERM " + del + " ;");
	}

	@Override
	public boolean isDdlRollbackable() {
		return true;
	}
}
