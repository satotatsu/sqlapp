/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Package;

/**
 * Alters the header and retains grants; reapply its body before executing
 * routines.
 */
public class Firebird30AlterPackageFactory extends SimpleSqlFactory<Package, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Package object) {
		var create = getSqlFactoryRegistry().createSql(object, SqlType.CREATE);
		String ddl = create.get(0).getSqlText();
		if (!ddl.matches("(?is)^\\s*CREATE\\s+PACKAGE\\s+(?!BODY\\b).+"))
			throw new IllegalArgumentException(
					"Package ALTER requires a CREATE PACKAGE definition or modeled header statement");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, createSqlBuilder()._add(ddl.replaceFirst("(?is)^\\s*CREATE\\s+PACKAGE\\b", "ALTER PACKAGE")),
				SqlType.ALTER, object);
		result.addAll(create.subList(1, create.size()));
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Package target = FirebirdAlterSql.target(difference, Package.class,
				Set.of("statement", "remarks", "specifics"));
		Package original = (Package) difference.getOriginal();
		var before = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		before.putAll(original.getSpecifics());
		before.remove("firebird.sqlSecurity");
		var after = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		after.putAll(target.getSpecifics());
		after.remove("firebird.sqlSecurity");
		if (!before.equals(after))
			throw new UnsupportedOperationException("Package ALTER cannot change these vendor-specific properties");
		boolean structural = difference.getChangedProperties().containsKey("statement")
				|| !Objects.equals(original.getSpecifics().get("firebird.sqlSecurity"),
						target.getSpecifics().get("firebird.sqlSecurity"));
		if (structural && !target.getDefinition().isEmpty())
			throw new IllegalArgumentException("A complete package definition shadows modeled ALTER changes");
		List<SqlOperation> result = new ArrayList<>();
		if (structural)
			result.addAll(createSql(target));
		if (difference.getChangedProperties().containsKey("remarks") && (!structural || target.getRemarks() == null))
			result.addAll(getSqlFactoryRegistry().createSql(target, SqlType.SET_COMMENT));
		return result;
	}
}
