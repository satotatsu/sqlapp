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
import com.sqlapp.data.db.dialect.mysql.util.MySqlDefiner;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;

public class MySqlCreateTriggerFactory extends AbstractCreateTriggerFactory<MySqlSqlBuilder> {
	@Override
	protected java.util.List<Trigger> sort(java.util.List<Trigger> triggers) {
		return com.sqlapp.data.db.dialect.mysql.util.MySqlTriggerOrder.sort(triggers);
	}

	@Override
	protected void addCreateObject(final Trigger obj, MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		validateComponents(obj);
		builder.create();
		MySqlDefiner.append(builder, obj.getSpecifics());
		builder.trigger();
		if (obj.getSchemaName() == null && obj.getTableSchemaName() != null && getOptions().isDecorateSchemaName()) {
			builder.name(obj.getTableSchemaName())._add(".").name(obj, false);
		} else {
			builder.name(obj, getOptions().isDecorateSchemaName());
		}
		addCreateObjectDetail(obj, builder);
	}

	private void validateComponents(Trigger trigger) {
		com.sqlapp.data.db.dialect.mysql.util.MySqlTriggerOrder.order(trigger);
		if (trigger.getName() == null || trigger.getName().isBlank() || trigger.getTableName() == null
				|| trigger.getTableName().isBlank()) {
			throw new IllegalArgumentException("MySQL trigger requires a name and target table");
		}
		if (CommonUtils.isEmpty(trigger.getStatement())
				|| trigger.getStatement().stream().noneMatch(line -> line != null && !line.isBlank())) {
			throw new IllegalArgumentException(
					"MySQL trigger requires a body statement or complete definition: " + trigger.getName());
		}
		if (trigger.getActionOrientation() != null && !"ROW".equalsIgnoreCase(trigger.getActionOrientation())) {
			throw new IllegalArgumentException("MySQL triggers support only FOR EACH ROW");
		}
		if (!CommonUtils.isEmpty(trigger.getWhen()) || !CommonUtils.isEmpty(trigger.getActionCondition())) {
			throw new IllegalArgumentException(
					"MySQL triggers do not support a WHEN condition; express the condition inside the trigger body");
		}
		if (!trigger.isEnable()) {
			throw new IllegalArgumentException("MySQL cannot create a disabled trigger");
		}
		if ((trigger.getActionReferenceOldRow() != null && !"OLD".equalsIgnoreCase(trigger.getActionReferenceOldRow()))
				|| (trigger.getActionReferenceNewRow() != null
						&& !"NEW".equalsIgnoreCase(trigger.getActionReferenceNewRow()))) {
			throw new IllegalArgumentException(
					"MySQL trigger row references must use OLD and NEW without custom aliases");
		}
		if (trigger.getSchemaName() != null && trigger.getTableSchemaName() != null
				&& !trigger.getSchemaName().equals(trigger.getTableSchemaName())) {
			throw new IllegalArgumentException("MySQL trigger and target table must belong to the same schema");
		}
	}

	@Override
	protected void addCreateObjectDetail(Trigger obj, MySqlSqlBuilder builder) {
		addEventManipulationText(obj, builder);
		builder.lineBreak().on();
		String tableSchema = obj.getTableSchemaName() == null ? obj.getSchemaName() : obj.getTableSchemaName();
		if (tableSchema != null) {
			builder.name(tableSchema)._add(".");
		}
		builder.name(obj.getTableName());
		addCreateTriggerBody(obj, builder);
	}

	@Override
	protected void addCreateTriggerBody(Trigger obj, MySqlSqlBuilder builder) {
		builder.lineBreak().forEach().row();
		builder.lineBreak()._add(toString(obj.getStatement()));
	}

	@Override
	protected void addEventManipulationText(final Trigger obj, MySqlSqlBuilder builder) {
		if (obj.getEventManipulation().size() != 1) {
			throw new IllegalArgumentException("MySQL trigger requires exactly one INSERT, UPDATE or DELETE event");
		}
		String event = CommonUtils.first(obj.getEventManipulation());
		if (!"INSERT".equalsIgnoreCase(event) && !"UPDATE".equalsIgnoreCase(event)
				&& !"DELETE".equalsIgnoreCase(event)) {
			throw new IllegalArgumentException("Unsupported MySQL trigger event: " + event);
		}
		if (!"BEFORE".equalsIgnoreCase(obj.getActionTiming()) && !"AFTER".equalsIgnoreCase(obj.getActionTiming())) {
			throw new IllegalArgumentException("MySQL trigger requires BEFORE or AFTER timing");
		}
		builder.lineBreak()._add(obj.getActionTiming());
		builder.space()._add(event);
	}
}
