/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Access;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Phase;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Table;

class HsqlTemporaryMigrationTest {
	@Test
	void preserveRowsTablesRetainSessionScopeThroughMigrationAndRecovery() throws Exception {
		for (boolean fail : java.util.List.of(false, true))
			try (var c = DriverManager.getConnection("jdbc:hsqldb:mem:temporary-migration-" + fail, "SA", "");
					var s = c.createStatement()) {
				s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				s.execute("CREATE GLOBAL TEMPORARY TABLE APP.T(V INTEGER) ON COMMIT PRESERVE ROWS");
				s.execute("INSERT INTO APP.T VALUES(2),(3)");
				var source = read(c);
				assertEquals(Table.TableType.Temporary,
						source.getSchemas().get("APP").getTables().get("T").getTableType());
				var target = source.clone();
				var table = target.getSchemas().get("APP").getTables().get("T");
				table.getColumns().get("V").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
				if (fail)
					table.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("BAD", "V<0"));
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
				var plan = migration.planAll(source, target);
				if (fail) {
					var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
							() -> migration.execute(c, plan, Access.EXCLUSIVE));
					assertEquals(Phase.RESTORE, failure.getPhase());
					assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
				} else
					assertEquals(Phase.COMPLETE, migration.execute(c, plan, Access.EXCLUSIVE).phase());
				c.commit();
				try (var rs = s.executeQuery("SELECT SUM(V) FROM APP.T")) {
					assertTrue(rs.next());
					assertEquals(5, rs.getInt(1));
				}
				assertEquals(Table.TableType.Temporary,
						read(c).getSchemas().get("APP").getTables().get("T").getTableType());
				try (var other = DriverManager.getConnection("jdbc:hsqldb:mem:temporary-migration-" + fail, "SA", "");
						var query = other.createStatement();
						var rs = query.executeQuery("SELECT COUNT(*) FROM APP.T")) {
					assertTrue(rs.next());
					assertEquals(0, rs.getInt(1));
				}
			}
	}

	@Test
	void deleteRowsTablesAreRecreatedButRejectedByAutocommitMigration() throws Exception {
		try (var c = DriverManager.getConnection("jdbc:hsqldb:mem:temporary-delete-boundary", "SA", "");
				var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE GLOBAL TEMPORARY TABLE APP.T(V INTEGER) ON COMMIT DELETE ROWS");
			var source = read(c);
			var target = source.clone();
			target.getSchemas().get("APP").getTables().get("T").getColumns().get("V")
					.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
			assertTrue(assertThrows(IllegalArgumentException.class, () -> migration.planAll(source, target))
					.getMessage().contains("PRESERVE"));
			var table = source.getSchemas().get("APP").getTables().get("T");
			s.execute("DROP TABLE APP.T");
			for (var op : DialectResolver.getInstance().getDialect(c).createSqlFactoryRegistry().createSql(table,
					com.sqlapp.data.db.sql.SqlType.CREATE))
				s.execute(op.getSqlText());
			s.execute("INSERT INTO APP.T VALUES 1");
			try (var rs = s.executeQuery("SELECT COUNT(*) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
		}
	}

	private Catalog read(Connection c) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader();
		reader.getSchemaReader().setSchemaName("APP");
		var catalog = new Catalog("PUBLIC");
		catalog.getSchemas().addAll(reader.getSchemaReader().getAllFull(c));
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(c));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(c));
		catalog.getRoutinePrivileges().addAll(reader.getRoutinePrivilegeReader().getAll(c));
		return catalog;
	}
}
