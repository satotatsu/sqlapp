/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;

/** HSQLDB SQL trigger transition rows and search condition. */
public class HsqlCreateTriggerFactory extends AbstractCreateTriggerFactory<HsqlSqlBuilder> {
	@Override
	protected void addCreateObjectDetail(Trigger obj, HsqlSqlBuilder builder) {
		addEventManipulationText(obj, builder);
		if (obj.getTableName() != null) {
			builder.lineBreak().on();
			String schema = obj.getTableSchemaName() == null ? obj.getSchemaName() : obj.getTableSchemaName();
			if (schema != null)
				builder.name(schema)._add(".");
			builder.name(obj.getTableName());
		}
		addCreateTriggerBody(obj, builder);
	}

	@Override
	protected void addEventManipulationText(Trigger obj, HsqlSqlBuilder builder) {
		String json = obj.getSpecifics().get("hsql.updateColumns.v1");
		if (json == null) {
			super.addEventManipulationText(obj, builder);
			return;
		}
		java.util.List<String> columns = new com.sqlapp.util.JsonConverter().getObjectMapper()
				.readerForListOf(String.class).readValue(json);
		if (columns == null || columns.isEmpty() || columns.stream().anyMatch(n -> n == null || n.isBlank())
				|| obj.getEventManipulation().size() != 1 || !obj.getEventManipulation().contains("UPDATE")) {
			throw new IllegalArgumentException(
					"HSQLDB UPDATE OF requires update column names and one UPDATE event: " + obj.getName());
		}
		if (obj.getActionTiming() != null)
			builder.lineBreak()._add(obj.getActionTiming());
		builder.lineBreak()._add("UPDATE OF");
		boolean first = true;
		for (String column : columns) {
			if (!first)
				builder.comma();
			builder.space().name(column);
			first = false;
		}
	}

	@Override
	protected void addCreateTriggerBody(Trigger obj, HsqlSqlBuilder builder) {
		String oldTable = obj.getSpecifics().get("ACTION_REFERENCE_OLD_TABLE");
		String newTable = obj.getSpecifics().get("ACTION_REFERENCE_NEW_TABLE");
		if (!CommonUtils.isEmpty(obj.getActionReferenceOldRow()) || !CommonUtils.isEmpty(obj.getActionReferenceNewRow())
				|| !CommonUtils.isEmpty(oldTable) || !CommonUtils.isEmpty(newTable)) {
			builder.lineBreak()._add("REFERENCING");
			if (!CommonUtils.isEmpty(oldTable)) {
				builder.space()._add("OLD TABLE AS").space().name(oldTable);
			}
			if (!CommonUtils.isEmpty(newTable)) {
				builder.space()._add("NEW TABLE AS").space().name(newTable);
			}
			if (!CommonUtils.isEmpty(obj.getActionReferenceOldRow())) {
				builder.space()._add("OLD ROW AS").space().name(obj.getActionReferenceOldRow());
			}
			if (!CommonUtils.isEmpty(obj.getActionReferenceNewRow())) {
				builder.space()._add("NEW ROW AS").space().name(obj.getActionReferenceNewRow());
			}
		}
		builder.lineBreak().forEach().space()
				._add(obj.getActionOrientation() == null ? "ROW" : obj.getActionOrientation());
		String condition = CommonUtils.isEmpty(obj.getActionCondition()) ? obj.getWhen() : obj.getActionCondition();
		if (!CommonUtils.isEmpty(condition)) {
			builder.lineBreak()._add("WHEN (")._add(condition)._add(")");
		}
		builder.lineBreak()._add(obj.getStatement());
	}
}
