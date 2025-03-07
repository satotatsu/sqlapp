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
		builder.lineBreak();
		builder._for().name(obj.getTableName());
		builder.lineBreak();
		if (obj.isEnable()) {
			builder.active();
		} else {
			builder.inactive();
		}
		String position = obj.getSpecifics().get("POSITION");
		if (!"0".equals(position) && !CommonUtils.isEmpty(position)) {
			builder.lineBreak();
			builder.partition().space()._add(position);
		}
		addEventManipulationText(obj, builder);
		addCreateTriggerBody(obj, builder);
	}

	protected void addEventManipulationText(final Trigger obj, FirebirdSqlBuilder builder) {
		SeparatedStringBuilder sepBuilder = new SeparatedStringBuilder(" OR ");
		sepBuilder.add(obj.getEventManipulation());
		if (obj.getActionTiming() != null) {
			builder.lineBreak();
			builder._add(obj.getActionTiming());
		}
		if (!CommonUtils.isEmpty(obj.getEventManipulation())) {
			builder.lineBreak();
			builder._add(sepBuilder.toString());
		}
	}

	protected void addCreateTriggerBody(final Trigger obj, FirebirdSqlBuilder builder) {
		builder.lineBreak();
		String first = CommonUtils.toUpperCase(CommonUtils.first(obj.getStatement()));
		if (first != null && !first.startsWith("AS")) {
			builder.as();
			builder.lineBreak();
		}
		builder._add(toString(obj.getStatement()));
	}
}
