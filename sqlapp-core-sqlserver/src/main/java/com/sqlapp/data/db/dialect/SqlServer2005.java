/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import static com.sqlapp.util.CommonUtils.LEN_1GB;
import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2005CatalogReader;
import com.sqlapp.data.db.dialect.sqlserver.sql.SqlServer2005SqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;

/**
 * SQL Server2005
 * 
 * @author satoh
 * 
 */
public class SqlServer2005 extends SqlServer2000 {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6574415406411255507L;

	protected SqlServer2005(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
		// VARCHAR
		getDbDataTypes().addVarchar(LEN_2GB)
			.addFormats("VARCHAR\\s*\\(\\s*MAX\\s*\\)").setDefaultLength(LEN_2GB)
			.setCreateFormat("VARCHAR(MAX)").setFixedLength(false);
		// NCHAR
		// NVARCHAR
		getDbDataTypes().addNVarchar(LEN_1GB)
				.addFormats("NVARCHAR\\s*\\(\\s*MAX\\s*\\)")
				.setDefaultLength(LEN_1GB)
				.setCreateFormat("NVARCHAR(MAX)").setFixedLength(false);
		// Binary
		getDbDataTypes().addVarBinary(LEN_2GB).setDefaultLength(LEN_2GB)
				.addFormats("VARBINARY\\s*\\(\\s*MAX\\s*\\)").setDefaultLength(LEN_2GB)
				.addFormats("IMAGE").setCreateFormat("VARBINARY(MAX)").setFixedLength(false)
				.setLiteral("0x", "");
		// XML
		getDbDataTypes().addSqlXml("XML").setLiteral("'", "'");
		// Time
		getDbDataTypes().addTime().setLiteral("{ts '", "'}")
				.setDefaultValueLiteral(getCurrentTimeFunction())
				.setDefaultPrecision(7).setMaxPrecision(7);
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	/**
	 * WITHステートメント再帰のサポート
	 */
	@Override
	public boolean supportsWithRecursive() {
		return true;
	}

	/**
	 * TOP句のサポート
	 */
	@Override
	public boolean supportsTop() {
		return true;
	}

	@Override
	public boolean supportsRuleOnDelete(final CascadeRule rule) {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(final CascadeRule rule) {
		return true;
	}

	/**
	 * DBスキーマのサポート
	 */
	@Override
	public boolean supportsSchema() {
		return true;
	}

	@Override
	public int hashCode() {
		return super.hashCode() + 1;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new SqlServer2005CatalogReader(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.SqlServer2000#createOperationFactoryRegistry()
	 */
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SqlServer2005SqlFactoryRegistry(this);
	}

	@Override
	public boolean setDbType(final DataType dataType, final String productDataType,
			final Long lengthOrPrecision, final Integer scale,
			final DataTypeLengthProperties<?> column) {
		final boolean bool=super.setDbType(dataType, productDataType, lengthOrPrecision, scale,
				column);
		if (bool) {
			setVarcharMax(column);
		}
		return bool;
	}

	protected void setVarcharMax(final DataTypeLengthProperties<?> column) {
		if (column.getDataType() == DataType.VARCHAR) {
			if (column.getLength() != null
					&& column.getLength().longValue() < 0) {
				column.setLength(LEN_2GB);
			}
		}
		if (column.getDataType() == DataType.NVARCHAR) {
			if (column.getLength() != null
					&& column.getLength().longValue() < 0) {
				column.setLength(LEN_1GB);
			}
		}
	}
}
