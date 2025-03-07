/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres83CatalogReader;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres83SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.util.GeometryUtils;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

public class Postgres83 extends Postgres82 {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1065448798064099388L;

	protected Postgres83(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
		// オプションだから微妙
		// GEOGRAPHY
		// getDataTypes().addGeography();
		// GEOMETRY
		// GEOMETRY
		GeometryUtils.run(new Runnable(){
			@Override
			public void run() {
				getDbDataTypes().addGeometry().setJdbcTypeHandler(
						new PostgresGeometryJdbcTypeHandler());
			}
		});
	}

	/**
	 * WITHステートメントのサポート
	 */
	@Override
	public boolean supportsWith() {
		return true;
	}

	/**
	 * WITHステートメント再帰のサポート
	 */
	@Override
	public boolean supportsWithRecursive() {
		return false;
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
		return new Postgres83CatalogReader(this);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Dialect#createDbOperationFactory()
	 */
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Postgres83SqlFactoryRegistry(this);
	}
}
