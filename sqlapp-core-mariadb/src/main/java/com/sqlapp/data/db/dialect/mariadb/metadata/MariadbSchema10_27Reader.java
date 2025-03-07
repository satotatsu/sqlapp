/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mariadb.
 *
 * sqlapp-core-mariadb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mariadb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mariadb.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mariadb.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlSchema564Reader;
import com.sqlapp.data.db.metadata.TableReader;

/**
 * Mariadb 10.0 Schema Reader
 * 
 * @author satoh
 * 
 */
public class MariadbSchema10_27Reader extends MySqlSchema564Reader {

	public MariadbSchema10_27Reader(Dialect dialect) {
		super(dialect);
	}
	
	@Override
	protected TableReader newTableReader() {
		return new MariadbTable10_27Reader(this.getDialect());
	}

}
