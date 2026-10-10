/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Ordered enum additions and single-label renames; never drop a type with live dependents. */
public class CockroachAlterTypeFactory extends SimpleSqlFactory<Type,PostgresSqlBuilder> {
	@Override public List<SqlOperation> createSql(Type type) {throw new UnsupportedOperationException("Enum ALTER requires original.diff(target)");}
	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var before=difference.getOriginal(Type.class);var after=difference.getTarget(Type.class);
		var changed=new HashSet<>(difference.getChangedProperties().keySet());changed.remove(SchemaProperties.DEFINITION.getLabel());
		if(!changed.isEmpty()) throw new UnsupportedOperationException("Unsupported CockroachDB enum changes: "+changed);
		var old=labels(before);var target=labels(after);var result=new ArrayList<SqlOperation>();
		if(old.size()==target.size()) {
			var renamed=new ArrayList<Integer>();for(int i=0;i<old.size();i++) if(!old.get(i).equals(target.get(i))) renamed.add(i);
			if(renamed.isEmpty()) return result;
			if(renamed.size()!=1 || old.contains(target.get(renamed.get(0)))) throw unsupported();
			int i=renamed.get(0);var builder=createSqlBuilder().alter().type().space().name(before,getOptions().isDecorateSchemaName());
			builder._add(" RENAME VALUE ").sqlChar(old.get(i))._add(" TO ").sqlChar(target.get(i));addSql(result,builder,SqlType.ALTER,after);return result;
		}
		if(!target.containsAll(old) || !target.stream().filter(old::contains).toList().equals(old)) throw unsupported();
		for(int i=0;i<target.size();i++) {
			String label=target.get(i);if(old.contains(label)) continue;
			var builder=createSqlBuilder().alter().type().space().name(before,getOptions().isDecorateSchemaName())._add(" ADD VALUE ").sqlChar(label);
			String next=null;for(int j=i+1;j<target.size();j++) if(old.contains(target.get(j))) {next=target.get(j);break;}
			if(next!=null) builder._add(" BEFORE ").sqlChar(next);
			addSql(result,builder,SqlType.ALTER,after);
		}
		return result;
	}
	private static List<String> labels(Type type) {
		String ddl=String.join("\n",type.getDefinition());
		var enumMatch=Pattern.compile("(?is)^CREATE\\s+TYPE\\s+.+?\\s+AS\\s+ENUM\\s*\\((.*)\\)\\s*;?\\s*$").matcher(ddl);
		if(!enumMatch.matches()) throw unsupported();
		String body=enumMatch.group(1);var result=new ArrayList<String>();
		var value=Pattern.compile("\\s*'((?:[^']|'')*)'\\s*(,|$)").matcher(body);int offset=0;
		while(offset<body.length()) {value.region(offset,body.length());if(!value.lookingAt()) throw unsupported();result.add(value.group(1).replace("''","'"));offset=value.end();if(value.group(2).isEmpty()) break;if(offset==body.length()) throw unsupported();}
		if(offset!=body.length() || result.isEmpty() || new HashSet<>(result).size()!=result.size()) throw unsupported();return result;
	}
	private static UnsupportedOperationException unsupported() {return new UnsupportedOperationException("CockroachDB enum changes support ordered additions or one label rename; removal/reordering requires an explicit migration");}
}
