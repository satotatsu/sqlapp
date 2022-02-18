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

import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2017CatalogReader;
import com.sqlapp.data.db.dialect.sqlserver.sql.SqlServer2017SqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * SQL SqlServer2017
 * 
 * @author satoh
 * 
 */
public class SqlServer2017 extends SqlServer2016 {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5751173741801001354L;

	protected SqlServer2017(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
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
	
	@Override
	public CatalogReader getCatalogReader() {
		return new SqlServer2017CatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SqlServer2017SqlFactoryRegistry(this);
	}
}
