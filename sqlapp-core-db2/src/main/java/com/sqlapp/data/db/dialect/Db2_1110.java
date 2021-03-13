/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.db2.metadata.Db2_1110CatalogReader;
import com.sqlapp.data.db.dialect.db2.sql.Db2_1110SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * DB2固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Db2_1110 extends Db2_1050 {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7536431030146748711L;

	/**
	 * コンストラクタ
	 * @param nextVersionDialectSupplier
	 */
	public Db2_1110(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
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
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new Db2_1110CatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Db2_1110SqlFactoryRegistry(this);
	}
	
	@Override
	public Db2SqlBuilder createSqlBuilder(){
		return super.createSqlBuilder();
	}
}
