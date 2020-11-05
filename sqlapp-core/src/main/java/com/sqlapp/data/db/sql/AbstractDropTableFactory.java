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
import java.util.Comparator;
import java.util.List;

import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FlexList;

/**
 * テーブル削除クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDropTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractDropNamedObjectFactory<Table, S> {

	@Override
	protected void addDropObject(Table obj, S builder) {
		builder.drop().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		if (this.getDialect().supportsDropCascade()) {
			builder.cascade().constraints();
		}
	}
	
	@Override
	protected List<Table> sort(List<Table> c){
		return SchemaUtils.getNewSortedTableList(c, Table.TableOrder.DROP.getComparator());
	}
	
	@Override
	protected List<DbObjectDifference> sortDbObjectDifference(
			List<DbObjectDifference> list) {
		return sort(list, Table.TableOrder.DROP.getComparator());
	}
	
	private List<DbObjectDifference> sort(
			List<DbObjectDifference> list, Comparator<Table> comparator) {
		List<Table> tables = CommonUtils.list(list.size());
		for (DbObjectDifference dbObjectDifference : list) {
			if (dbObjectDifference.getOriginal()!=null) {
				tables.add((Table) dbObjectDifference.getOriginal());
			}
		}
		Collections.sort(tables, comparator);
		List<DbObjectDifference> result = new FlexList<DbObjectDifference>();
		for (int i = 0; i < tables.size(); i++) {
			Table table = tables.get(i);
			for (DbObjectDifference dbObjectDifference : list) {
				if (table == dbObjectDifference.getOriginal()) {
					result.add(dbObjectDifference);
				}
			}
		}
		return result;
	}
}
