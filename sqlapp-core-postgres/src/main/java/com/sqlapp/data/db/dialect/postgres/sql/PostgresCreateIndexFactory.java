/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.Map;

import com.sqlapp.data.db.dialect.postgres.util.PostgresIndexOptions;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * INDEX生成クラス
 * 
 * @author satoh
 * 
 */
public class PostgresCreateIndexFactory extends AbstractCreateIndexFactory<PostgresSqlBuilder> {

	@Override
	public void addCreateObject(final Index obj, final PostgresSqlBuilder builder) {
		super.addCreateObject(obj, builder);
		addObjectDetailAfter(obj, obj.getTable(), builder);
	}

	@Override
	protected void addUnique(final Index obj, final Table table, final PostgresSqlBuilder builder) {
		builder.unique(obj.isUnique()).index();
		boolean conc=table!=null&&this.getOptions().getTableOptions().getOnlineIndex().test(table, obj);
		builder.concurrently(conc);
		builder.ifNotExists(table!=null&&this.getOptions().isCreateIfNotExists()).space();
	}
	
	@Override
	public void addObjectDetail(final Index obj, final Table table, final PostgresSqlBuilder builder) {
		super.addObjectDetail(obj, table, builder);
		addWith(obj, table, builder);
		addFilter(obj, table, builder);
	}
	
	protected void addObjectDetailAfter(final Index obj, final Table table,
			final PostgresSqlBuilder builder) {
		addIncludes(obj, table, builder);
		addIncludesAfter(obj, table, builder);
	}

	protected void addIncludes(final Index obj, final Table table,
			final PostgresSqlBuilder builder) {
	}
	
	protected void addIncludesAfter(final Index obj, final Table table,
			final PostgresSqlBuilder builder) {
	}

	protected void addWith(final Index obj, final Table table,
			final PostgresSqlBuilder builder) {
		Map<String,String> map=createIndexWithOption(obj, table);
		if (map.isEmpty()) {
			return;
		}
		builder.lineBreak().with().space().brackets(()->{
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
	
	protected Map<String,String> createIndexWithOption(final Index obj, final Table table) {
		final Map<String,String> map=CommonUtils.linkedMap();
		String key=PostgresIndexOptions.FILLFACTOR.toString();
		String val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		key=PostgresIndexOptions.BUFFERING.toString();
		val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		key=PostgresIndexOptions.FASTUPDATE.toString();
		val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		key=PostgresIndexOptions.GIN_PENDING_LIST_LIMIT.toString();
		val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		key=PostgresIndexOptions.PAGE_PER_RANGE.toString();
		val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		key=PostgresIndexOptions.AUTOSUMMARISE.toString();
		val=obj.getSpecifics().get(key);
		if (val!=null){
			map.put(key, val);
		}
		return map;
	}

	protected void addFilter(final Index obj, final Table table,
			final PostgresSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getWhere())){
			builder.lineBreak().where().space()._add(obj.getWhere());
		}
	}

	@Override
	protected void addColumn(final ReferenceColumn col, final PostgresSqlBuilder builder) {
		builder.name(col);
		if (col.getOrder()!=null&&col.getOrder()!=Order.Asc) {
			builder.space()._add(col.getOrder());
		}
		if (col.getNullsOrder()!=null) {
			builder.space()._add(col.getNullsOrder());
		}
	}

}
