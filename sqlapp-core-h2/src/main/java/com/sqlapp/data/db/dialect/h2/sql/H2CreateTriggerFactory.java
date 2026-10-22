/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.schemas.Trigger;

public class H2CreateTriggerFactory extends AbstractCreateTriggerFactory<H2SqlBuilder> {

	@Override
	protected void addCreateObject(final Trigger obj, H2SqlBuilder builder) {
		if (!obj.getDefinition().isEmpty()) {
			builder._add(obj.getDefinition());
			return;
		}
		builder.create().trigger().ifNotExists(this.getOptions().isCreateIfNotExists());
		builder.name(obj, getOptions().isDecorateSchemaName());
		addCreateObjectDetail(obj, builder);
	}

	@Override
	protected void addCreateObjectDetail(Trigger obj, H2SqlBuilder builder) {
		if (obj.getTableName() == null || obj.getTableName().isBlank())
			throw new IllegalArgumentException("H2 trigger requires a target table");
		addEventManipulationText(obj, builder);
		builder.lineBreak().on();
		String schema = obj.getTableSchemaName() == null ? obj.getSchemaName() : obj.getTableSchemaName();
		if (schema != null
				&& (getOptions().isDecorateSchemaName() || !java.util.Objects.equals(schema, obj.getSchemaName())))
			builder.name(schema)._add(".");
		builder.name(obj.getTableName());
		addCreateTriggerBody(obj, builder);
	}

	@Override
	protected void addEventManipulationText(Trigger obj, H2SqlBuilder builder) {
		String timing = obj.getActionTiming();
		if (!java.util.List.of("BEFORE", "AFTER", "INSTEAD OF").contains(timing))
			throw new IllegalArgumentException("H2 trigger requires BEFORE, AFTER or INSTEAD OF");
		if (obj.getEventManipulation().isEmpty())
			throw new IllegalArgumentException("H2 trigger requires an event");
		for (String event : obj.getEventManipulation())
			if (!java.util.List.of("INSERT", "UPDATE", "DELETE", "SELECT", "ROLLBACK").contains(event))
				throw new IllegalArgumentException("Unsupported H2 trigger event: " + event);
		builder.space()._add(timing).space()._add(String.join(", ", obj.getEventManipulation()));
	}

	@Override
	protected void addCreateTriggerBody(final Trigger obj, H2SqlBuilder builder) {
		String orientation = obj.getActionOrientation() == null ? "ROW" : obj.getActionOrientation();
		if (!java.util.List.of("ROW", "STATEMENT").contains(orientation))
			throw new IllegalArgumentException("Invalid H2 trigger orientation: " + orientation);
		builder.forEach().space()._add(orientation);
		String queue = obj.getSpecifics().get("h2.queueSize");
		if (queue != null) {
			try {
				if (Integer.parseInt(queue) < 0)
					throw new NumberFormatException();
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("H2 trigger QUEUE must be a nonnegative integer", ex);
			}
			builder.space()._add("QUEUE ")._add(queue);
		}
		if ("true".equals(obj.getSpecifics().get("h2.noWait")))
			builder.space()._add("NOWAIT");
		if (!obj.getStatement().isEmpty())
			builder.as().space().sqlChar(toString(obj.getStatement()));
		else {
			if (obj.getClassName() == null || obj.getClassName().isBlank())
				throw new IllegalArgumentException(
						"H2 trigger source is unavailable in the catalog; supply complete definition, source statement or Java class: "
								+ obj.getName());
			builder.call().space().sqlChar(obj.getClassName());
		}
	}

}
