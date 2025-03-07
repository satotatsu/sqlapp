/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateMaskFactory;
import com.sqlapp.data.schemas.Mask;
import com.sqlapp.util.CommonUtils;

public class Db2_1010CreateMaskFactory extends
		AbstractCreateMaskFactory<Db2SqlBuilder> {

	@Override
	protected void addCreateObject(final Mask obj, Db2SqlBuilder builder) {
		builder.create().or().replace().mask();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.on().name(obj.getTable(), !CommonUtils.eq(obj.getTableSchemaName(), obj.getSchemaName()));
		builder._for().column().name(obj.getColumn())._return();
		builder.appendIndent(1);
		builder.lineBreak()._add(obj.getStatement());
		builder.appendIndent(-1);
		builder.lineBreak().end().enable(obj.isEnable()).disable(!obj.isEnable());
	}
}
