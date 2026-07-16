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

import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.ParentTableNotFoundException;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * DELETE TABLE生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDeleteByParentRowsFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractDeleteTableFactory<S> {

	@Override
	protected SqlType getSqlType() {
		return SqlType.DELETE_BY_PARENT_ROWS;
	}

	protected void addDeleteFromTable(final Table table, final SqlSignature sqlSignature, final S builder) {
		builder.delete().from();
		builder.name(table, this.getOptions().isDecorateSchemaName()).space()._add("a");
		this.addTableComment(table, builder);
		builder.lineBreak().where().false_();
		addDeleteConditionColumns(table, sqlSignature, builder);
	}

	@Override
	protected void addDeleteConditionColumns(final Table table, final SqlSignature sqlSignature, S builder) {
		List<ForeignKeyConstraint> fks = table.getConstraints().getForeignKeyConstraints(fk -> fk.getTable() != table);
		Table parent;
		if (fks.size() == 1) {
			parent = fks.get(0).getRelatedTable();
		} else {
			parent = this.getTableOptions().getParentTable().apply(table);
			if (parent == null) {
				throw new ParentTableNotFoundException(table, this.getSqlType());
			}
		}
		ColumnSelectionStrategy strategy = this.getTableOptions().getDeleteKeyColumnsMatchingStrategy().apply(table);
		builder.lineBreak();
		builder.or().brackets(true, () -> {
			builder.select().space()._add("1");
			builder.lineBreak();
			builder.from().name(parent);
			builder.lineBreak();
			builder.where().false_();
			builder.lineBreak();
			builder._add("/*ROWS_EQUALS(");
			builder._add(strategy);
			builder._add(")*/");
		});
	}
}
