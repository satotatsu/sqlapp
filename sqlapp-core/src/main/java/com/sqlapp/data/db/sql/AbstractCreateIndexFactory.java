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

import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * インデックス生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateIndexFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractCreateNamedObjectFactory<Index, S> implements AddTableObjectDetailFactory<Index, S>{

	@Override
	public List<SqlOperation> createSql(final Index obj) {
		final List<SqlOperation> sqlList = list();
		if (!createIndex(obj)) {
			return sqlList;
		}
		final S builder = createSqlBuilder();
		addCreateObject(obj, builder);
		addSql(sqlList, builder, SqlType.CREATE, obj);
		return sqlList;
	}

	protected boolean createIndex(final Index obj) {
		final Table table = obj.getTable();
		if (!table.getConstraints().contains(obj.getName())) {
			return true;
		}
		return false;
	}

	@Override
	public void addCreateObject(final Index obj, final S builder) {
		builder.create();
		addObjectDetail(obj, obj.getTable(), builder);
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param obj
	 * @param table
	 * @param builder
	 */
	@Override
	public void addObjectDetail(final Index obj, final Table table, final S builder) {
		builder.unique(obj.isUnique()).index().ifNotExists(table!=null&&this.getOptions().isCreateIfNotExists()).space();
		if (table == null) {
			builder.name(obj, false);
		} else{
			builder.name(obj, this.getOptions().isDecorateSchemaName());
		}
		if (obj.getIndexType() != null&&this.getDialect().supportsIndexType(table, obj, obj.getIndexType())){
			builder.space()._add(obj.getIndexType());
		}
		if (table != null) {
			builder.on();
			if (obj.getSchemaName()!=null&&table.getSchemaName()!=null&&!CommonUtils.eq(obj.getSchemaName(), table.getSchemaName())){
				builder.name(table, true);
			} else{
				builder.name(table, false);
			}
		}
		builder.space()._add("(");
		int i=0;
		for(final ReferenceColumn col:obj.getColumns()) {
			builder.comma(i>0);
			addColumn(col,builder);
			i++;
		}
		builder.space()._add(")");
	}
	
	protected void addColumn(final ReferenceColumn col, final S builder) {
		builder.name(col);
		if (col.getOrder()!=null&&col.getOrder()!=Order.Asc) {
			builder.space()._add(col.getOrder());
		}
	}

}
