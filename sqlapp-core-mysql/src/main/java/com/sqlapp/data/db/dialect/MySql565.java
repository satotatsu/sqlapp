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

import com.sqlapp.data.db.dialect.mysql.sql.MySql565SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.mysql.util.MySql565SqlBuilder;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
/**
 * MySql
 * 
 * @author SATOH
 * 
 */
public class MySql565 extends MySql564 {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -752667258642631405L;

	protected MySql565(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
	
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new MySql565SqlFactoryRegistry(this);
	}
	
	@Override
	public MySql565SqlBuilder createSqlBuilder(){
		return new MySql565SqlBuilder(this);
	}

}
