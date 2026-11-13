/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 *
 * sqlapp-core-duckdb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-duckdb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-duckdb.  If not, see <http://www.gnu.org/licenses/>.
 */

module com.sqlapp.core.duckdb {
	requires java.xml;
	requires java.sql;
	requires com.sqlapp.core;

	exports com.sqlapp.data.db.dialect.duckdb;
	exports com.sqlapp.data.db.dialect.duckdb.metadata;
	exports com.sqlapp.data.db.dialect.duckdb.resolver;
	exports com.sqlapp.data.db.dialect.duckdb.util;
	exports com.sqlapp.data.db.dialect.duckdb.sql to com.sqlapp.core;
}
