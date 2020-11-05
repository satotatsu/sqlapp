/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-derby.
 *
 * sqlapp-core-derby is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-derby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-derby.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.derby.sql;

import com.sqlapp.test.AbstractSqlFactoryTest;

public abstract class AbstractDerbyOperationFactoryTest extends AbstractSqlFactoryTest{

	@Override
	protected String productName() {
		return "Apache derby";
	}

	@Override
	protected int getMajorVersion() {
		return 10;
	}

	@Override
	protected int getMinorVersion() {
		return 0;
	}

}
