/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractMergeByPkTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

public class OracleMergeByPkTableFactory extends AbstractMergeByPkTableFactory<OracleSqlBuilder>{
	
	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		UniqueConstraint constraint=table.getConstraints().getPrimaryKeyConstraint();
		if (constraint==null){
			constraint=CommonUtils.first(table.getConstraints().getUniqueConstraints());
		}
		if (constraint==null){
			return super.createSql(table);
		}
		final String targetTable=this.getOptions().getTableOptions().getTemporaryAlias().apply(table);
		final OracleSqlBuilder builder = createSqlBuilder();
		builder.merge().space().name(table, this.getOptions().isDecorateSchemaName());
		builder.lineBreak();
		builder.using().space()._add("(");
		builder.appendIndent(1);
		builder.lineBreak();
		builder.select().space();
		final boolean[] first=new boolean[]{true};
		for(final Column column:table.getColumns()){
			if (this.isAutoIncrementColumn(column)){
				final String def=this.getValueDefinitionSimple(column);
				builder.$if(!CommonUtils.isEmpty(def), ()->{
					builder.comma(!first[0])._add(def).as().name(column);
					first[0]=false;
				});
			} else{
				if (!isFormulaColumn(column)) {
					final String def=this.getValueDefinitionForInsert(column);
					builder.$if(!CommonUtils.isEmpty(def), ()->{
						builder.comma(!first[0])._add(def).as().name(column);
						first[0]=false;
					});
				}
			}
		}
		builder.lineBreak();
		builder._fromSysDummy();
		builder.appendIndent(-1);
		builder.lineBreak();
		builder._add(")").as().space().name(targetTable);
		builder.lineBreak();
		builder.on();
		first[0]=true;
		for(final Column column:table.getColumns()){
			if (!constraint.getColumns().contains(column.getName())){
				continue;
			}
			builder.and(!first[0]).columnName(column, true).eq().names(targetTable, column.getName());
			first[0]=false;
		}
		builder.lineBreak();
		builder.when().matched().then();
		builder.appendIndent(1);
		builder.lineBreak();
		builder.update().set();
		first[0]=true;
		for(final Column column:table.getColumns()){
			if (constraint.getColumns().contains(column.getName())){
				continue;
			}
			if (isFormulaColumn(column)) {
				continue;
			}
			final String def=this.getValueDefinitionForUpdate(column);
			builder.and(!first[0]).name(column).eq();
			if (this.isOptimisticLockColumn(column)){
				builder._add(def);
			} else{
				if (this.withCoalesceAtUpdate(column)){
					builder.coalesce(()->{
						builder.names(column.getName()).comma();
						builder.names(targetTable, column.getName()).space();
					});
				} else{
					builder.names(targetTable, column.getName());
				}
			}
			first[0]=false;
		}
		builder.appendIndent(-1);
		builder.lineBreak();
		builder.when().not().matched().then();
		builder.appendIndent(1);
		builder.lineBreak();
		builder.insert().space()._add("(");
		first[0]=true;
		for(final Column column:table.getColumns()){
			final String def=this.getValueDefinitionForInsert(column);
			builder.$if(!CommonUtils.isEmpty(def), ()->{
				if (!isFormulaColumn(column)) {
					builder.comma(!first[0]).name(column);
					first[0]=false;
				}
			});
		}
		builder.space()._add(")").values();
		builder.space()._add("(");
		first[0]=true;
		for(final Column column:table.getColumns()){
			final String def=this.getValueDefinitionForInsert(column);
			builder.$if(!CommonUtils.isEmpty(def), ()->{
				if (!isFormulaColumn(column)) {
					builder.comma(!first[0]).names(targetTable, column.getName());
					first[0]=false;
				}
			});
		}
		builder.space()._add(")");
		builder.appendIndent(-1);
		addSql(sqlList, builder, SqlType.MERGE_BY_PK, table);
		return sqlList;
	}

}
