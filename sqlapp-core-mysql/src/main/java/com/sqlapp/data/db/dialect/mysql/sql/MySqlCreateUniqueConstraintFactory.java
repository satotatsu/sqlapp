/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.IndexType;

/**
 * Unique Constraint生成クラス
 * 
 * @author satoh
 * 
 */
public class MySqlCreateUniqueConstraintFactory extends AbstractCreateUniqueConstraintFactory<MySqlSqlBuilder> {

	@Override
	public void addObjectDetail(UniqueConstraint obj, Table table, MySqlSqlBuilder builder) {
		MySqlIndexValidation.validate(obj.getIndex(), table == null ? obj.getTable() : table, getDialect(),
				obj.isPrimaryKey(), true);
		builder.constraint().space().name(obj, false);
		if (obj.isPrimaryKey()) {
			builder.primaryKey();
		} else {
			builder.unique();
		}
		if (obj.getIndexType() != null && obj.getIndexType() != IndexType.BTree) {
			builder.space().using().space()._add(obj.getIndexType());
		}
		addOption(obj, builder);
		builder.space()._add("(").names(obj.getColumns()).space()._add(")");
		addDeferrability(obj, builder);
		addAfter(obj, builder);
	}

	@Override
	protected void addAfter(UniqueConstraint obj, MySqlSqlBuilder builder) {
		if (obj.getRemarks() != null) {
			builder.comment().space().sqlChar(obj.getRemarks());
		}
		if (!obj.getIndex().isEnable()) {
			builder.space()._add("MariaDB".equalsIgnoreCase(getDialect().getProductName()) ? "IGNORED" : "INVISIBLE");
		}
	}
}
