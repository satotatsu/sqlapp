/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle;

import com.sqlapp.data.db.dialect.Dialect;

public class DialectHolder {

	public final static Dialect oracle23cDialect = new Oracle23ai(() -> null);
	public final static Dialect oracle12cDialect = new Oracle12c(() -> oracle23cDialect);
	public final static Dialect oracle11gR2Dialect = new Oracle11gR2(() -> oracle12cDialect);
	public final static Dialect oracle11gDialect = new Oracle11g(() -> oracle11gR2Dialect);
	public final static Dialect oracle10gDialect = new Oracle10g(() -> oracle11gDialect);
	public final static Dialect defaultDialect = new Oracle(() -> oracle10gDialect);

}
