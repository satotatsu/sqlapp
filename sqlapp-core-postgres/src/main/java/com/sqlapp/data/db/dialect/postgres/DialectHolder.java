/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres;

import com.sqlapp.data.db.dialect.Dialect;

public class DialectHolder {
	public final static Dialect postgreSQL160 = new Postgres160(() -> null);
	public final static Dialect postgreSQL150 = new Postgres150(() -> postgreSQL160);
	public final static Dialect postgreSQL140 = new Postgres140(() -> postgreSQL150);
	public final static Dialect postgreSQL130 = new Postgres130(() -> postgreSQL140);
	public final static Dialect postgreSQL120 = new Postgres120(() -> postgreSQL130);
	public final static Dialect postgreSQL110 = new Postgres110(() -> postgreSQL120);
	public final static Dialect postgreSQL100 = new Postgres100(() -> postgreSQL110);
	public final static Dialect postgreSQL96 = new Postgres96(() -> postgreSQL100);
	public final static Dialect postgreSQL95 = new Postgres95(() -> postgreSQL96);
	public final static Dialect postgreSQL94 = new Postgres94(() -> postgreSQL95);
	public final static Dialect postgreSQL93 = new Postgres93(() -> postgreSQL94);
	public final static Dialect postgreSQL92 = new Postgres92(() -> postgreSQL93);
	public final static Dialect postgreSQL91 = new Postgres91(() -> postgreSQL92);
	public final static Dialect postgreSQL90 = new Postgres90(() -> postgreSQL91);
	public final static Dialect postgreSQL84 = new Postgres84(() -> postgreSQL90);
	public final static Dialect postgreSQL83 = new Postgres83(() -> postgreSQL84);
	public final static Dialect postgreSQL82 = new Postgres83(() -> postgreSQL83);
	public final static Dialect defaultDialect = new Postgres(() -> postgreSQL82);
}
