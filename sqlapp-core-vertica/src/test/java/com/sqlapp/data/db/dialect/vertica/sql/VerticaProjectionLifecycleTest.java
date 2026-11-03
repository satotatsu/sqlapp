package com.sqlapp.data.db.dialect.vertica.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class VerticaProjectionLifecycleTest {
	@Test
	void dropUsesModeledBuddyNameAndExplicitExistenceOption() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setDropIfExists(true);
		Projection p = new Projection("buddy\"_b1");
		p.setSchemaName("physical space");
		p.getSpecifics().put("PROJECTION_BASENAME", "all_buddies");
		String sql = registry.createSql(p, SqlType.DROP).get(0).getSqlText();
		assertEquals("DROP PROJECTION IF EXISTS \"physical space\".\"buddy\"\"_b1\"", sql);
		assertFalse(sql.contains("all_buddies"));
		assertFalse(sql.contains("CASCADE"));
		var deleted = p.diff((Projection) null);
		assertEquals(sql, registry.getSqlFactory(deleted).createDiffSql(deleted).get(0).getSqlText());
		registry.getOptions().setDropIfExists(false);
		assertFalse(registry.createSql(p, SqlType.DROP).get(0).getSqlText().contains("IF EXISTS"));
	}

	@Test
	void renameDifferenceProducesOnlyAlterAndRetainsModelIdentity() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		Projection original = new Projection("old name");
		original.setSchemaName("physical");
		Projection target = original.clone();
		target.setName("new\"name");
		SqlFactory<Projection> factory = registry.getSqlFactory(original, SqlType.ALTER);
		var sql = factory.createDiffSql(original.diff(target));
		assertEquals(1, sql.size());
		assertEquals("ALTER PROJECTION \"physical\".\"old name\" RENAME TO \"new\"\"name\"", sql.get(0).getSqlText());
		assertSame(original, sql.get(0).getOriginal());
		assertSame(target, sql.get(0).getTarget());
		assertTrue(factory.createDiffSql(original.diff(original.clone())).isEmpty());
		target.setStatement("SELECT different_layout FROM t");
		assertThrows(UnsupportedOperationException.class, () -> factory.createDiffSql(original.diff(target)));
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(original));
	}
}
