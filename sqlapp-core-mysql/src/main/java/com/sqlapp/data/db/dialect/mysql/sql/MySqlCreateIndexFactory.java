/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.db.sql.AddTableObjectDetailFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

public class MySqlCreateIndexFactory extends
		AbstractCreateIndexFactory<MySqlSqlBuilder> 
	implements AddTableObjectDetailFactory<Index, MySqlSqlBuilder>{

	@Override
	public void addObjectDetail(final Index obj, Table table,
			MySqlSqlBuilder builder) {
		if (obj.getIndexType() == IndexType.FullText) {
			builder.fulltext();
		} else if (obj.getIndexType() == IndexType.Spatial) {
			builder.spatial();
		} else {
		}
		builder.index().space();
		if (table!=null){
			builder.name(obj, this.getOptions().isDecorateSchemaName());
		} else{
			builder.name(obj, false);
		}
		if (obj.getIndexType() == IndexType.FullText) {
		} else if (obj.getIndexType() == IndexType.Spatial) {
		} else if (obj.getIndexType() == IndexType.BTree) {
		} else if (obj.getIndexType()!=null){
			builder.space().using().space()._add(obj.getIndexType());
		} else {
		}
		if (table != null) {
			builder.on();
			builder.name(table);
		}
		builder.space()._add("(");
		builder.names(obj.getColumns());
		builder.space()._add(")");
		if (obj.getRemarks()!=null){
			builder.comment().space().sqlChar(obj.getRemarks());
		}
	}

}
