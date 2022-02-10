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

import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.PublicSynonym;
import com.sqlapp.data.schemas.TypeBody;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * オブジェクト削除クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDropNamedObjectFactory<T extends AbstractNamedObject<?>, S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<T, S> {

	@Override
	public List<SqlOperation> createSql(T obj) {
		S builder = createSqlBuilder();
		addDropObject(obj, builder);
		List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, SqlType.DROP, obj);
		return sqlList;
	}

	protected void addDropObject(T obj, S builder) {
		builder.drop().space();
		builder._add(obj.getClass().getSimpleName().toUpperCase());
		if (obj instanceof AbstractSchemaObject){
			builder.name((AbstractSchemaObject<?>)obj, this.getOptions().isDecorateSchemaName());
		} else{
			builder.name(obj);
		}
	}

	protected void appendName(T obj, S builder){
		if (obj instanceof Mview){
			builder.materialized().view();
		} else if (obj instanceof TypeBody){
			builder.type().body();
		} else if (obj instanceof PublicSynonym){
			builder._add("PUBLIC").synonym();
		} else{
			builder._add(obj.getClass().getSimpleName().toUpperCase());
		}
	}
}
