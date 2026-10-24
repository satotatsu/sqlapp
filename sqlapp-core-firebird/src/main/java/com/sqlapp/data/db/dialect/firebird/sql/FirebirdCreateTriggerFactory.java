/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;

public class FirebirdCreateTriggerFactory extends AbstractCreateTriggerFactory<FirebirdSqlBuilder> {

	@Override
	protected void addCreateObject(final Trigger obj, FirebirdSqlBuilder builder) {
		if (!isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
		} else {
			builder.create().trigger();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			addCreateObjectDetail(obj, builder);
		}
	}

	protected void addCreateObjectDetail(final Trigger obj, FirebirdSqlBuilder builder) {
		if (!isEmpty(obj.getTableName())) {
			builder.lineBreak();
			builder.for_().name(obj.getTableName());
		}
		builder.lineBreak();
		if (obj.isEnable()) {
			builder.active();
		} else {
			builder.inactive();
		}
		addEventManipulationText(obj, builder);
		String position = obj.getSpecifics().get("POSITION");
		if (!"0".equals(position) && !CommonUtils.isEmpty(position)) {
			builder.lineBreak();
			int value;
			try {
				value = Integer.parseInt(position);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Firebird trigger POSITION must be an integer: " + position, e);
			}
			if (value < 0 || value > 32767) {
				throw new IllegalArgumentException("Firebird trigger POSITION must be between 0 and 32767");
			}
			builder.position().space()._add(value);
		}
		FirebirdRoutineSql.addSecurity(obj.getSpecifics(), builder);
		if (!FirebirdRoutineSql.addExternal(obj.getSpecifics(), builder))
			addCreateTriggerBody(obj, builder);
	}

	protected void addEventManipulationText(final Trigger obj, FirebirdSqlBuilder builder) {
		SeparatedStringBuilder sepBuilder = new SeparatedStringBuilder(" OR ");
		sepBuilder.add(obj.getEventManipulation());
		if (!isEmpty(obj.getActionTiming())) {
			builder.lineBreak();
			builder._add(obj.getActionTiming());
		}
		if (!CommonUtils.isEmpty(obj.getEventManipulation())) {
			builder.lineBreak();
			builder._add(sepBuilder.toString());
		}
	}

	protected void addCreateTriggerBody(final Trigger obj, FirebirdSqlBuilder builder) {
		if (isEmpty(obj.getStatement()) || toString(obj.getStatement()).isBlank()) {
			throw new IllegalArgumentException("Firebird trigger requires a statement or complete definition");
		}
		builder.lineBreak();
		String first = CommonUtils.toUpperCase(CommonUtils.first(obj.getStatement()));
		if (first != null && !first.stripLeading().matches("(?s)^AS\\b.*")) {
			builder.as();
			builder.lineBreak();
		}
		builder._add(toString(obj.getStatement()));
	}
}
