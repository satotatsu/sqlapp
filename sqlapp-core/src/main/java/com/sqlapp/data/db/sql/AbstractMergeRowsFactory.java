/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * MERGE ROWS 生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractMergeRowsFactory<S extends AbstractSqlBuilder<?>> extends AbstractMergeTableFactory<S> {
	@Override
	protected SqlType getSqlType() {
		return SqlType.MERGE_ROWS;
	}

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addMergeTable(table, builder);
		addSql(sqlList, builder, getSqlType(), table);
		return sqlList;
	}

	@Override
	protected void addUsing(final Table obj, String sourceTableAlias, final S builder) {
		List<Column> columns = CommonUtils.list();
		builder.using().space().brackets(() -> {
			builder.space()._add("/*VALUES*/").values().space();
			builder._add(createRowValue(obj, columns));
			builder._add("/*END*/");
		});
		/// *VALUES*/VALUES('Taro',20)/*END*/
		builder.as().space()._add(sourceTableAlias);
		builder.space().brackets(() -> {
			int i = 0;
			for (Column column : columns) {
				builder.comma(i > 0).space(i == 0);
				builder.name(column);
				i++;
			}
		});
	}

}
