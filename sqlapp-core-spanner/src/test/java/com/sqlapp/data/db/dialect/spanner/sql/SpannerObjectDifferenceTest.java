/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Synonym;

class SpannerObjectDifferenceTest {

	@Test
	void routesAddedAndDeletedObjectsToNativeFactories() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final DbObject<?> object : new DbObject<?>[] {
				new Schema("Sales").setDialect(DialectHolder.defaultDialect),
				new Sequence("OrderSeq").setSchemaName("Sales").setDialect(DialectHolder.defaultDialect),
				new Synonym("LegacyOrders").setSchemaName("Sales").setObjectName("Orders")
						.setDialect(DialectHolder.defaultDialect)}) {
			final var added = new DbObjectDifference(null, object, new EqualsHandler());
			assertEquals(registry.createSql(object, SqlType.CREATE).getFirst().getSqlText(),
					registry.createSql(added).getFirst().getSqlText());
			final var deleted = added.reverse();
			assertEquals(1, registry.createSql(deleted).size());
			assertEquals(registry.createSql(object, SqlType.DROP).getFirst().getSqlText(),
					registry.createSql(deleted).getFirst().getSqlText());
		}
	}
}
