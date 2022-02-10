/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.sql;

import java.util.Collections;
import java.util.List;

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * 何も処理を行わないダミーのOperation
 * 
 * @author tatsuo satoh
 * 
 */
public class EmptySqlFactory<T extends DbCommonObject<?>> extends AbstractSqlFactory<T,AbstractSqlBuilder<?>> {

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.sql.SqlFactory#createSql()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<SqlOperation> createSql(){
		return (List<SqlOperation>) Collections.EMPTY_LIST;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.sql.SqlFactory#createSql(com.sqlapp.data.schemas.DbCommonObject)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<SqlOperation> createSql(T obj) {
		return (List<SqlOperation>) Collections.EMPTY_LIST;
	}

}
