/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * CREATE OR ALTER uses the same complete trigger renderer and retains grants.
 */
public class FirebirdAlterTriggerFactory extends SimpleSqlFactory<Trigger, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Trigger trigger) {
		Trigger rendered = trigger.clone();
		rendered.getSpecifics().putIfAbsent("POSITION", "0");
		var create = getSqlFactoryRegistry().createSql(rendered, SqlType.CREATE);
		String ddl = create.get(0).getSqlText();
		if (!ddl.matches("(?is)^\\s*CREATE\\s+TRIGGER\\b.*"))
			throw new IllegalArgumentException(
					"Trigger ALTER requires a CREATE TRIGGER definition or modeled statement");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result,
				createSqlBuilder()._add(ddl.replaceFirst("(?is)^\\s*CREATE\\s+TRIGGER\\b", "CREATE OR ALTER TRIGGER")),
				SqlType.ALTER, trigger);
		result.addAll(create.subList(1, create.size()));
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Trigger target = FirebirdAlterSql.target(difference, Trigger.class,
				Set.of("statement", "enable", "actionTiming", "eventManipulation", "specifics", "remarks"));
		Trigger original = (Trigger) difference.getOriginal();
		boolean clearSecurity = original.getSpecifics().containsKey("firebird.sqlSecurity")
				&& !target.getSpecifics().containsKey("firebird.sqlSecurity");
		var before = new HashMap<>(original.getSpecifics());
		var after = new HashMap<>(target.getSpecifics());
		for (String key : Set.of("POSITION", "firebird.sqlSecurity", "firebird.external.engine",
				"firebird.external.entrypoint")) {
			before.remove(key);
			after.remove(key);
		}
		if (!before.equals(after))
			throw new UnsupportedOperationException("Unsupported Firebird trigger specifics change");
		boolean structural = difference.getChangedProperties().keySet().stream()
				.anyMatch(key -> !"remarks".equals(key));
		if (structural && !target.getDefinition().isEmpty())
			throw new IllegalArgumentException("A complete trigger definition shadows modeled ALTER changes");
		List<SqlOperation> result = new ArrayList<>();
		if (structural) {
			result.addAll(createSql(target));
			if (clearSecurity) {
				if (getDialect().compareTo(com.sqlapp.data.db.dialect.firebird.DialectHolder.defaultDialect40) < 0)
					throw new IllegalArgumentException("Trigger SQL SECURITY requires Firebird 4.0 or later");
				addSql(result,
						createSqlBuilder()._add("ALTER TRIGGER ").name(target.getName())._add(" DROP SQL SECURITY"),
						SqlType.ALTER, target);
			}
		}
		if (difference.getChangedProperties().containsKey("remarks") && (!structural || target.getRemarks() == null))
			result.addAll(getSqlFactoryRegistry().createSql(target, SqlType.SET_COMMENT));
		return result;
	}
}
