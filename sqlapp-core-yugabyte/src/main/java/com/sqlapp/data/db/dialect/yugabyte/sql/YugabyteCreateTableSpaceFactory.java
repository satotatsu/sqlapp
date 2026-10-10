/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.sql;

import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.TableSpace;

public class YugabyteCreateTableSpaceFactory extends AbstractCreateNamedObjectFactory<TableSpace,PostgresSqlBuilder> {
	@Override
	protected void addCreateObject(TableSpace tableSpace, PostgresSqlBuilder builder) {
		builder._add("CREATE TABLESPACE").space().name(tableSpace);
		if (tableSpace.getOwnerName() != null) builder.space()._add("OWNER").space().name(tableSpace.getOwnerName());
		String placement = tableSpace.getSpecifics().get("YSQL_REPLICA_PLACEMENT");
		if (placement == null || placement.isBlank()) throw new IllegalArgumentException("YSQL_REPLICA_PLACEMENT is required to create a YSQL tablespace");
		builder.space()._add("WITH (replica_placement = ").sqlChar(placement)._add(")");
	}
}
