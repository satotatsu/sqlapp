/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2000IndexReader;
import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2005IndexReader;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.db.sql.AddTableObjectDetailFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

public class SqlServerCreateIndexFactory extends
		AbstractCreateIndexFactory<SqlServerSqlBuilder> 
	implements AddTableObjectDetailFactory<Index, SqlServerSqlBuilder>{

	@Override
	public void addObjectDetail(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		builder.unique(obj.isUnique());
		addIndexType(obj, table, builder);
		builder.index().space();
		if (table!=null){
			builder.name(obj, this.getOptions().isDecorateSchemaName());
		} else{
			builder.name(obj, false);
		}
		if (table != null) {
			builder.on();
			builder.name(table);
		}
		builder.space()._add("(");
		builder.names(obj.getColumns());
		builder.space()._add(")");
		addObjectDetailAfter(obj, table, builder);
	}

	protected void addIndexType(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		if (obj.getIndexType()!=null&&obj.getIndexType().isClusterd()) {
			builder.clustered();
		}
	}
	
	protected void addObjectDetailAfter(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		addIndexOption(obj, table, builder);
	}
	
	
	protected void addIndexOption(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		String key=SqlServer2000IndexReader.PAD_INDEX;
		String val=obj.getSpecifics().get(key);
		if (val!=null){
			builder.lineBreak()._add(key).eq().space()._add(val);
		}
		key=SqlServer2000IndexReader.FILL_FACTOR;
		val=obj.getSpecifics().get(key);
		if (val!=null){
			builder.lineBreak()._add(SqlServer2005IndexReader.FILLFACTOR).eq().space()._add(val);
		} else{
			val=obj.getSpecifics().get(SqlServer2005IndexReader.FILLFACTOR);
			if (val!=null){
				builder.lineBreak()._add(SqlServer2005IndexReader.FILLFACTOR).eq().space()._add(val);
			}
		}
		key=SqlServer2000IndexReader.ALLOW_ROW_LOCKS;
		val=obj.getSpecifics().get(key);
		if (val!=null){
			builder.lineBreak()._add(key).eq().space()._add(val);
		}
		key=SqlServer2000IndexReader.ALLOW_PAGE_LOCKS;
		val=obj.getSpecifics().get(key);
		if (val!=null){
			builder.lineBreak()._add(key).eq().space()._add(val);
		}
		if(obj.isCompression()){
			
		}
	}

}
