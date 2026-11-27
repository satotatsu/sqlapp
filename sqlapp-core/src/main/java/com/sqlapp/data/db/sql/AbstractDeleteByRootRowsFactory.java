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

import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * DELETE BY PARENT ROWS生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDeleteByRootRowsFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableRelationFactory<S> {

	@Override
	protected SqlType getSqlType() {
		return SqlType.DELETE_BY_ROOT_ROWS;
	}

	@Override
	public List<SqlOperation> createSql(final TableRelation obj) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addDeleteFromTable(obj, builder);
		addSql(sqlList, builder, getSqlType(), obj);
		return sqlList;
	}

	protected void addDeleteFromTable(final TableRelation obj, final S builder) {
		builder.delete().from();
		builder.name(obj.getTable(), this.getOptions().isDecorateSchemaName());
		this.addTableComment(obj.getTable(), builder);
		builder.lineBreak().where().true_();
		addDeleteConditionColumns(obj, builder);
	}

	protected void addDeleteConditionColumns(final TableRelation obj, S builder) {
		List<TableRelation> listParents = obj.getParentTableRelations();
		if (listParents.isEmpty()) {
			builder.lineBreak()._add("/*ROWS=(target=ROOT)*/");
			return;
		}
		builder.lineBreak();
		final boolean useAliases = listParents.size() > 1;
		final String rootAlias = useAliases ? "p" + (listParents.size() - 1) : null;
		builder.and().exists().space().brackets(true, () -> {
			builder.select().space()._add("1");
			builder.lineBreak();
			String previousAlias = useAliases ? "p0" : null;
			TableRelation previous = listParents.get(0);
			builder.from();
			if (useAliases) {
				builder.nameAs(previous.getTable(), previousAlias);
			} else {
				builder.name(previous.getTable());
			}
			for (int i = 1; i < listParents.size(); i++) {
				TableRelation parent = listParents.get(i);
				String parentAlias = "p" + i;
				previous.addJoin(previousAlias, parentAlias, builder);
				previousAlias = parentAlias;
				previous = parent;
			}
			builder.lineBreak();
			builder.where().true_();
			builder.indent(() -> {
				obj.forEach((i, col, rcol) -> {
					builder.lineBreak();
					builder.and().name(col, true).eq();
					if (useAliases) {
						builder.name("p0.", rcol);
					} else {
						builder.name(rcol, true);
					}
				});
				builder.lineBreak();
				builder._add("/*ROWS=(");
				builder._add("target=ROOT");
				if (useAliases) {
					builder._add(";prefix=" + rootAlias + ".");
				}
				builder._add(")*/");
			});
		});
	}
}
