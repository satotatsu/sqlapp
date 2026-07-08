/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2;

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

import com.sqlapp.data.db.datatype.util.LengthColumnTypeMatcher;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.metadata.Db2CatalogReader;
import com.sqlapp.data.db.dialect.db2.sql.Db2SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTerminator;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Table;
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
	 * 
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
		getDbDataTypes().addVarchar(32700, type -> {
			type.setMaxLength(32672);
		});
		// LONGVARCHAR(非推奨)
		getDbDataTypes().addLongVarchar(32700, type -> {
			type.setDeprecated(getDbDataTypes().getDbType(VARCHAR));
		});
		// CLOB
		getDbDataTypes().addClob("CLOB", LEN_2GB - 1);
		// NCHAR
		getDbDataTypes().addNChar("GRAPHIC", 127, type -> {
			type.setLiteral("'", "'");
		});
		// NVARCHAR(LONGNVARCHARの代替にするためにあえて、別途MaxLengthを設定)
		getDbDataTypes().addNVarchar("VARGRAPHIC", 16350, type -> {
			type.setMaxLength(16336).setLiteral("'", "'");
		});
		// LONGNVARCHAR(非推奨)
		getDbDataTypes().addLongNVarchar("LONG VARGRAPHIC", 16350, type -> {
			type.setDeprecated(getDbDataTypes().getDbType(NVARCHAR));
		});
		// NCLOB
		getDbDataTypes().addNClob("DBCLOB", LEN_1GB - 1, type -> {
			type.setLiteral("'", "'");
		});
		// UUID
		getDbDataTypes().addUUID("CHAR(16) FOR BIT DATA", type -> {
			type.setPetternColumnTypeMatcher("CHAR(ACTER)?\\s*\\(\\s*16\\s*\\)\\s*FOR\\s+BIT\\s+DATA");
			type.setLiteral("X'", "'").setAsBinaryType();
		});
		// BIT
		getDbDataTypes().addBoolean("CHAR(1) FOR BIT DATA", type -> {
			type.setPetternColumnTypeMatcher("CHAR(ACTER)?\\s*\\(\\s*1\\s*\\)\\s*FOR\\s+BIT\\s+DATA");
			type.setLiteral("X'", "'").setDefaultValueLiteral("X'0'");
		});
		// BINARY
		getDbDataTypes().addBinary("CHAR () FOR BIT DATA", 32672, type -> {
			type.setColumnTypeMatcher(new LengthColumnTypeMatcher("CHAR(ACTER)?", "FOR\\s+BIT\\s+DATA"));
			type.setCreateFormat("CHAR(", ") FOR BIT DATA").setLiteral("X'", "'").setDefaultValueLiteral("X'0'")
					.setSizeSarrogation(1, BIT).setSizeSarrogation(16, UUID);
		});
		// VARBINARY
		getDbDataTypes().addVarBinary("VARCHAR () FOR BIT DATA", 32672, type -> {
			type.setColumnTypeMatcher(new LengthColumnTypeMatcher("VARCHAR", "FOR\\s+BIT\\s+DATA"));
			type.setCreateFormat("VARCHAR(", ") FOR BIT DATA").setLiteral("X'", "'").setDefaultValueLiteral("X'0'");
		});
		// LONGVARBINARY(非推奨)
		getDbDataTypes().addVarBinary("LONG VARCHAR FOR BIT DATA", 32700, type -> {
			type.setDefaultLength(32700).setCreateFormat("LONG VARCHAR FOR BIT DATA").setLiteral("X'", "'")
					.setDefaultValueLiteral("X'0'").setDeprecated(getDbDataTypes().getDbType(LONGVARBINARY));
		});
		// BLOB
		getDbDataTypes().addBlob("BLOB", LEN_2GB - 1, type -> {
		});
		// Byte
		getDbDataTypes().addTinyInt(type -> {
		});
		// Int16
		getDbDataTypes().addSmallInt(type -> {
		});
		// Int32
		getDbDataTypes().addInt("INTEGER", type -> {
		});
		// Int64
		getDbDataTypes().addBigInt(type -> {
		});
		// XML
		getDbDataTypes().addSqlXml("XML", type -> {
		});
		// REAL
		getDbDataTypes().addReal(type -> {
		});
		// Double
		getDbDataTypes().addDouble(type -> {
		});
		// DecimalFloat
		getDbDataTypes().addDecimalFloat(34);
		// Date
		getDbDataTypes().addDate(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentDateFunction());
		});
		// Time
		getDbDataTypes().addTime(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentTimeFunction());
		});
		// Timestamp
		getDbDataTypes().addTimestamp(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentTimestampFunction());
		});
		// Decimal
		getDbDataTypes().addDecimal(type -> {
			type.setDefaultPrecision(19).setDefaultScale(5).setMaxPrecision(31).setMaxScale(31);
		});
		// Numeric
		getDbDataTypes().addNumeric(type -> {
			type.setDefaultPrecision(19).setDefaultScale(5).setMaxPrecision(31).setMaxScale(31);
		});
		// XML
		getDbDataTypes().addSqlXml("XMLVARCHAR", 32672, type -> {
			type.setLiteral("'", "'");
		});
		getDbDataTypes().addSqlXml("XMLCLOB", CommonUtils.LEN_2GB, type -> {
			type.setLiteral("'", "'");
		});
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
	public String getSelectDummyTableName() {
		return "SYSIBM.SYSDUMMY1";
	}

	@Override
	public String getTemporaryTableName(final Table table, String prefix, String suffix, boolean witSchema) {
		String name = null;
		if (!CommonUtils.isEmpty(prefix)) {
			name = prefix + table.getName();
		} else {
			name = table.getName();
		}
		if (!CommonUtils.isEmpty(suffix)) {
			name = name + suffix;
		}
		return getObjectFullName("SESSION", name);
	}

	@Override
	public String getIdentitySelectString() {
		if (getSelectDummyTableName() != null) {
			return "select identity_val_local() from " + getSelectDummyTableName();
		} else {
			return "select identity_val_local()";
		}
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
	public Db2SqlBuilder createSqlBuilder() {
		return new Db2SqlBuilder(this);
	}

	@Override
	public Db2SqlSplitter createSqlSplitter() {
		return new Db2SqlSplitter(this);
	}

	private static String[] DELIMITERS = new String[] { "@", "$", "%", "/", "!" };

	@Override
	public void setChangeAndResetSqlDelimiter(final String sql, final SqlTerminator sqlTerminator) {
		if (!sql.contains(";")) {
			return;
		}
		final String del = getDelimiter(sql, DELIMITERS);
		sqlTerminator.setStartStatementTerminator("--#SET TERMINATOR " + del);
		sqlTerminator.setTerminator(del);
		sqlTerminator.setEndStatementTerminator("--#SET TERMINATOR ;");
	}

	@Override
	public boolean isDdlRollbackable() {
		return true;
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
