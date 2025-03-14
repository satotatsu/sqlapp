/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle. If not, see
 * &lt;http://www.gnu.org/licenses/&gt;.
 */

module com.sqlapp.core.oracle {
	requires java.xml;
	requires java.sql;
	requires com.sqlapp.core;

	exports com.sqlapp.data.db.dialect.oracle;
	exports com.sqlapp.data.db.dialect.oracle.metadata;
	exports com.sqlapp.data.db.dialect.oracle.resolver;
	exports com.sqlapp.data.db.dialect.oracle.sql to com.sqlapp.core;
}