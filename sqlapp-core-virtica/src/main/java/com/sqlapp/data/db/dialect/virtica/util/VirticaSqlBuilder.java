/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.virtica.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * DB2用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class VirticaSqlBuilder extends AbstractSqlBuilder<VirticaSqlBuilder> {

	public VirticaSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	
	@Override
	public VirticaSqlBuilder clone(){
		return (VirticaSqlBuilder)super.clone();
	}
	
}
