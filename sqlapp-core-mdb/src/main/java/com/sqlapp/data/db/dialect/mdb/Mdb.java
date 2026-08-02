/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mdb.
 *
 * sqlapp-core-mdb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mdb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mdb.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mdb;

import static com.sqlapp.util.CommonUtils.LEN_1GB;
import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mdb.metadata.MdbCatalogReader;
import com.sqlapp.data.db.dialect.mdb.sql.MdbSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;

/**
 * Microsoft JET固有情報クラス
 * 
 * @author SATOH
 *
 */
public class Mdb extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6002159179591648985L;

	/**
	 * コンストラクタ
	 */
	protected Mdb(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar("TEXT", 255, type -> {
			type.setLiteral("'", "'");
		});
		// VARCHAR
		getDbDataTypes().addVarchar("TEXT", 255, type -> {
			type.setLiteral("'", "'");
		});
		// LONGVARCHAR
		getDbDataTypes().addLongVarchar("MEMO", LEN_1GB, type -> {
			type.setColumnTypeMatcher("LONGTEXT", "MEMO");
			type.setLiteral("'", "'").setCreateFormat("MEMO");
		});
		// NCHAR
		getDbDataTypes().addNChar("TEXT", 255, type -> {
			type.setLiteral("'", "'");
		});
		// NVARCHAR
		getDbDataTypes().addNVarchar("TEXT", 255, type -> {
			type.setLiteral("'", "'");
		});
		// LONGNVARCHAR
		getDbDataTypes().addLongNVarchar("MEMO", LEN_1GB, type -> {
			type.setColumnTypeMatcher("LONGTEXT", "MEMO");
			type.setLiteral("'", "'").setCreateFormat("MEMO");
		});
		// NCLOB
		getDbDataTypes().addNClob("MEMO", LEN_1GB, type -> {
			type.setCreateFormat("MEMO");
		});
		// BLOB
		getDbDataTypes().addBlob("OLE", LEN_2GB, type -> {
			type.setColumnTypeMatcher("OLE", "OLEOBJECT");
			type.setCreateFormat("OLE").setLiteral("0x", "");
		});
		// Boolean
		getDbDataTypes().addBoolean(type -> {
			type.addColumnTypeMatcher("LOGICAL", "LOGICAL1", "YESNO");
		});
		// SByte
		getDbDataTypes().addTinyInt("BYTE", type -> {
		});
		// Int16
		getDbDataTypes().addSmallInt("SHORT", type -> {
			type.addColumnTypeMatcher("INTEGER2", "SMALLINT");
		});
		// Int32
		getDbDataTypes().addInt("LONG", type -> {
			type.addColumnTypeMatcher("INTEGER", "INT", "INTEGER4");
		});
		// Int64
		getDbDataTypes().addBigInt(type -> {
			type.setPetternColumnTypeMatcher("DECIMAL\\s*\\(\\s*19\\s*,\\s*0\\s*\\)");
			type.setCreateFormat("DECIMAL(19,0)");
		});
		// Serial
		getDbDataTypes().addSerial("AUTOINCREMENT", type -> {
		});
		// GUID
		getDbDataTypes().addUUID("UNIQUEIDENTIFIER", type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral("NEWID()");
		});
		// Single
		getDbDataTypes().addReal("SINGLE", type -> {
			type.addColumnTypeMatcher("FLOAT4", "IEEESINGLE", "REAL");
		});
		// Double
		getDbDataTypes().addDouble(type -> {
			type.addColumnTypeMatcher("FLOAT", "FLOAT8", "IEEEDOUBLE", "NUMBER", "NUMERIC");
		});
		// Date
		getDbDataTypes().addDateTime("DATETIME", type -> {
			type.setLiteral("#", "#").setJdbcTypeHandler(new DateTimeTypeHandler(DataType.DATETIME.getJdbcType(),
					Converters.getDefault().getConverter(java.util.Date.class)));
		});
		// Time
		getDbDataTypes().addTime("TIME", type -> {
			type.setLiteral("#", "#");
		});
		// Money
		getDbDataTypes().addMoney("CURRENCY", type -> {
			type.addColumnTypeMatcher("MONEY");
		});
		// Decimal
		getDbDataTypes().addDecimal(type -> {
			type.setMaxPrecision(28).setDefaultPrecision(15).setDefaultScale(0);
		});
		// Numeric
		getDbDataTypes().addNumeric(type -> {
			type.setMaxPrecision(28).setDefaultPrecision(15).setDefaultScale(0);
		});
	}

	/**
	 * DB名
	 */
	@Override
	public String getProductName() {
		return "MS Jet";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "msjet";
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
		return "select @@IDENTITY";
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	@Override
	public String getIdentityColumnString() {
		return "COUNTER";
	}

	@Override
	public String getCurrentDateFunction() {
		return "DATE()";
	}

	@Override
	public String getCurrentDateTimeFunction() {
		return "NOW()";
	}

	@Override
	public String getCurrentTimestampFunction() {
		return "NOW()";
	}

	@Override
	public String getCurrentTimeFunction() {
		return "TIME()";
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return false;
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
	public boolean supportsDropCascade() {
		return false;
	}

	@Override
	public boolean supportsCascadeDelete() {
		return true;
	}

	@Override
	public boolean supportsRuleOnDelete(final CascadeRule rule) {
		return rule == CascadeRule.None || rule == CascadeRule.Cascade
				|| rule == CascadeRule.SetNull;
	}

	@Override
	public boolean supportsCascadeUpdate() {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(final CascadeRule rule) {
		return rule == CascadeRule.None || rule == CascadeRule.Cascade;
	}

	@Override
	public boolean storesMixedCaseIdentifiers() {
		return true;
	}

	/**
	 * インデックス名のテーブルスコープ
	 */
	public boolean supportsIndexNameTableScope() {
		return true;
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new MdbCatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new MdbSqlFactoryRegistry(this);
	}

	@Override
	public MdbSqlBuilder createSqlBuilder() {
		return new MdbSqlBuilder(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}
}
