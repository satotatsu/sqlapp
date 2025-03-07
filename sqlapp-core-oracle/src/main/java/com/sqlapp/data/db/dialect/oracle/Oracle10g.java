/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.metadata.Oracle10gCatalogReader;
import com.sqlapp.data.db.dialect.oracle.sql.Oracle10gSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.util.GeometryUtils;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * Oracle10g固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Oracle10g extends Oracle {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5104596865028027867L;

	/**
	 * コンストラクタ
	 */
	protected Oracle10g(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
		// Single
		getDbDataTypes().addReal("BINARY_FLOAT");
		// Double
		getDbDataTypes().addDouble("BINARY_DOUBLE");
		// GEOMETRY
		GeometryUtils.run(new Runnable(){
			@Override
			public void run() {
				getDbDataTypes().addGeometry("SDO_GEOMETRY")
						.setJdbcTypeHandler(new OracleGeometryJdbcTypeHandler())
						.setJdbcType(java.sql.JDBCType.STRUCT);
			}
		});
	}

	@Override
	public int hashCode() {
		return getProductName().hashCode() + 1;
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
		return new Oracle10gCatalogReader(this);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Dialect#createDbOperationFactory()
	 */
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Oracle10gSqlFactoryRegistry(this);
	}
}
