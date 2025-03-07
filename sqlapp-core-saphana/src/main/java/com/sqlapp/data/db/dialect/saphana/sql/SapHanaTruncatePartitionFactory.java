/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractTruncatePartitionFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;

public class SapHanaTruncatePartitionFactory extends AbstractTruncatePartitionFactory<SapHanaSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(final AbstractPartition<?> obj) {
		final List<SqlOperation> sqlList = list();
		if (obj instanceof SubPartition) {
			final SapHanaSqlBuilder builder = createSqlBuilder();
			addTruncateTable(obj, builder);
			addSql(sqlList, builder, SqlType.TRUNCATE, obj);
			return sqlList;
		}
		Partition partition=(Partition)obj;
		if (partition.getSubPartitions().isEmpty()) {
			final SapHanaSqlBuilder builder = createSqlBuilder();
			addTruncateTable(obj, builder);
			addSql(sqlList, builder, SqlType.TRUNCATE, obj);
			return sqlList;
		}
		for(SubPartition sub:partition.getSubPartitions()) {
			final SapHanaSqlBuilder builder = createSqlBuilder();
			addTruncateTable(sub, builder);
			addSql(sqlList, builder, SqlType.TRUNCATE, obj);
		}
		return sqlList;
	}

	protected void addTruncateTable(final AbstractPartition<?> obj, final SapHanaSqlBuilder builder) {
		final Table table=obj.getPartitioning().getTable();
		builder.truncate().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		this.addTableComment(table, builder);
		addTruncatePartition(table, obj, builder);
	}

	@Override
	protected void addTruncatePartition(Table table, AbstractPartition<?> obj, SapHanaSqlBuilder builder) {
		// TODO Auto-generated method stub
		builder.partition().space().brackets(()->{
			builder.space();
			if (obj.getId()!=null) {
				builder._add(obj.getId());
			} else {
				builder._add(obj.getName());
			}
		});
	}
}