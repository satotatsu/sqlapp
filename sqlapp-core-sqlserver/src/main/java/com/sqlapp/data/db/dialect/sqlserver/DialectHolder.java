/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver;

import com.sqlapp.data.db.dialect.Dialect;

public class DialectHolder {
	public final static Dialect defaultDialect2019 = new SqlServer2019(() -> null);
	public final static Dialect defaultDialect2017 = new SqlServer2017(() -> defaultDialect2019);
	public final static Dialect defaultDialect2016Sp1 = new SqlServer2016Sp1(() -> defaultDialect2017);
	public final static Dialect defaultDialect2016 = new SqlServer2016(() -> defaultDialect2016Sp1);
	public final static Dialect defaultDialect2014 = new SqlServer2014(() -> defaultDialect2016);
	public final static Dialect defaultDialect2012 = new SqlServer2012(() -> defaultDialect2014);
	public final static Dialect defaultDialect2008R2 = new SqlServer2008R2(() -> defaultDialect2012);
	public final static Dialect defaultDialect2008 = new SqlServer2008(() -> defaultDialect2008R2);
	public final static Dialect defaultDialect2005 = new SqlServer2005(() -> defaultDialect2008);
	public final static Dialect defaultDialect2000 = new SqlServer2000(() -> defaultDialect2005);
}
