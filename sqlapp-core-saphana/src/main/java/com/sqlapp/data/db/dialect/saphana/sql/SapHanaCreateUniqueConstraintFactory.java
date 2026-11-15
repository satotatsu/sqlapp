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

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * PRIMARY KEY / UNIQUE constraint definition for SAP HANA.
 * <p>
 * SAP HANA names unnamed constraints itself ({@code _SYS_TREE_CS_#...}) and
 * rejects the reserved {@code _SYS_} prefix in DDL (SQL error 383), so such a
 * read-back name is omitted and SAP HANA generates a new one.
 */
public class SapHanaCreateUniqueConstraintFactory extends AbstractCreateUniqueConstraintFactory<SapHanaSqlBuilder> {

	static boolean isSystemGeneratedName(final String name) {
		return name != null && name.startsWith("_SYS_");
	}

	@Override
	public void addObjectDetail(final UniqueConstraint obj, final Table table, final SapHanaSqlBuilder builder) {
		if (!isSystemGeneratedName(obj.getName())) {
			super.addObjectDetail(obj, table, builder);
			return;
		}
		if (obj.isPrimaryKey()) {
			builder.primaryKey();
		} else {
			builder.unique();
		}
		addOption(obj, builder);
		builder.space()._add('(');
		int i = 0;
		for (final ReferenceColumn col : obj.getColumns()) {
			builder.comma(i > 0).name(col);
			if (col.getOrder() != null && col.getOrder() != Order.Asc) {
				builder.space()._add(col.getOrder());
			}
			i++;
		}
		builder.space()._add(')');
		addDeferrability(obj, builder);
		addAfter(obj, builder);
	}
}
