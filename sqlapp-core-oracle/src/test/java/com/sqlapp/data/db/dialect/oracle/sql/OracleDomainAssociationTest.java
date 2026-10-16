/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.sql.SqlType;

class OracleDomainAssociationTest {
	@Test
	void opaqueCharacterDomainRequiresExplicitLengthSemanticsForInference() {
		Schema schema = new Schema("APP");
		Domain domain = new Domain("ADDRESS").setDefinition("CREATE DOMAIN APP.ADDRESS AS (CITY AS VARCHAR2(80 BYTE), ZIP AS NUMBER)");
		schema.getDomains().add(domain);
		DomainColumn city = new DomainColumn("CITY").setDataType(DataType.VARCHAR).setLength(80);
		domain.getColumns().add(city); domain.getColumns().add(new DomainColumn("ZIP").setDataType(DataType.INT));
		Table table = new Table("T"); schema.getTables().add(table);
		table.getColumns().add(new Column("LOCATION").setDomain(domain).setDomainColumnName("CITY"));
		table.getColumns().add(new Column("POSTCODE").setDomain(domain).setDomainColumnName("ZIP"));
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertTrue(assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE)).getMessage().contains("BYTE/CHAR"));
		city.setCharacterSemantics(CharacterSemantics.Byte);
		assertTrue(registry.createSql(table, SqlType.CREATE).get(0).getSqlText().toUpperCase(java.util.Locale.ROOT).contains("VARCHAR2(80 BYTE)"));
		city.setCharacterSemantics((CharacterSemantics) null);
		table.getColumns().get("LOCATION").setDataType(DataType.VARCHAR).setLength(80).setCharacterSemantics(CharacterSemantics.Byte);
		assertTrue(registry.createSql(table, SqlType.CREATE).get(0).getSqlText().toUpperCase(java.util.Locale.ROOT).contains("VARCHAR2(80 BYTE)"));
	}

	@Test
	void flexibleUsingMappingsSurviveXmlAndRejectIncompleteGroups() throws Exception {
		Schema schema = new Schema("APP");
		Domain domain = new Domain("CHOICE"); schema.getDomains().add(domain);
		domain.getSpecifics().put(OracleDomainAssociationUtils.KIND, "FLEXIBLE");
		domain.getColumns().add(new DomainColumn("VALUE_PART"));
		DomainColumn discriminator = new DomainColumn("KIND");
		discriminator.getSpecifics().put(OracleDomainAssociationUtils.DISCRIMINANT, true);
		domain.getColumns().add(discriminator);
		Table table = new Table("T"); schema.getTables().add(table);
		table.getColumns().add(new Column("CATEGORY").setDataType(DataType.INT).setDomain(domain).setDomainColumnName("KIND"));
		table.getColumns().add(new Column("PAYLOAD").setDataType(DataType.VARCHAR).setLength(80).setDomain(domain).setDomainColumnName("VALUE_PART"));
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		Table restoredTable = restored.getTables().get("T");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(restored.getDomains().get("CHOICE"), SqlType.CREATE));
		String sql = registry.createSql(restoredTable, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("DOMAIN APP.CHOICE( PAYLOAD) USING ( CATEGORY)"), sql);
		assertFalse(sql.contains("PAYLOAD VARCHAR2(80) DOMAIN"), sql);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle23aiDialect.createSqlFactoryRegistry().createSql(restoredTable, SqlType.CREATE));
		restoredTable.getColumns().remove("CATEGORY");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(restoredTable, SqlType.CREATE));
	}

	@Test
	void flexibleAssociationFailsRatherThanEmitScalarDomainSyntax() {
		Domain domain = new Domain("CHOICE").setSchemaName("APP");
		domain.getSpecifics().put("oracle.domain.kind", "FLEXIBLE");
		Table table = new Table("T");
		table.getColumns().add(new Column("V").setDataType(DataType.INT).setDomain(domain));
		var error = assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect
				.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		assertTrue(error.getMessage().contains("USING"));
		assertTrue(error.getMessage().contains("APP.CHOICE"));
	}
	@Test
	void repeatedRowAssociationsRequireDistinctGroups() {
		Domain domain = new Domain("PAIR");
		domain.getColumns().add(new DomainColumn("X").setDataType(DataType.INT));
		domain.getColumns().add(new DomainColumn("Y").setDataType(DataType.INT));
		Table table = new Table("T");
		for (String suffix : java.util.List.of("A", "B")) {
			for (String part : java.util.List.of("X", "Y")) {
				table.getColumns().add(new Column(part + suffix).setDomain(domain).setDomainColumnName(part));
			}
		}
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		for (Column column : table.getColumns()) column.getSpecifics().put(Oracle23aiCreateTableFactory.DOMAIN_ASSOCIATION_ID,
				column.getName().substring(1));
		String sql = registry.createSql(table, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("DOMAIN PAIR( XA, YA)"), sql);
		assertTrue(sql.contains("DOMAIN PAIR( XB, YB)"), sql);
	}
	@Test
	void scalarReferencePreservesPhysicalTypeAndQuotedDomain() throws Exception {
		Table table = new Table("T");
		table.getColumns().add(new Column("C").setDataType(DataType.VARCHAR).setLength(40)
				.setDomain(new Domain("Email Domain").setSchemaName("Other Schema")));
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("VARCHAR2(40) DOMAIN \"Other Schema\".\"Email Domain\""), sql);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle21cDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
	}

	@Test
	void rowAssociationUsesDomainOrderAndTracksComponentRenames() throws Exception {
		Schema schema = new Schema("APP");
		Domain domain = new Domain("ADDRESS");
		domain.getColumns().add(new DomainColumn("CITY").setDataType(DataType.VARCHAR).setLength(80));
		domain.getColumns().add(new DomainColumn("ZIP").setDataType(DataType.INT));
		schema.getDomains().add(domain);
		Table table = new Table("T"); schema.getTables().add(table);
		Column zip = new Column("POSTCODE").setDomain(domain).setDomainColumnName("ZIP");
		Column city = new Column("LOCATION").setDomain(domain).setDomainColumnName("CITY");
		table.getColumns().add(zip); table.getColumns().add(city);
		assertEquals("CITY", city.getDomainColumnName());
		domain.getColumns().get("CITY").setName("TOWN");
		assertEquals("TOWN", city.getDomainColumnName());
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		Table restoredTable = restored.getTables().get("T");
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restoredTable, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("LOCATION VARCHAR2(80)"), sql);
		assertTrue(sql.replaceAll("\\s+", " ").contains("DOMAIN APP.ADDRESS( LOCATION, POSTCODE)"), sql);
		restoredTable.getColumns().remove("LOCATION");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restoredTable, SqlType.CREATE));
	}
}
