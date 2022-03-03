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

import java.util.Map;

import com.sqlapp.data.db.dialect.sqlserver.metadata.SqlServer2000IndexReader;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerIndexOptions;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.db.sql.AddTableObjectDetailFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class SqlServerCreateIndexFactory extends
		AbstractCreateIndexFactory<SqlServerSqlBuilder> 
	implements AddTableObjectDetailFactory<Index, SqlServerSqlBuilder>{

	@Override
	public void addObjectDetail(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		builder.unique(obj.isUnique());
		addIndexType(obj, table, builder);
		builder.index().space();
		builder.name(obj, false);
		if (table != null) {
			builder.on();
			builder.name(table);
		}
		builder.space().brackets(()->{
			builder.names(obj.getColumns());
			builder.space();
		});
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
		addIndexWithOption(obj, table, builder);
	}
	
	protected void addIndexWithOption(final Index obj, final Table table,
			final SqlServerSqlBuilder builder) {
		final Map<String,String> map=createIndexWithOption(obj, table);
		if (!map.isEmpty()) {
			builder.lineBreak();
			builder.with().space().brackets(()->{
				builder.indent(()->{
					final boolean[] first=new boolean[]{true};
					map.forEach((k,v)->{
						builder.lineBreak().comma(!first[0])._add(k).eq().space()._add(v);
						first[0]=false;
					});
				});
				builder.lineBreak();
			});
		}
	}

	protected Map<String,String> createIndexWithOption(final Index obj, final Table table) {
		final Map<String,String> map=CommonUtils.linkedMap();
		String key=SqlServerIndexOptions.PAD_INDEX.toString();
		String val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		key=SqlServer2000IndexReader.FILL_FACTOR;
		val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(SqlServerIndexOptions.FILLFACTOR.toString(), val);
		} else{
			val=obj.getSpecifics().get(SqlServerIndexOptions.FILLFACTOR.toString());
			if (val!=null){
				map.put(SqlServerIndexOptions.FILLFACTOR.toString(), val);
			}
		}
		key=SqlServerIndexOptions.ALLOW_ROW_LOCKS.toString();
		val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		key=SqlServerIndexOptions.ALLOW_PAGE_LOCKS.toString();
		val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		return map;
	}

}
