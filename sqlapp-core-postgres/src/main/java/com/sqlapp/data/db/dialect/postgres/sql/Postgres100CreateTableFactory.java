/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Table;

/** Declarative partition parents were introduced in PostgreSQL 10. */
public class Postgres100CreateTableFactory extends Postgres82CreateTableFactory {
	@Override
	protected void addOption(Table table, PostgresSqlBuilder builder) {
		if(table.getPartitioning()!=null) {
			var partitioning=table.getPartitioning();
			var type=partitioning.getPartitioningType();
			if(type==null || !java.util.List.of("RANGE","LIST","HASH").contains(type.getSqlValue())) {
				throw new IllegalArgumentException("PostgreSQL partitioning requires RANGE, LIST or HASH: "+table.getName());
			}
			if(type.getSqlValue().equals("HASH") && getDialect().compareTo(DialectHolder.postgreSQL110)<0) {
				throw new IllegalArgumentException("HASH partitioning requires PostgreSQL 11 or later.");
			}
			builder.lineBreak().partitionBy().space();
			String deparsed=table.getSpecifics().get("postgres.partitionBy");
			if(deparsed!=null) builder._add(deparsed);
			else {
				if(partitioning.getPartitioningColumns().isEmpty()) throw new IllegalArgumentException("Partition key columns are required: "+table.getName());
				builder._add(type.getSqlValue()).space().brackets(()->{
					int index=0;
					for(var column:partitioning.getPartitioningColumns()) builder.comma(index++>0).name(column.getName());
				});
			}
		}
		super.addOption(table,builder);
	}
}
