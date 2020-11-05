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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.List;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateMviewFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.Table;

public class OracleCreateMviewFactory extends
		AbstractCreateMviewFactory<OracleSqlBuilder> {

	@Override
	protected void addCreateObject(final Mview obj, OracleSqlBuilder builder) {
		if (!isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
		} else {
			builder.create().or().replace().materialized().view();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			builder.lineBreak().as();
			builder.lineBreak()._add(obj.getStatement());
		}
	}

	@Override
	protected void addOtherDefinitions(Mview table, List<SqlOperation> result){
		if (table.getRemarks()!=null){
			OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().materialized().view().space().name(table, this.getOptions().isDecorateSchemaName()).is().sqlChar(table.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		table.getColumns().stream().filter(c->c.getRemarks()!=null).forEach(c->{
			OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().column().space().columnName(c, true, this.getOptions().isDecorateSchemaName()).is().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
		table.getIndexes().stream().filter(c->c.getRemarks()!=null).forEach(c->{
			OracleSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().index().space().name(c, this.getOptions().isDecorateSchemaName()).is().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
	}

	/**
	 * 全インデックスを追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addIndexes(final Table table, OracleSqlBuilder builder) {
		for (Index index : table.getIndexes()) {
			if (!table.getConstraints().contains(index.getName())) {
				addIndex(index, builder);
			}
		}
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param index
	 * @param builder
	 */
	protected void addIndex(final Index index, OracleSqlBuilder builder) {
		if (index == null) {
			return;
		}
		SqlFactory<Index> sqlFactory = getSqlFactoryRegistry()
				.getSqlFactory(index, SqlType.CREATE);
		if (sqlFactory instanceof OracleCreateIndexFactory) {
			builder.lineBreak().comma();
			OracleCreateIndexFactory indexOperation 
				= (OracleCreateIndexFactory) sqlFactory;
			indexOperation.addObjectDetail(index, null, builder);
		}
	}
}
