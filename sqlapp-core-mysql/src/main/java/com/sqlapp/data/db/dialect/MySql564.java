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
package com.sqlapp.data.db.dialect;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.mysql.metadata.MySqlCatalog564Reader;
import com.sqlapp.data.db.metadata.CatalogReader;

/**
 * MySql
 * 
 * @author SATOH
 * 
 */
public class MySql564 extends MySql {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3303539585921104825L;

	protected MySql564(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
		// DateTime
		getDbDataTypes().addTimestamp("DATETIME").setCreateFormat("DATETIME({p})").addFormats("DATETIME\\s*\\([0-9]*\\)\\s*").setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentDateTimeFunction()).setDefaultPrecision(0).setMaxPrecision(6);
		// Time
		getDbDataTypes().addTime().addFormats("TIME\\s*\\([0-9]*\\)\\s*").setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimeFunction()).setDefaultPrecision(0).setMaxPrecision(6);
		// Timestamp
		getDbDataTypes().addTimestampVersion("TIMESTAMP").addFormats("TIMESTAMP\\s*\\([0-9]*\\)\\s*").setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimestampFunction()).setDefaultPrecision(0).setMaxPrecision(6);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new MySqlCatalog564Reader(this);
	}
}
