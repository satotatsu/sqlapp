/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlCatalog800Reader;
import com.sqlapp.data.db.metadata.CatalogReader;

/**
 * MySql
 * 
 * @author SATOH
 * 
 */
public class MySql800 extends MySql570 {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -752667258642631405L;

	protected MySql800(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
	
	@Override
	protected void registerDataType() {
		super.registerDataType();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new MySqlCatalog800Reader(this);
	}
}
