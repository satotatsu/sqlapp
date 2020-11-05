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

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * オブジェクト作成 生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateNamedObjectFactory<T extends AbstractNamedObject<?>, S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<T, S> {

	@Override
	public List<SqlOperation> createSql(final T obj) {
		List<SqlOperation> sqlList = list();
		S builder = createSqlBuilder();
		addCreateObject(obj, builder);
		addSql(sqlList, builder, SqlType.CREATE, obj);
		addOptions(obj, sqlList);
		addOtherDefinitions(obj, sqlList);
		return sqlList;
	}

	protected void addCreateObject(final T obj, S builder) {
		String text = toString(obj.getDefinition());
		if (!isEmpty(text)) {
			builder._add(text);
		} else {
			builder.create().space();
			builder._add(obj.getClass().getSimpleName().toUpperCase());
			if (obj instanceof AbstractSchemaObject){
				builder.name((AbstractSchemaObject<?>)obj, this.getOptions().isDecorateSchemaName());
			} else{
				builder.name(obj);
			}
			builder.as()._add(text);
		}
	}

	protected void addOptions(final T obj, List<SqlOperation> sqlList) {

	}
	protected void addOtherDefinitions(final T table, List<SqlOperation> result){
		
	}
}
