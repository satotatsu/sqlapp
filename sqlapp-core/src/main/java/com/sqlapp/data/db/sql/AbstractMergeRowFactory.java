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

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * MERGE生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractMergeRowFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractRowFactory<S> {

	@Override
	protected List<SqlOperation> getOperations(final Row row) {
		List<SqlOperation> sqlList = list();
		SqlFactory<Row> operation = this.getSqlFactoryRegistry()
				.getSqlFactory(row, SqlType.INSERT_SELECT_ROW);
		sqlList.addAll(operation.createSql(row));
		operation = this.getSqlFactoryRegistry().getSqlFactory(row,
				SqlType.UPDATE_ROW);
		sqlList.addAll(operation.createSql(row));
		return sqlList;
	}
}
