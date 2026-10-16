/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbInfo;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.util.CommonUtils;

/** Non-destructive changes supported by Oracle ALTER DOMAIN. */
public class Oracle23aiAlterDomainFactory extends SimpleSqlFactory<Domain, OracleSqlBuilder> {
	@Override public List<SqlOperation> createSql(Domain domain) {
		throw new UnsupportedOperationException("ALTER DOMAIN requires an original/target difference: " + domain.getName());
	}
	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Domain original = difference.getOriginal(Domain.class);
		Domain target = difference.getTarget(Domain.class);
		if (original == null || target == null) throw new IllegalArgumentException("ALTER DOMAIN requires both versions");
		if (original.equals(target)) return List.of();
		if (OracleDomainAssociationUtils.isFlexible(original) || OracleDomainAssociationUtils.isFlexible(target)
				|| !CommonUtils.isEmpty(original.getDefinition()) || !CommonUtils.isEmpty(target.getDefinition())
				|| !structuralCopy(original).equals(structuralCopy(target))) {
			throw new IllegalArgumentException("Oracle ALTER DOMAIN supports only structured DISPLAY, ORDER and object annotations; plan other changes explicitly: "
					+ original.getSchemaName() + "." + original.getName());
		}
		List<SqlOperation> result = new ArrayList<>();
		for (String key : List.of(Oracle23aiCreateDomainFactory.DISPLAY, Oracle23aiCreateDomainFactory.ORDER)) {
			String before = original.getSpecifics().get(key), after = target.getSpecifics().get(key);
			if (Objects.equals(before, after)) continue;
			if (after != null && (after.isBlank() || after.indexOf('\n') >= 0 || after.indexOf('\r') >= 0)) {
				throw new IllegalArgumentException("Use a single-line SQL expression for " + key + ": " + target.getName());
			}
			var builder = alter(target);
			builder.space()._add(after == null ? "DROP" : before == null ? "ADD" : "MODIFY").space()._add(key);
			if (after != null) builder.space()._add(after);
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		var before = OracleAnnotationUtils.getAnnotations(original);
		var after = OracleAnnotationUtils.getAnnotations(target);
		var names = new LinkedHashSet<>(before.keySet()); names.addAll(after.keySet());
		for (String name : names) {
			if (before.containsKey(name) == after.containsKey(name) && Objects.equals(before.get(name), after.get(name))) continue;
			var builder = alter(target);
			builder.space()._add("ANNOTATIONS (")._add(!after.containsKey(name) ? "DROP" : before.containsKey(name) ? "REPLACE" : "ADD")
					.space().name(name);
			String value = after.get(name);
			if (after.containsKey(name) && value != null && !value.isEmpty()) builder.space().sqlChar(value);
			builder._add(")");
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		return result;
	}
	private Domain structuralCopy(Domain domain) {
		Domain copy = domain.clone();
		copy.setSpecifics(new DbInfo());
		domain.getSpecifics().forEach((key, value) -> {
			if (!key.equalsIgnoreCase(Oracle23aiCreateDomainFactory.DISPLAY)
					&& !key.equalsIgnoreCase(Oracle23aiCreateDomainFactory.ORDER) && !OracleAnnotationUtils.isAnnotationKey(key)) {
				copy.getSpecifics().put(key, value);
			}
		});
		return copy;
	}
	private OracleSqlBuilder alter(Domain domain) {
		var builder = createSqlBuilder();
		builder.alter().space()._add("DOMAIN").space().name(domain, getOptions().isDecorateSchemaName());
		return builder;
	}
}
