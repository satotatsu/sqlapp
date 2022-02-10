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

import java.util.Collection;
import java.util.List;

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObjectDifference;

public interface SqlFactory<T extends DbCommonObject<?>> {

	/**
	 * SQLを取得します
	 * 
	 */
	default List<SqlOperation> createSql(){
		throw new UnsupportedOperationException();
	}

	/**
	 * SQLを取得します
	 * 
	 * @param obj
	 */
	List<SqlOperation> createSql(T obj);

	/**
	 * SQLを取得します
	 * 
	 * @param obj
	 */
	List<SqlOperation> createSql(Collection<T> obj);

	/**
	 * SQLを取得します
	 * 
	 * @param obj
	 */
	List<SqlOperation> createDiffSql(DbObjectDifference obj);

	/**
	 * SQLを取得します
	 * 
	 * @param obj
	 */
	List<SqlOperation> createDiffSql(Collection<DbObjectDifference> c);

	/**
	 * @return the sqlFactoryRegistry
	 */
	SqlFactoryRegistry getSqlFactoryRegistry();

	Options getOptions();

	void setOptions(Options options);

	public static final String COMMAND_METHOD = "createSql";
}
