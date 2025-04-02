/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.derby;

import static com.sqlapp.data.db.datatype.DataType.BIT;
import static com.sqlapp.data.db.datatype.DataType.CHAR;
import static com.sqlapp.data.db.datatype.DataType.LONGVARBINARY;
import static com.sqlapp.data.db.datatype.DataType.NCHAR;
import static com.sqlapp.data.db.datatype.DataType.NVARCHAR;
import static com.sqlapp.data.db.datatype.DataType.UUID;
import static com.sqlapp.data.db.datatype.DataType.VARCHAR;
import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.datatype.util.LengthColumnTypeMatcher;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.derby.metadata.DerbyCatalogReader;
import com.sqlapp.data.db.dialect.derby.sql.DerbySqlFactoryRegistry;
import com.sqlapp.data.db.dialect.derby.util.DerbySqlBuilder;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;

/**
 * Derby固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Derby extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8635862003765087520L;
	private static final long SIZE_MAX = LEN_2GB - 1;

	/**
	 * コンストラクタ
	 */
	protected Derby(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(SIZE_MAX, type -> {
			type.setDefaultLength(1);
			type.setCharset("UTF-16");
		});
		// VARCHAR
		getDbDataTypes().addVarchar(32672, type -> {
			type.setCharset("UTF-16");
		});
		// LONGVARCHAR(非推奨)
		getDbDataTypes().addLongVarchar("LONG VARCHAR", 32700, type -> {
			type.setDefaultLength(32700).setCreateFormat("LONG VARCHAR").setCharset("UTF-16");
		});
		// CLOB
		getDbDataTypes().addClob("CLOB", SIZE_MAX, type -> {
			type.setCharset("UTF-16");
		});
		// UUID
		getDbDataTypes().addUUID("CHAR(16) FOR BIT DATA", type -> {
			type.setPetternColumnTypeMatcher("CHAR(ACTER)?\\s*\\(\\s*16\\s*\\)\\s*FOR\\s+BIT\\s+DATA");
			type.setLiteral("'", "'").setAsBinaryType();
		});
		// BIT
		getDbDataTypes().addBit("CHAR(1) FOR BIT DATA", 1, type -> {
			type.setPetternColumnTypeMatcher("CHAR(ACTER)?\\s*\\(\\s*1\\s*\\)\\s*FOR\\s+BIT\\s+DATA");
			type.setLiteral("X'", "'").setDefaultValueLiteral("X'0'");
		});
		// BOOLEAN
		getDbDataTypes().addBoolean();
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
			type.setColumnTypeMatcher(new LengthColumnTypeMatcher("LONG\\s+VARCHAR", "FOR\\s+BIT\\s+DATA"));
			type.setDefaultLength(32700).setCreateFormat("LONG VARCHAR FOR BIT DATA").setLiteral("X'", "'")
					.setDefaultValueLiteral("X'0'").setDeprecated(getDbDataTypes().getDbType(LONGVARBINARY));
		});
		// BLOB
		getDbDataTypes().addBlob("BLOB", SIZE_MAX, type -> {
		});
		// TINYINT
		getDbDataTypes().addTinyInt(type -> {
		});
		// SMALLINT
		getDbDataTypes().addSmallInt(type -> {
		});
		// INT
		getDbDataTypes().addInt(type -> {
		});
		// BIGINT
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
		// 推奨される型の登録
		getDbDataTypes().registerRecommend(CHAR, VARCHAR);
		getDbDataTypes().registerRecommend(NCHAR, NVARCHAR);
	}

	/**
	 * DB名
	 */
	@Override
	public String getProductName() {
		return "Apache Derby";
	}

	/**
	 * DB製品名(シンプル名)
	 */
	@Override
	public String getSimpleName() {
		return "derby";
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
		return new DerbyCatalogReader(this);
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
		return new DerbySqlFactoryRegistry(this);
	}

	@Override
	public DerbySqlBuilder createSqlBuilder() {
		return new DerbySqlBuilder(this);
	}
}
