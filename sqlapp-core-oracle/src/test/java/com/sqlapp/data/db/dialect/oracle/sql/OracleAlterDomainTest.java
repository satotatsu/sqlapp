/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleAlterDomainTest {
	@Test
	void addsModifiesAndDropsExpressionsAndAnnotationsThroughStateRegistry() {
		for (var dialect : java.util.List.of(DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			Domain original = new Domain("Label Domain").setSchemaName("APP").setDataType(DataType.INT);
			original.getSpecifics().put(Oracle23aiCreateDomainFactory.DISPLAY, "TO_CHAR(VALUE)");
			OracleAnnotationUtils.setAnnotation(original, "Label", "Old");
			OracleAnnotationUtils.setAnnotation(original, "Removed", "gone");
			Domain target = original.clone();
			target.getSpecifics().put(Oracle23aiCreateDomainFactory.DISPLAY, "LOWER(VALUE)");
			target.getSpecifics().put(Oracle23aiCreateDomainFactory.ORDER, "-VALUE");
			OracleAnnotationUtils.setAnnotation(target, "Label", "O'Brien");
			target.getSpecifics().remove(OracleAnnotationUtils.PREFIX + "Removed");
			OracleAnnotationUtils.setAnnotation(target, "New Label", "");
			SqlFactory<Domain> factory = dialect.createSqlFactoryRegistry().getSqlFactory(target, State.Modified);
			var sql = factory.createDiffSql(original.diff(target)).stream().map(o -> o.getSqlText()).toList();
			assertEquals(5, sql.size(), sql.toString());
			assertTrue(sql.stream().allMatch(s -> s.startsWith("ALTER DOMAIN APP.\"Label Domain\"")), sql.toString());
			assertTrue(sql.stream().anyMatch(s -> s.contains("MODIFY DISPLAY LOWER(VALUE)")));
			assertTrue(sql.stream().anyMatch(s -> s.contains("ADD ORDER -VALUE")));
			assertTrue(sql.stream().anyMatch(s -> s.contains("REPLACE \"Label\" 'O''Brien'")), sql.toString());
			assertTrue(sql.stream().anyMatch(s -> s.contains("DROP \"Removed\"")), sql.toString());
			assertTrue(sql.stream().anyMatch(s -> s.contains("ADD \"New Label\"")), sql.toString());
			Domain empty = new Domain("Label Domain").setSchemaName("APP").setDataType(DataType.INT);
			assertTrue(factory.createDiffSql(original.diff(empty)).stream().anyMatch(o -> o.getSqlText().contains("DROP DISPLAY")));
			assertTrue(factory.createDiffSql(original.diff(original.clone())).isEmpty());
		}
	}
	@Test
	void unsupportedChangesFailRatherThanRecreateOrIgnore() {
		Domain original = new Domain("D").setDataType(DataType.INT);
		SqlFactory<Domain> factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER);
		Domain typed = original.clone().setDataType(DataType.VARCHAR).setLength(40);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(typed)));
		Domain renamed = original.clone().setName("NEW_D");
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(renamed)));
		Domain flexible = original.clone(); flexible.getSpecifics().put(OracleDomainAssociationUtils.KIND, "FLEXIBLE");
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(flexible)));
		Domain defined = original.clone().setDefinition("CREATE DOMAIN D AS NUMBER");
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(defined)));
	}
}
