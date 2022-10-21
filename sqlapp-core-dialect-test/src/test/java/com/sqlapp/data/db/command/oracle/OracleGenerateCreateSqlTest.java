/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 *
 * sqlapp-core-dialect-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-dialect-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-dialect-test.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command.oracle;

import com.sqlapp.data.db.command.AbstractGenerateCreateSqlTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

public class OracleGenerateCreateSqlTest extends AbstractGenerateCreateSqlTest{

	@Override
	protected Dialect getDialect() {
		return DialectResolver.getInstance().getDialect("oracle", 11, 1);
	}
	
}
