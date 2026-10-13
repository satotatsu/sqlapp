/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class EdbOracleLinksSqlTest {
	@Test void createsAndDropsLocalAndRemoteSynonyms() throws Exception {
		for(int major=14;major<=18;major++) {
			var registry=DialectResolver.getInstance().getDialect("edb-oracle",major,0,null).createSqlFactoryRegistry();
			var synonym=new Synonym("Alias");synonym.setSchemaName("Other.Schema");
			synonym.setObjectSchemaName("Target.Schema");synonym.setObjectName("Order");synonym.setDbLinkName("Remote.Link");
			Synonym restored=SchemaUtils.readXml(new java.io.StringReader(synonym.asXml()));
			String sql=registry.createSql(restored,SqlType.CREATE).getFirst().getSqlText();
			assertTrue(sql.contains("CREATE OR REPLACE SYNONYM"),sql);
			assertTrue(sql.contains("\"Target.Schema\".\"Order\""),sql);
			assertTrue(sql.contains("@\"Remote.Link\""),sql);
			assertTrue(registry.createSql(restored,SqlType.DROP).getFirst().getSqlText().contains("DROP SYNONYM"));
			var shared=new PublicSynonym("Alias");shared.setObjectSchemaName("public");shared.setObjectName("target");
			assertTrue(registry.createSql(shared,SqlType.CREATE).getFirst().getSqlText().contains("PUBLIC SYNONYM"));
			assertTrue(registry.createSql(shared,SqlType.DROP).getFirst().getSqlText().contains("DROP PUBLIC SYNONYM"));
		}
	}
	@Test void requiresExplicitLinkRecreationInputs() {
		var registry=new EdbOracle17().createSqlFactoryRegistry();
		var link=new DbLink("remote");
		assertThrows(IllegalArgumentException.class,() -> registry.createSql(link,SqlType.CREATE));
		link.getSpecifics().put("edb.dbLink.transport","postgres_fdw");
		link.setDataSource("host=localhost dbname=example");link.setUserId("user");
		assertThrows(IllegalArgumentException.class,() -> registry.createSql(link,SqlType.CREATE));
		link.setPassword("fixture");link.setPasswordEncrypted(true);
		assertThrows(IllegalArgumentException.class,() -> registry.createSql(link,SqlType.CREATE));
		link.setPasswordEncrypted(false);link.getSpecifics().put("edb.dbLink.transport","postgres_fdw;SELECT 1");
		assertThrows(IllegalArgumentException.class,() -> registry.createSql(link,SqlType.CREATE));
	}
	@Test void preservesLiteralQuotesAndBackslashesAndPublicLinkSyntax() {
		var registry=new EdbOracle17().createSqlFactoryRegistry();
		var link=new DbLink("Remote.Link");link.setUserId("Remote.User");link.setPassword("'fixture\\'");
		link.setDataSource("host=localhost dbname='sample'");link.getSpecifics().put("edb.dbLink.transport","postgres_fdw");
		String sql=registry.createSql(link,SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("CREATE DATABASE LINK \"Remote.Link\""),sql);
		assertTrue(sql.contains("IDENTIFIED BY E'''fixture\\\\'''"),sql);
		assertTrue(sql.contains("USING postgres_fdw E'host=localhost dbname=''sample'''"),sql);
		assertFalse(sql.contains("null"),sql);
		assertTrue(registry.createSql(link,SqlType.DROP).getFirst().getSqlText().contains("DROP DATABASE LINK"));
		var shared=new PublicDbLink("Shared");shared.setUserId("remote");shared.setPassword("fixture");
		shared.setDataSource("//localhost/example");shared.getSpecifics().put("edb.dbLink.transport","oci");
		assertTrue(registry.createSql(shared,SqlType.CREATE).getFirst().getSqlText().contains("CREATE PUBLIC DATABASE LINK"));
		assertTrue(registry.createSql(shared,SqlType.DROP).getFirst().getSqlText().contains("DROP PUBLIC DATABASE LINK"));
	}
	@Test void rejectsSynonymsWithoutTargets() {
		var registry=new EdbOracle17().createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class,() -> registry.createSql(new Synonym("invalid"),SqlType.CREATE));
	}
}
