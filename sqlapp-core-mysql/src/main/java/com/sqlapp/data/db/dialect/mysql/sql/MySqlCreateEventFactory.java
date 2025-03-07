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
import com.sqlapp.data.db.sql.AbstractCreateEventFactory;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;

public class MySqlCreateEventFactory extends
		AbstractCreateEventFactory<MySqlSqlBuilder> {

	@Override
	protected void addCreateObject(final Event obj, MySqlSqlBuilder builder) {
		builder.create();
		// TODO DEFINER処理
		builder.event().ifNotExists();
		builder.name(obj);
		builder.lineBreak();
		builder.on().schedule();
		if (obj.getExecuteAt() != null) {
			builder.at().space()._add(DateUtils.format(obj.getExecuteAt()));
		} else if (obj.getIntervalValue() != null) {
			builder.every().space()._add(obj.getIntervalValue());
			if (obj.getStarts() != null) {
				builder.starts().space()
						._add(DateUtils.format(obj.getStarts()));
			}
			if (obj.getEnds() != null) {
				builder.ends().space()._add(DateUtils.format(obj.getEnds()));
			}
		}
		if (obj.getOnCompletion() != null) {
			builder.lineBreak();
			builder.on().completion().space()._add(obj.getOnCompletion());
		}
		if (obj.isEnable()) {
			builder.lineBreak();
			builder.enable();
		} else {
			builder.lineBreak();
			builder.disable();
		}
		if (!CommonUtils.isEmpty(obj.getRemarks())) {
			builder.lineBreak();
			builder.comment().space();
			builder.sqlChar(obj.getRemarks());
		}
		builder._do();
		builder.lineBreak();
		builder._add(obj.getStatement());
	}
}
