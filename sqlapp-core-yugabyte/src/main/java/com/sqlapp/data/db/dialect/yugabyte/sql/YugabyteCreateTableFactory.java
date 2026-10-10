/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.sql;

import com.sqlapp.data.db.dialect.postgres.sql.PostgresCreateTableFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Table;

public class YugabyteCreateTableFactory extends PostgresCreateTableFactory {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(Table table) {
		var result = new java.util.ArrayList<com.sqlapp.data.db.sql.SqlOperation>();
		if ("true".equals(table.getSpecifics().get("YSQL_COLOCATION"))) {
			var guard = createSqlBuilder();
			guard._add("DO $sqlapp$ BEGIN IF NOT yb_is_database_colocated() THEN RAISE EXCEPTION 'YSQL COLOCATION requires a colocated target database'; END IF; END $sqlapp$");
			addSql(result, guard, com.sqlapp.data.db.sql.SqlType.CREATE, table);
		}
		result.addAll(super.createSql(table));
		return result;
	}
    @Override
    protected void addConstraintDefinition(com.sqlapp.data.schemas.Constraint constraint, PostgresSqlBuilder builder) {
        if (constraint instanceof com.sqlapp.data.schemas.UniqueConstraint unique && !unique.isPrimaryKey()
                && unique.getSpecifics().containsKey("YSQL_HASH_COLUMNS")) return;
        if (constraint instanceof com.sqlapp.data.schemas.ForeignKeyConstraint foreignKey
                && hasPlacedUnique(foreignKey.getTable())) return;
        super.addConstraintDefinition(constraint,builder);
    }
    @Override
    protected void addCreateIndexDefinition(Table table, com.sqlapp.data.schemas.Index index,
            java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
        if (table.getConstraints().getUniqueConstraints().stream().anyMatch(c -> c.getIndex()!=null && (java.util.Objects.equals(c.getName(),index.getName()) || java.util.Objects.equals(c.getIndex().getName(),index.getName())))) return;
        super.addCreateIndexDefinition(table,index,result);
    }
    @Override
    protected void addOtherDefinitions(Table table, java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
        for (var constraint:table.getConstraints().getUniqueConstraints()) {
            if (!constraint.isPrimaryKey() && constraint.getSpecifics().containsKey("YSQL_HASH_COLUMNS")) {
                getSqlFactoryRegistry().createSql(constraint,com.sqlapp.data.db.sql.SqlType.CREATE).stream()
                    .filter(op -> op.getSqlType()!=com.sqlapp.data.db.sql.SqlType.SET_COMMENT).forEach(result::add);
            }
        }
        if (hasPlacedUnique(table)) {
            for (var constraint : table.getConstraints()) {
                if (constraint instanceof com.sqlapp.data.schemas.ForeignKeyConstraint
                        && !Boolean.parseBoolean(constraint.getSpecifics().get("notValid"))) {
                    getSqlFactoryRegistry().createSql(constraint,com.sqlapp.data.db.sql.SqlType.CREATE).stream()
                        .filter(op -> op.getSqlType()!=com.sqlapp.data.db.sql.SqlType.SET_COMMENT).forEach(result::add);
                }
            }
        }
        super.addOtherDefinitions(table,result);
    }
    private boolean hasPlacedUnique(Table table) {
        return table.getConstraints().getUniqueConstraints().stream().anyMatch(c -> !c.isPrimaryKey()
                && c.getSpecifics().containsKey("YSQL_HASH_COLUMNS"));
    }

	@Override
	protected void addOption(Table table, PostgresSqlBuilder builder) {
		super.addOption(table, builder);
		String colocated = table.getSpecifics().get("YSQL_COLOCATION");
		if (colocated != null) {
			if (!"true".equals(colocated) && !"false".equals(colocated)) throw new IllegalArgumentException("YSQL_COLOCATION must be true or false");
			builder.space()._add("WITH (COLOCATION = " + colocated + ")");
		}
		YugabytePlacement.tablespace(table.getTableSpaceName(), builder);
		YugabytePlacement.split(table.getSpecifics(), builder);
	}
}
