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
import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2008CatalogReader;
import com.sqlapp.data.db.dialect.sqlserver.sql.SqlServer2008SqlBuilder;
import com.sqlapp.data.db.dialect.sqlserver.sql.SqlServer2008SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.util.GeometryUtils;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * SQL Server2008
 * 
 * @author satoh
 * 
 */
public class SqlServer2008 extends SqlServer2005 {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5751173741801001354L;

	protected SqlServer2008(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
		// Date
		getDbDataTypes().addDate().setLiteral("'", "'")
				.setDefaultValueLiteral("CONVERT (date, "+getCurrentTimestampFunction()+")");
		// DateTimeOffset
		getDbDataTypes().addTimestampWithTimeZoneType("DATETIMEOFFSET").setLiteral("'", "'")
			.setDefaultPrecision(7).setMaxPrecision(7)
			.setCreateFormat("DATETIMEOFFSET(", ")");
		// Timestamp
		getDbDataTypes().addTimestamp("DATETIME2").setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimestampFunction())
				.setDefaultPrecision(7).setMaxPrecision(7).setCreateFormat("DATETIME2(", ")");
		// DateTimeOffset
		getDbDataTypes().addTimestampWithTimeZoneType("DATETIMEOFFSET")
			.setDefaultPrecision(7).setMaxPrecision(7)
			.setCreateFormat("DATETIMEOFFSET(", ")");
		GeometryUtils.run(new Runnable(){
			@Override
			public void run() {
				// GEOGRAPHY
				getDbDataTypes().addGeography().setJdbcTypeHandler(
						new SqlServerGeometryJdbcTypeHandler());
				// GEOMETRY
				getDbDataTypes().addGeometry().setJdbcTypeHandler(
						new SqlServerGeometryJdbcTypeHandler());
			}
		});
	}

	@Override
	public boolean supportsMerge() {
		return true;
	}

	@Override
	public int hashCode() {
		return super.hashCode() + 1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.SqlServer2005#equals(java.lang.Object)
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
		return new SqlServer2008CatalogReader(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.SqlServer2005#createOperationFactoryRegistry()
	 */
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SqlServer2008SqlFactoryRegistry(this);
	}
	
	@Override
	public SqlServer2008SqlBuilder createSqlBuilder(){
		return new SqlServer2008SqlBuilder(this);
	}
	
	@Override
	public boolean supportsColumnFormula() {
		return false;
	}
}
