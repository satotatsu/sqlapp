/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import java.io.InputStream;

import com.sqlapp.core.test.AbstractSqlFactoryTest;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.util.FileUtils;

public abstract class AbstractDb2SqlFactoryTest extends AbstractSqlFactoryTest {

	@Override
	protected String getProductName() {
		return "db2";
	}

	@Override
	protected int getMajorVersion() {
		return 11;
	}

	@Override
	protected int getMinorVersion() {
		return 0;
	}

	@Override
	protected String getResource(String fileName) {
		final InputStream is = getResourceAsInputStream(fileName);
		String sql = FileUtils.readText(is, "utf8");
		return sql;
	}

	@Override
	protected Dialect getDialect() {
		return DialectResolver.getInstance().getDialect(getProductName(), getMajorVersion(), getMinorVersion(),
				getRevision());
	}

	@Override
	protected InputStream getResourceAsInputStream(String fileName) {
		final InputStream is = this.getClass().getResourceAsStream(fileName);
		// final InputStream is = this.getClass().getResourceAsStream(fileName);
		return is;
	}

}
