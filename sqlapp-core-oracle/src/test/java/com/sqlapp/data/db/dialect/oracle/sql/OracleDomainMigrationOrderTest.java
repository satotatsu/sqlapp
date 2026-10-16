/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleDomainMigrationOrderTest {
	@Test
	void schemaRejectsRemovingReferencedDomainWithoutDetachment() {
		Schema original = new Schema("APP");
		Domain domain = new Domain("D").setDataType(DataType.INT); original.getDomains().add(domain);
		Table table = new Table("T"); original.getTables().add(table);
		table.getColumns().add(new Column("V").setDataType(DataType.INT).setDomain(domain));
		Schema target = original.clone(); target.getDomains().clear();
		SqlFactory<Schema> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target, State.Modified);
		assertTrue(assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(target))).getMessage().contains("APP.T.V"));
	}

	@Test
	void schemaRemovesDomainAfterTableDetachmentAndRoutesDomainUpdates() {
		Schema original = new Schema("APP");
		Domain domain = new Domain("D").setDataType(DataType.INT); original.getDomains().add(domain);
		Table table = new Table("T"); original.getTables().add(table);
		table.getColumns().add(new Column("V").setDataType(DataType.INT).setDomain(domain));
		Schema target = original.clone(); target.getTables().get("T").getColumns().get("V").setDomain(null);
		target.getDomains().clear();
		SqlFactory<Schema> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target, State.Modified);
		var operations = factory.createDiffSql(original.diff(target));
		assertEquals(SqlType.DROP, operations.get(operations.size()-1).getSqlType());
		assertTrue(operations.get(operations.size()-1).getOriginal() instanceof Domain);
		assertTrue(operations.get(0).getSqlText().contains("DROP DOMAIN"));
		assertTrue(operations.get(0).getOriginal() instanceof Column);
		Schema modified = original.clone(); modified.getDomains().get("D").getSpecifics().put(Oracle23aiCreateDomainFactory.DISPLAY, "TO_CHAR(VALUE)");
		operations = factory.createDiffSql(original.diff(modified));
		assertEquals(1, operations.size());
		assertTrue(operations.get(0).getSqlText().contains("ADD DISPLAY TO_CHAR(VALUE)"));
	}
	@Test
	void catalogRemovesCrossSchemaDomainAfterAllDependentsAndRequiresQualifiedNames() {
		Catalog original = new Catalog("CAT");
		Schema base = new Schema("BASE"); original.getSchemas().add(base);
		Schema app = new Schema("APP"); original.getSchemas().add(app);
		Domain domain = new Domain("D").setDataType(DataType.INT); base.getDomains().add(domain);
		Table table = new Table("T"); app.getTables().add(table);
		table.getColumns().add(new Column("V").setDataType(DataType.INT).setDomain(domain));
		Catalog target = original.clone(); target.getSchemas().get("BASE").getDomains().clear();
		target.getSchemas().get("APP").getTables().get("T").getColumns().get("V").setDomain(null);
		SqlFactory<Catalog> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target, State.Modified);
		var operations = factory.createDiffSql(original.diff(target));
		assertTrue(operations.get(operations.size()-1).getOriginal() instanceof Domain);
		assertTrue(operations.get(operations.size()-1).getSqlText().contains("BASE.D"));
		assertTrue(operations.stream().limit(operations.size()-1).anyMatch(o -> o.getOriginal() instanceof Column && o.getSqlText().contains("DROP DOMAIN")));
		factory.getOptions().setDecorateSchemaName(false);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(target)));
	}
}
