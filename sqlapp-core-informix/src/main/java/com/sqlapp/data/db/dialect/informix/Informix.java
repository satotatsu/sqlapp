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

/**
 * Informix固有情報クラス
 * 
 * @author SATOH
 *
 */
public class Informix extends Dialect {
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
		});
		// BigSerial
		getDbDataTypes().addBigSerial(type -> {
			type.setCreateFormat("SERIAL8").setFormats("SERIAL8");
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
			type.setDefaultValueLiteral(getCurrentDateFunction());
		});
		// Time
		getDbDataTypes().addTime("DATETIME HOUR TO SECOND", type -> {
			type.setDefaultValueLiteral(getCurrentTimeFunction());
		});
		// Time
		getDbDataTypes().addTimestamp("DATETIME YEAR TO FRACTION", type -> {
			type.setCreateFormat("DATETIME YEAR TO FRACTION").setDefaultValueLiteral(getCurrentTimestampFunction());
		});
		// Decimal
		getDbDataTypes().addDecimal(type -> {
			type.setMaxPrecision(32).setMaxScale(32);
		});
	}

	/**
	 * DB製品名
	 */
	@Override
	public String getProductName() {
		return "Informix Dynamic Server";
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

	@Override
	public char getCloseQuote() {
		return ']';
	}

	@Override
	public char getOpenQuote() {
		return '[';
	}

	@Override
	public String getCurrentDateFunction() {
		return "CAST(CONVERT(VARCHAR(10),CURRENT_TIMESTAMP,121) AS DATETIME)";
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
