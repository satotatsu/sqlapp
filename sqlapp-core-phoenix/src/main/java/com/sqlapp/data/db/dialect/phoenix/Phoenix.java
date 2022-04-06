/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-phoenix.
 *
 * sqlapp-core-phoenix is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-phoenix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-phoenix.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.phoenix;

import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcCatalogReader;
import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * Phoenix固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Phoenix extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8635862003765087520L;
	private static final long SIZE_MAX = LEN_2GB - 1;

	/**
	 * コンストラクタ
	 * @param nextVersionDialectSupplier 次のバージョンのDialect
	 */
	protected Phoenix(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(SIZE_MAX).setDefaultLength(1)
				.setCharset("UTF-16");
		// VARCHAR
		getDbDataTypes().addVarchar(SIZE_MAX).setCharset("UTF-8");
		// BOOLEAN
		getDbDataTypes().addBoolean();
		// BINARY
		getDbDataTypes()
				.addBinary(SIZE_MAX);
		// VARBINARY
		getDbDataTypes()
				.addVarBinary(SIZE_MAX);
		// TINYINT
		getDbDataTypes().addTinyInt();
		// UNSIGNED TINYINT
		getDbDataTypes().addUTinyInt("UNSIGNED_TINYINT").setType(Byte.class);
		// SMALLINT
		getDbDataTypes().addSmallInt();
		// UNSIGNED SMALLINT
		getDbDataTypes().addUSmallInt("UNSIGNED_SMALLINT").setType(Short.class);
		// INT
		getDbDataTypes().addInt("INTEGER");
		// UNSIGNED INT
		getDbDataTypes().addUInt("UNSIGNED_INT").setType(Integer.class);
		// BIGINT
		getDbDataTypes().addBigInt();
		// UNSIGNED_LONG
		getDbDataTypes().addUBigInt("UNSIGNED_LONG").setType(Long.class);
		// REAL
		getDbDataTypes().addReal("FLOAT");
		// Double
		getDbDataTypes().addDouble();
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
	}

	/**
	 * DB名
	 */
	@Override
	public String getProductName() {
		return "Apache Phoenix";
	}

	/**
	 * DB製品名(シンプル名)
	 */
	@Override
	public String getSimpleName() {
		return "Phoenix";
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new JdbcCatalogReader(this);
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
		return new PhoenixSqlFactoryRegistry(this);
	}
	
	@Override
	public PhoenixSqlBuilder createSqlBuilder(){
		return new PhoenixSqlBuilder(this);
	}
}
