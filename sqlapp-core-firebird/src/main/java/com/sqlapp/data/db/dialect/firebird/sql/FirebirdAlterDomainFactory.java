/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Alters modeled domain attributes without replacing dependent columns. */
public class FirebirdAlterDomainFactory extends SimpleSqlFactory<Domain, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Domain domain) {
		throw new IllegalArgumentException(
				"Domain ALTER requires original.diff(target) to distinguish SET and DROP operations");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Domain target = FirebirdAlterSql.target(difference, Domain.class,
				Set.of("dataType", "dataTypeName", "length", "scale", "defaultValue", "check", "notNull", "remarks"));
		Domain original = (Domain) difference.getOriginal();
		if (!Objects.equals(original.getCharacterSet(), target.getCharacterSet())
				|| !Objects.equals(original.getCollation(), target.getCollation()))
			throw new UnsupportedOperationException("Firebird domain ALTER cannot change character set or collation");
		var changed = difference.getChangedProperties();
		if (!target.getDefinition().isEmpty() && changed.keySet().stream().anyMatch(key -> !"remarks".equals(key)))
			throw new IllegalArgumentException("A complete domain definition shadows modeled ALTER changes");
		if (changed.containsKey("notNull") && getDialect().compareTo(DialectHolder.defaultDialect30) < 0)
			throw new IllegalArgumentException("Domain nullability ALTER requires Firebird 3.0 or later");
		List<SqlOperation> result = new ArrayList<>();
		if (changed.keySet().stream().anyMatch(Set.of("dataType", "dataTypeName", "length", "scale")::contains)) {
			if (target.getArrayDimension() > 0)
				throw new IllegalArgumentException("Firebird cannot ALTER an array domain type");
			if (original.getCollation() != null
					&& !"0".equals(original.getSpecifics().get("firebird.domain.collationId")))
				throw new IllegalArgumentException(
						"Firebird TYPE resets domain collation; a non-default or unverified collation requires domain recreation with its dependencies");
			var builder = prefix(target)._add(" TYPE ").typeDefinition(target);
			addSql(result, builder, SqlType.ALTER, target);
		}
		if (changed.containsKey("defaultValue"))
			addSql(result,
					target.getDefaultValue() == null ? prefix(target)._add(" DROP DEFAULT")
							: prefix(target)._add(" SET DEFAULT ")._add(target.getDefaultValue()),
					SqlType.ALTER, target);
		if (changed.containsKey("check")) {
			if (original.getCheck() != null)
				addSql(result, prefix(target)._add(" DROP CONSTRAINT"), SqlType.ALTER, target);
			if (target.getCheck() != null)
				addSql(result, prefix(target)._add(" ADD CHECK (")._add(target.getCheck())._add(")"), SqlType.ALTER,
						target);
		}
		if (changed.containsKey("notNull"))
			addSql(result, prefix(target)._add(target.isNotNull() ? " SET NOT NULL" : " DROP NOT NULL"), SqlType.ALTER,
					target);
		if (changed.containsKey("remarks"))
			result.addAll(getSqlFactoryRegistry().createSql(target, SqlType.SET_COMMENT));
		return result;
	}

	private FirebirdSqlBuilder prefix(Domain target) {
		return createSqlBuilder()._add("ALTER DOMAIN ").name(target.getName());
	}
}
