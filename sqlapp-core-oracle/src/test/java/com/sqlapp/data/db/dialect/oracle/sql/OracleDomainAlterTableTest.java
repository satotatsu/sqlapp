/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleDomainAlterTableTest {
	@Test
	void rowGroupsDetachAndAttachInComponentOrder() {
		Schema schema = new Schema("APP");
		Domain domain = new Domain("PAIR"); schema.getDomains().add(domain);
		for (String part : java.util.List.of("X", "Y")) domain.getColumns().add(new DomainColumn(part).setDataType(DataType.INT));
		Table table = new Table("T"); schema.getTables().add(table);
		for (String part : java.util.List.of("Y", "X")) table.getColumns().add(new Column("C" + part).setDataType(DataType.INT).setDomain(domain).setDomainColumnName(part));
		Schema targetSchema = schema.clone(); Table target = targetSchema.getTables().get("T");
		for (Column column : target.getColumns()) column.setDomain(null);
		SqlFactory<Table> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		var operations = factory.createDiffSql(table.diff(target));
		assertEquals(2, operations.size());
		assertTrue(operations.get(0).getSqlText().replaceAll("\\s+", " ").contains("MODIFY ( CX, CY) DROP DOMAIN"), operations.toString());
		operations = factory.createDiffSql(target.diff(table));
		assertEquals(2, operations.size());
		assertTrue(operations.get(1).getSqlText().replaceAll("\\s+", " ").contains("MODIFY ( CX, CY) ADD DOMAIN APP.PAIR"), operations.toString());
		Table partial = targetSchema.clone().getTables().get("T");
		partial.getColumns().get("CX").setDomain(domain).setDomainColumnName("X");
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(target.diff(partial)));
		domain.getSpecifics().put(OracleDomainAssociationUtils.KIND, "FLEXIBLE");
		domain.getColumns().get("Y").getSpecifics().put(OracleDomainAssociationUtils.DISCRIMINANT, true);
		operations = factory.createDiffSql(target.diff(table));
		assertEquals(2, operations.size());
		assertTrue(operations.get(1).getSqlText().replaceAll("\\s+", " ").contains("MODIFY ( CX, CY) ADD DOMAIN APP.PAIR"), operations.toString());
		assertFalse(operations.get(1).getSqlText().contains("USING"));
		SqlFactory<Table> older = DialectHolder.oracle23aiDialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> older.createDiffSql(target.diff(table)));
		Table empty = schema.clone().getTables().get("T"); empty.getColumns().clear();
		operations = factory.createDiffSql(empty.diff(table));
		assertEquals(2, operations.size());
		assertTrue(operations.get(1).getSqlText().contains("ADD DOMAIN APP.PAIR"));
		operations = factory.createDiffSql(table.diff(empty));
		assertEquals(2, operations.size());
		assertTrue(operations.get(0).getSqlText().contains("DROP DOMAIN"));
	}

	private Table table() {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("V").setDataType(DataType.VARCHAR).setLength(40)
				.setDomain(new Domain("D").setSchemaName("APP")));
		return table;
	}
	@Test
	void removesAndReplacesScalarDomainWithSeparateStatements() {
		for (var dialect : java.util.List.of(DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			Table original = table(); Table target = original.clone();
			target.getColumns().get(0).setDomain(null);
			SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
			var sql = factory.createDiffSql(original.diff(target));
			assertEquals(2, sql.size());
			assertTrue(sql.get(0).getSqlText().replaceAll("\\s+", " ").contains("MODIFY ( V) DROP DOMAIN"), sql.toString());
			assertFalse(sql.get(1).getSqlText().contains("DOMAIN"));
			target.getColumns().get(0).setDomain(new Domain("Other Domain").setSchemaName("Elsewhere"));
			sql = factory.createDiffSql(original.diff(target));
			assertEquals(2, sql.size());
			assertTrue(sql.get(1).getSqlText().contains("DOMAIN \"Elsewhere\".\"Other Domain\""), sql.toString());
		}
	}
	@Test
	void unchangedAssociationDoesNotDropDomain() {
		Table original = table(); Table target = original.clone();
		target.getColumns().get(0).setLength(80);
		SqlFactory<Table> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		var operations = factory.createDiffSql(original.diff(target));
		assertEquals(1, operations.size());
		assertFalse(operations.get(0).getSqlText().contains("DROP DOMAIN"));
		assertTrue(operations.get(0).getSqlText().contains("VARCHAR2(80) DOMAIN APP.D"));
		assertFalse(DialectHolder.oracle21cDialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER)
				instanceof Oracle23aiAlterTableFactory);
	}

	@Test
	void rowAssociationChangesAndUnsafeRenameFailExplicitly() {
		Table original = table();
		original.getColumns().get(0).getDomain().getColumns().add(new DomainColumn("X").setDataType(DataType.INT));
		Table target = original.clone(); target.getColumns().get(0).setDomain(null);
		SqlFactory<Table> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(target)));
		Table scalar = table(); Table renamed = scalar.clone();
		renamed.getColumns().get(0).setName("NEW_V").setLength(80);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(scalar.diff(renamed)));
	}
}
