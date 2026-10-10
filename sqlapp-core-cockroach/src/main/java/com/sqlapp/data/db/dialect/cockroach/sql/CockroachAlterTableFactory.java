/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresAlterTableFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Vendor ALTER syntax over the shared table/column/constraint difference model. */
public class CockroachAlterTableFactory extends PostgresAlterTableFactory {
	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var before=difference.getOriginal(Table.class);var after=difference.getTarget(Table.class);
		if(!Objects.equals(before.getSchemaName(),after.getSchemaName()) || !Objects.equals(before.getCatalogName(),after.getCatalogName()))
			throw new UnsupportedOperationException("CockroachDB table moves require an explicit migration");
		var changed=new HashSet<>(difference.getChangedProperties().keySet());
		changed.removeAll(Set.of("name","columns","constraints","indexes","remarks","definition"));
		if(!changed.isEmpty()) throw new UnsupportedOperationException("Unsupported CockroachDB table changes: "+changed);
		if(difference.getChangedProperties().size()==1 && difference.getChangedProperties().containsKey("definition"))
			throw new UnsupportedOperationException("CockroachDB table definition-only changes require an explicit migration; edit modeled columns/constraints/indexes for supported ALTER");
		var result=new ArrayList<SqlOperation>();
		if(!Objects.equals(before.getName(),after.getName())) {
			PostgresSqlBuilder builder=createSqlBuilder();
			builder.alter().table().name(before,getOptions().isDecorateSchemaName()).rename().to().name(after,false);
			addSql(result,builder,SqlType.ALTER,after);before=before.clone().setName(after.getName());difference=before.diff(after);
		}
		result.addAll(super.createDiffSql(difference));
		if(result.isEmpty() && !difference.getChangedProperties().isEmpty()) throw new UnsupportedOperationException("CockroachDB difference has no supported ALTER action; provide an explicit migration");
		return result;
	}
	@Override public List<SqlOperation> createSql(Table table) {throw new UnsupportedOperationException("Table ALTER requires original.diff(target)");}
	@Override protected void addAddColumn(Table original,Table table,DbObjectDifference diff,List<SqlOperation> result) {
		var column=diff.getTarget(Column.class);
		if(column.getFormula()!=null || column.isIdentity()) throw new UnsupportedOperationException("Adding generated/identity columns requires an explicit CockroachDB migration");
		var builder=createSqlBuilder().alter().table().name(table,getOptions().isDecorateSchemaName()).add().column().name(column).space().definition(column,false);
		addSql(result,builder,SqlType.ALTER,column);
	}
	@Override protected void addAlterColumn(Table original,Table table,Column oldColumn,Column column,DbObjectDifference diff,List<SqlOperation> result) {
		var changed=new HashSet<>(diff.getChangedProperties().keySet());
		var typeFields=Set.of("dataType","dataTypeName","length","scale","arrayDimension");
		var unsupported=new HashSet<>(changed);unsupported.removeAll(typeFields);unsupported.removeAll(Set.of("name","defaultValue","notNull","remarks","ordinal"));
		if(!unsupported.isEmpty()) throw new UnsupportedOperationException("Unsupported CockroachDB column changes: "+unsupported);
		if(changed.stream().anyMatch(typeFields::contains)) {
			var type=column.clone().setDefaultValue(null).setNotNull(false).setRemarks(null).setCheck(null);
			var builder=alterColumn(table,oldColumn)._add(" TYPE ").definitionForAlterColumn(type);
			addSql(result,builder,SqlType.ALTER,column);
		}
		if(changed.contains("defaultValue")) {
			var builder=alterColumn(table,oldColumn);
			if(column.getDefaultValue()==null) builder._add(" DROP DEFAULT");else builder._add(" SET DEFAULT ")._add(column.getDefaultValue());
			addSql(result,builder,SqlType.ALTER,column);
		}
		if(changed.contains("notNull")) addSql(result,alterColumn(table,oldColumn)._add(column.isNotNull()?" SET NOT NULL":" DROP NOT NULL"),SqlType.ALTER,column);
	}
	private PostgresSqlBuilder alterColumn(Table table,Column column) {return createSqlBuilder().alter().table().name(table,getOptions().isDecorateSchemaName()).alter().column().name(column);}
	@Override protected void addIndexDefinition(Table original,Table table,Index oldIndex,Index index,DbObjectDifference diff,List<SqlOperation> result) {
		if(diff.getState()==State.Modified) {
			if(!backing(original,oldIndex) && !backing(table,index)) result.addAll(getSqlFactoryRegistry().createSql(diff));
		} else super.addIndexDefinition(original,table,oldIndex,index,diff,result);
	}

	@Override protected void addCreateIndexDefinition(Table original,Table table,Index oldIndex,Index index,DbObjectDifference diff,List<SqlOperation> result) {
		if(!backing(table,index)) result.addAll(getSqlFactoryRegistry().createSql(index,SqlType.CREATE));
	}
	@Override protected void addDropIndexDefinition(Table original,Table table,Index oldIndex,Index index,DbObjectDifference diff,List<SqlOperation> result) {
		if(!backing(original,oldIndex)) result.addAll(getSqlFactoryRegistry().createSql(oldIndex,SqlType.DROP));
	}
	private boolean backing(Table table,Index index) {return table.getConstraints().getUniqueConstraints().stream().anyMatch(c->Objects.equals(c.getName(),index.getName()) || c.getIndex()!=null && Objects.equals(c.getIndex().getName(),index.getName()));}
	@Override protected void addDropConstraintDefinition(Table original,Table table,Constraint oldConstraint,Constraint constraint,DbObjectDifference diff,List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(oldConstraint,SqlType.DROP));
	}

	@Override protected void addConstraintDefinition(Table original,Table table,Constraint oldConstraint,Constraint constraint,DbObjectDifference diff,List<SqlOperation> result) {
		if(!diff.getChangedProperties().isEmpty() && diff.getChangedProperties().keySet().stream().allMatch("remarks"::equals)) {super.addConstraintDefinition(original,table,oldConstraint,constraint,diff,result);return;}
		if(oldConstraint instanceof UniqueConstraint u && u.isPrimaryKey() || constraint instanceof UniqueConstraint targetUnique && targetUnique.isPrimaryKey())
			throw new UnsupportedOperationException("CockroachDB primary-key changes require an explicit migration");
		super.addConstraintDefinition(original,table,oldConstraint,constraint,diff,result);
	}
	@Override protected void addCommentDefinitions(Map<String,Difference<?>> diff,Table original,Table table,List<SqlOperation> result) {
		// Index comments require table@index; handle those through the dedicated index factory.
		var withoutIndexes=new HashMap<>(diff);withoutIndexes.remove("indexes");super.addCommentDefinitions(withoutIndexes,original,table,result);
	}
}
