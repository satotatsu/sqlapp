/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-test.
 *
 * sqlapp-core-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-test.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.core.test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.test.AbstractTest;

/**
 * Operationテスト
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class AbstractSqlFactoryTest extends AbstractTest {
	
	protected Dialect dialect = DialectResolver.getInstance().getDialect(productName(), getMajorVersion(), getMinorVersion(), getRevision());

	protected SqlFactoryRegistry sqlFactoryRegistry = createSqlFactoryRegistry();
	
	protected abstract String productName();

	protected abstract int getMajorVersion();

	protected abstract int getMinorVersion();
	
	protected int getRevision() {
		return 0;
	}
	
	protected SqlFactoryRegistry createSqlFactoryRegistry(){
		return dialect.createSqlFactoryRegistry();
	}
}
