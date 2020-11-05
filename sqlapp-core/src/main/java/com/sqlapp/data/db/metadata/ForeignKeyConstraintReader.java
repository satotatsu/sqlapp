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
package com.sqlapp.data.db.metadata;

import static com.sqlapp.util.CommonUtils.first;

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * 外部制約読み込み抽象クラス
 * 
 * @author satoh
 * 
 */
public abstract class ForeignKeyConstraintReader extends
		ConstraintReader<ForeignKeyConstraint> {

	protected ForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * 外部キー制約のカラムの設定
	 * 
	 * @param tColMap
	 * @param list
	 */
	protected void setForeignKeyConstraintColumns(
			TripleKeyMap<String, String, String, FlexList<ColumnPair>> tColMap,
			List<ForeignKeyConstraint> list) {
		for (ForeignKeyConstraint c : list) {
			FlexList<ColumnPair> colList = tColMap.get(c.getCatalogName(),
					c.getSchemaName(), c.getName());
			ColumnPair cPair = first(colList);
			Table rTable = new Table(cPair.refTableName);
			rTable.setCatalogName(cPair.refCatalogName);
			rTable.setSchemaName(cPair.refSchemaName);
			Column[] columns = new Column[colList.size()];
			Column[] rColumns = new Column[colList.size()];
			for (int i = 0; i < columns.length; i++) {
				cPair = colList.get(i);
				columns[i] = createColumn(c.getCatalogName(),
						c.getSchemaName(), c.getTableName(), cPair.columnName);
				rColumns[i] = createColumn(cPair.refCatalogName,
						cPair.refSchemaName, cPair.refTableName,
						cPair.refColumnName);
			}
			c.setColumns(columns);
			c.setRelatedColumns(rColumns);
		}
	}

	protected Column createColumn(String catalogName, String schemaName,
			String tableName, String columnName) {
		Column column = new Column(columnName);
		column.setCatalogName(catalogName);
		column.setSchemaName(schemaName);
		column.setTableName(tableName);
		return column;
	}
}
