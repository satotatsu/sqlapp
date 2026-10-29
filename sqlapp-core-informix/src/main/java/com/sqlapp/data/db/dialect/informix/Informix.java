/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-informix.
 *
 * sqlapp-core-informix is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-informix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-informix.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.informix;

import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.informix.metadata.InformixCatalogReader;
import com.sqlapp.data.db.dialect.informix.sql.InformixSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Informix固有情報クラス
 * 
 * @author SATOH
 *
 */
public class Informix extends Dialect {
	/** Actual owner when it differs from the containing table's schema. */
	public static final String OBJECT_OWNER = "INFORMIX_OBJECT_OWNER";
	/**
	 * Ordered catalog ALTER history applied after a routine's CREATE definition.
	 */
	public static final String ROUTINE_ALTER = "INFORMIX_ROUTINE_ALTER";
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 3840025482658828284L;

	protected Informix(Supplier<Dialect> nextVersionDialectSupplier) {
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
		getDbDataTypes().addVarchar(255);
		// LONG VARCHAR
		getDbDataTypes().addLongVarchar("LVARCHAR", 32739);
		// CLOB
		getDbDataTypes().addClob("TEXT", LEN_2GB, type -> {
			type.setCreateFormat("TEXT");
		});
		// NCHAR
		getDbDataTypes().addNChar(255);
		// NVARCHAR
		getDbDataTypes().addNVarchar(255);
		// Binary
		getDbDataTypes().addBinary(LEN_2GB, type -> {
			type.addColumnTypeMatcher("BYTE");
			type.setCreateFormat("BYTE");
		});
		// Boolean
		getDbDataTypes().addBoolean(type -> {
			type.setDefaultValueLiteral("'f'");
		});
		// Int16
		getDbDataTypes().addSmallInt(type -> {
		});
		// Int32
		getDbDataTypes().addInt("INTEGER", type -> {
		});
		// Int64
		getDbDataTypes().addBigInt("INT8", type -> {
		});
		// Serial
		getDbDataTypes().addSerial(type -> {
			type.addColumnTypeMatcher("SERIAL");
			type.setCreateFormat("SERIAL");
		});
		// BigSerial
		getDbDataTypes().addBigSerial(type -> {
			type.addColumnTypeMatcher("SERIAL8");
			type.setCreateFormat("SERIAL8");
		});
		// UUID
		getDbDataTypes().addUUID(type -> {
			type.setAsVarcharType();
		});
		// Single
		getDbDataTypes().addReal("SMALLFLOAT", type -> {
		});
		// Double
		getDbDataTypes().addDouble("FLOAT", type -> {
		});
		// Date
		getDbDataTypes().addDate(type -> {
			type.setDefaultValueLiteral(getCurrentDateFunction());
		});
		// DateTime
		getDbDataTypes().addDateTime("DATETIME YEAR TO SECOND", type -> {
			type.setDefaultValueLiteral(getCurrentDateTimeFunction());
		});
		// Time
		getDbDataTypes().addTime("DATETIME HOUR TO SECOND", type -> {
			type.setDefaultValueLiteral(getCurrentTimeFunction());
		});
		// Time
		getDbDataTypes().addTimestamp("DATETIME YEAR TO FRACTION", type -> {
			type.setCreateFormat("DATETIME YEAR TO FRACTION(5)").setDefaultValueLiteral(getCurrentTimestampFunction());
		});
		// DECIMAL without scale uses decimal floating-point semantics.
		getDbDataTypes().addDecimalFloat("DECIMAL");
		((com.sqlapp.data.db.datatype.DecimalFloatType) getDbDataTypes()
				.getDbTypeStrict(com.sqlapp.data.db.datatype.DataType.DECIMALFLOAT)).setMaxPrecision(32)
				.setDefaultPrecision(16);
		// Fixed-scale decimal
		getDbDataTypes().addDecimal(type -> {
			type.setMaxPrecision(32).setMaxScale(32);
		});
		getDbDataTypes().addMoney("MONEY", type -> {
		});
		getDbDataTypes().addIntervalYearToMonth(type -> {
		});
		getDbDataTypes().addIntervalDayToSecond(type -> {
		});
	}

	/**
	 * DB製品名
	 */
	@Override
	public String getProductName() {
		return "Informix Dynamic Server";
	}

	@Override
	public java.util.Optional<com.sqlapp.data.db.datatype.util.TypeInformation> matchDbType(
			final com.sqlapp.data.db.datatype.DataType dataType, final String productDataType, final Long precision,
			final Integer scale) {
		if (productDataType != null && "DECIMAL".equalsIgnoreCase(productDataType.strip()) && precision != null
				&& scale != null) {
			final String declaration = scale == 255 ? "DECIMAL(" + precision + ")"
					: "DECIMAL(" + precision + "," + scale + ")";
			return super.matchDbType(dataType, declaration, precision, scale == 255 ? null : scale);
		}
		return super.matchDbType(dataType, productDataType, precision, scale);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "Informix";
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	/**
	 * TOP句のサポート
	 */
	@Override
	public boolean supportsTop() {
		return false;
	}

	@Override
	public String getIdentitySelectString() {
		return "SELECT DBINFO('sqlca.sqlerrd1') FROM systables WHERE tabid=1";
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	@Override
	public boolean supportsBatchExecuteGeneratedKeys() {
		return true;
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new InformixCatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new InformixSqlFactoryRegistry(this);
	}

	@Override
	public String getIdentityColumnString() {
		return "SERIAL";
	}

	@Override
	public String getCurrentDateFunction() {
		return "TODAY";
	}

	@Override
	public String getCurrentDateTimeFunction() {
		return "CURRENT YEAR TO SECOND";
	}

	@Override
	public String getCurrentTimeFunction() {
		return "CURRENT HOUR TO SECOND";
	}

	@Override
	public String getCurrentTimestampFunction() {
		return "CURRENT YEAR TO FRACTION(5)";
	}

	@Override
	public String getSelectDummyTableName() {
		return "sysmaster:sysdual";
	}

	@Override
	public AbstractSqlBuilder<?> createSqlBuilder() {
		return new InformixSqlBuilder(this);
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
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}

	@Override
	public boolean isDdlRollbackable() {
		return true;
	}
}
