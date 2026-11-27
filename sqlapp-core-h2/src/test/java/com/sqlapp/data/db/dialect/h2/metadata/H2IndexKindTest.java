/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest.create;
import static com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest.read;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

class H2IndexKindTest {
	@Test
	void recreatesHashSpatialAndUniqueNullTreatment() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement()) {
			statement.execute("CREATE TABLE T(A INT, B INT, G GEOMETRY NOT NULL)");
			statement.execute("CREATE UNIQUE NULLS NOT DISTINCT INDEX UNN ON T(A)");
			statement.execute("CREATE UNIQUE NULLS ALL DISTINCT INDEX UALL ON T(A, B)");
			statement.execute("CREATE UNIQUE INDEX UDEF ON T(B)");
			statement.execute("CREATE HASH INDEX H ON T(B)");
			statement.execute("CREATE SPATIAL INDEX S ON T(G)");
			Table table = read(source, "PUBLIC").getTables().get("T");
			assertIndexes(table);
			create(target, table);
			Table copy = read(target, "PUBLIC").getTables().get("T");
			assertIndexes(copy);
			try (var copyStatement = target.createStatement()) {
				copyStatement.execute("INSERT INTO T VALUES(NULL, 1, 'POINT(0 0)')");
				assertThrows(SQLException.class,
						() -> copyStatement.execute("INSERT INTO T VALUES(NULL, 2, 'POINT(0 0)')"));
			}
		}
	}

	private static void assertIndexes(Table table) {
		var indexes = table.getIndexes();
		assertEquals("NO", indexes.get("UNN").getSpecifics().get("h2.nullsDistinct"));
		assertEquals("ALL", indexes.get("UALL").getSpecifics().get("h2.nullsDistinct"));
		assertEquals("YES", indexes.get("UDEF").getSpecifics().get("h2.nullsDistinct"));
		assertEquals(IndexType.Hash, indexes.get("H").getIndexType());
		assertNull(indexes.get("H").getSpecifics().get("h2.nullsDistinct"));
		assertEquals(IndexType.Spatial, indexes.get("S").getIndexType());
		assertNull(indexes.get("UNN").getIndexType());
	}

	@Test
	void createsUnsupportedForeignIndexTypesAsOrdinaryIndexes() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(A INT)");
			var table = read(connection, "PUBLIC").getTables().get("T");
			var index = new Index("X_A").setIndexType(IndexType.Gin);
			index.getColumns().add("A");
			table.getIndexes().add(index);
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			String sql = registry.createSql(index, SqlType.CREATE).get(0).getSqlText();
			assertFalse(sql.contains("GIN"), sql);
			statement.execute(sql);
		}
	}

	@Test
	void validatesNullTreatmentByTargetVersion() {
		var table = new Table("T");
		var index = new Index("U").setUnique(true);
		index.getColumns().add("A");
		table.getIndexes().add(index);
		var resolver = DialectResolver.getInstance();
		var h2_21 = resolver.getDialect("H2", 2, 1, 214).createSqlFactoryRegistry();
		var h2_23 = resolver.getDialect("H2", 2, 3, 232).createSqlFactoryRegistry();

		index.getSpecifics().put("h2.nullsDistinct", "YES");
		assertFalse(h2_21.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("NULLS"));
		assertTrue(h2_23.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("UNIQUE NULLS DISTINCT INDEX"));

		index.getSpecifics().put("h2.nullsDistinct", "NO");
		assertThrows(IllegalArgumentException.class, () -> h2_21.createSql(index, SqlType.CREATE));

		index.setUnique(false);
		assertThrows(IllegalArgumentException.class, () -> h2_23.createSql(index, SqlType.CREATE));

		index.getSpecifics().remove("h2.nullsDistinct");
		index.setUnique(true).setIndexType(IndexType.Spatial);
		assertThrows(IllegalArgumentException.class, () -> h2_23.createSql(index, SqlType.CREATE));
	}
}
