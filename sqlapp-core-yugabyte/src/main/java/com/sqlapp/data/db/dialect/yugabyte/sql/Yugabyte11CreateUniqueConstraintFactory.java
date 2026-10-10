/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.sql;

import com.sqlapp.data.db.dialect.postgres.sql.Postgres110CreateUniqueConstraintFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Order;

public class Yugabyte11CreateUniqueConstraintFactory extends Postgres110CreateUniqueConstraintFactory {
    @Override
    public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(UniqueConstraint constraint) {
        var standard = super.createSql(constraint);
        if (constraint.isPrimaryKey() || !constraint.getSpecifics().containsKey("YSQL_HASH_COLUMNS")) return standard;
        var result = new java.util.ArrayList<com.sqlapp.data.db.sql.SqlOperation>();
        var table = constraint.getTable();
        var sourceIndex = table.getIndexes().get(constraint.getName());
        var index = (sourceIndex == null ? constraint.getIndex() : sourceIndex).clone();
        index.setName(constraint.getName()).setUnique(true);
        index.getSpecifics().putAll(constraint.getSpecifics());
        if (constraint.getSpecifics().containsKey("YSQL_TABLESPACE")) index.setTableSpaceName(constraint.getSpecifics().get("YSQL_TABLESPACE"));
        var owner = new Table(table.getName()).setSchemaName(table.getSchemaName());
        owner.getIndexes().add(index);
        var indexBuilder = createSqlBuilder();
        var indexFactory = (com.sqlapp.data.db.dialect.postgres.sql.PostgresCreateIndexFactory) getSqlFactoryRegistry().getSqlFactory(index, com.sqlapp.data.db.sql.SqlType.CREATE);
        indexFactory.addCreateObject(index, indexBuilder);
        addSql(result,indexBuilder,com.sqlapp.data.db.sql.SqlType.CREATE,index);
        var attach = createSqlBuilder();
        attach.alter().table().name(constraint.getTable(),getOptions().isDecorateSchemaName()).add().constraint().space().name(constraint,false)
                .space()._add("UNIQUE USING INDEX").space().name(index,false);
        addSql(result,attach,com.sqlapp.data.db.sql.SqlType.CREATE,constraint);
        result.addAll(standard.subList(1,standard.size()));
        return result;
    }
	@Override
	public void addObjectDetail(UniqueConstraint constraint, Table table, PostgresSqlBuilder builder) {
		int hash = YugabytePlacement.hashColumns(constraint.getSpecifics());
		if (hash < 0) { super.addObjectDetail(constraint, table, builder); return; }
		if (hash > constraint.getColumns().size()) throw new IllegalArgumentException("YSQL hash key exceeds constraint key columns");
		builder.constraint().space().name(constraint, false);
		if (constraint.isPrimaryKey()) builder.primaryKey(); else builder.unique();
		addOption(constraint, builder);
		builder.space()._add("(");
		for (int i=0; i<constraint.getColumns().size(); i++) {
			var column = constraint.getColumns().get(i);
			builder.comma(i>0);
			if (i==0 && hash>1) builder._add("(");
			builder.name(column);
			if (i==hash-1) builder._add(hash>1 ? ") HASH" : " HASH");
			else if (i>=hash) builder.space()._add(column.getOrder()==Order.Desc ? "DESC" : "ASC");
		}
		builder._add(")");
		addDeferrability(constraint,builder);
		addAfter(constraint,builder);
		String tablespace = constraint.getSpecifics().get("YSQL_TABLESPACE");
		if (tablespace != null && !constraint.isPrimaryKey()) builder.space()._add("USING INDEX TABLESPACE").space().name(tablespace);
		// Backing-index split clauses are not accepted inside table constraints.
	}
}
