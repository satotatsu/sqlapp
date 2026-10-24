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

import java.util.Locale;
import java.util.Set;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.dialect.mysql.util.MySqlDefiner;
import com.sqlapp.data.db.sql.AbstractCreateEventFactory;
import com.sqlapp.data.schemas.Event;
import com.sqlapp.data.schemas.EventType;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;

public class MySqlCreateEventFactory extends AbstractCreateEventFactory<MySqlSqlBuilder> {
	/**
	 * Exact interval text for compound intervals or values outside the shared
	 * Integer model.
	 */
	public static final String INTERVAL_VALUE = "mysql_interval_value";
	/** Optional ENABLED, DISABLED or SLAVESIDE_DISABLED catalog status. */
	public static final String STATUS = "mysql_event_status";
	private static final Set<String> UNITS = Set.of("YEAR", "QUARTER", "MONTH", "DAY", "HOUR", "MINUTE", "WEEK",
			"SECOND", "YEAR_MONTH", "DAY_HOUR", "DAY_MINUTE", "DAY_SECOND", "HOUR_MINUTE", "HOUR_SECOND",
			"MINUTE_SECOND");

	@Override
	protected void addCreateObject(final Event obj, MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(obj.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(obj.getStatement())
				|| obj.getStatement().stream().allMatch(line -> line == null || line.isBlank()))
			throw new IllegalArgumentException("MySQL event requires a statement or complete definition");
		String interval = obj.getSpecifics().get(INTERVAL_VALUE);
		if (interval == null && obj.getIntervalValue() != null)
			interval = obj.getIntervalValue().toString();
		boolean recurring = interval != null;
		if ((obj.getExecuteAt() != null) == recurring)
			throw new IllegalArgumentException("MySQL event requires exactly one AT timestamp or EVERY interval");
		if (obj.getEventType() != null && obj.getEventType() != (recurring ? EventType.Recurring : EventType.OneTime))
			throw new IllegalArgumentException("MySQL event type conflicts with its schedule");
		if (!recurring && (obj.getStarts() != null || obj.getEnds() != null || obj.getIntervalField() != null))
			throw new IllegalArgumentException("STARTS, ENDS and interval units require a recurring MySQL event");
		if (obj.getStarts() != null && obj.getEnds() != null && obj.getEnds().before(obj.getStarts()))
			throw new IllegalArgumentException("MySQL event ENDS must not precede STARTS");
		String unit = obj.getIntervalField() == null ? null : obj.getIntervalField().trim().toUpperCase(Locale.ROOT);
		if (recurring && (unit == null || !UNITS.contains(unit) || !interval.matches("[0-9]+(?:[- :][0-9]+)*")
				|| !interval.matches(".*[1-9].*")))
			throw new IllegalArgumentException(
					"MySQL event requires a positive interval and a supported interval unit");
		String completion = obj.getOnCompletion() == null ? null
				: obj.getOnCompletion().trim().toUpperCase(Locale.ROOT);
		if (completion != null && !Set.of("PRESERVE", "NOT PRESERVE").contains(completion))
			throw new IllegalArgumentException("MySQL event ON COMPLETION requires PRESERVE or NOT PRESERVE");
		String status = obj.getSpecifics().get(STATUS);
		if (status != null && (!Set.of("ENABLED", "DISABLED", "SLAVESIDE_DISABLED").contains(status)
				|| obj.isEnable() != "ENABLED".equals(status)))
			throw new IllegalArgumentException("MySQL event status must agree with its enable flag");
		builder.create();
		MySqlDefiner.append(builder, obj.getSpecifics(), obj.getDefiner());
		builder.event();
		builder.name(obj, getOptions().isDecorateSchemaName());
		builder.lineBreak();
		builder.on().schedule();
		if (obj.getExecuteAt() != null) {
			builder.at().space().sqlChar(DateUtils.format(obj.getExecuteAt(), "yyyy-MM-dd HH:mm:ss"));
		} else {
			builder.every().space();
			if (unit.contains("_"))
				builder.sqlChar(interval);
			else {
				if (!interval.matches("[0-9]+"))
					throw new IllegalArgumentException("Simple MySQL event intervals require an integer");
				builder._add(interval);
			}
			builder.space()._add(unit);
			if (obj.getStarts() != null) {
				builder.starts().space().sqlChar(DateUtils.format(obj.getStarts(), "yyyy-MM-dd HH:mm:ss"));
			}
			if (obj.getEnds() != null) {
				builder.ends().space().sqlChar(DateUtils.format(obj.getEnds(), "yyyy-MM-dd HH:mm:ss"));
			}
		}
		if (completion != null) {
			builder.lineBreak();
			builder.on().completion().space()._add(completion);
		}
		if (obj.isEnable()) {
			builder.lineBreak();
			builder.enable();
		} else {
			builder.lineBreak();
			builder.disable();
			if ("SLAVESIDE_DISABLED".equals(status))
				builder._add(" ON SLAVE");
		}
		if (!CommonUtils.isEmpty(obj.getRemarks())) {
			builder.lineBreak();
			builder.comment().space();
			builder.sqlChar(obj.getRemarks());
		}
		builder.lineBreak().do_();
		builder.lineBreak();
		builder._add(obj.getStatement());
	}
}
