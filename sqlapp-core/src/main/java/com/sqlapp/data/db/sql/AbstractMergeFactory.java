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

import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * MERGE生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractMergeFactory<S extends AbstractSqlBuilder<?>> extends AbstractMergeTableFactory<S> {

	@Override
	protected SqlType getSqlType() {
		return SqlType.MERGE;
	}

	@Override
	protected void addUsing(final Table obj, final SqlSignature sqlSignature, String sourceTableAlias,
			final S builder) {
		List<Column> columns = CommonUtils.list();
		builder.using().space().brackets(true, () -> {
			int i = 0;
			boolean supportValues = this.getDialect().supportsValues();
			if (!supportValues) {
				builder.select();
			} else {
				builder.values();
				builder._add(" (");
				builder.appendIndent(1);
			}
			for (final Column column : obj.getColumns()) {
				final String def = this.getValueDefinitionForMerge(column);
				builder.lineBreak();
				builder.comma(i > 0).space(2, i == 0);
				builder._add(def);
				addUsingColumnNameAlias(column, builder);
				columns.add(column);
				i++;
			}
			if (!supportValues) {
				builder.lineBreak();
				builder.from().space()._add(this.getDialect().getSelectDummyTableName());
			} else {
				builder.appendIndent(-1);
				builder.lineBreak();
				builder._add(")");
			}
		});
		if (this.getDialect().supportsTableNameAlias()) {
			builder.lineBreak();
			builder.as();
			builder.space()._add(sourceTableAlias);
		} else {
			builder.space()._add(sourceTableAlias);
		}
		addUsingSourceColumns(obj, columns, builder);
	}

	protected void addUsingColumnNameAlias(Column column, final S builder) {

	}

	protected void addUsingSourceColumns(final Table obj, List<Column> columns, final S builder) {
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
