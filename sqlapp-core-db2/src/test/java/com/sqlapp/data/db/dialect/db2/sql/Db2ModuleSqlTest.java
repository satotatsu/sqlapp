/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.SchemaUtils;

class Db2ModuleSqlTest {
	private Package module(String type) {
		var module = new Package("M.Name").setSchemaName("A.Schema");
		module.getSpecifics().putAll(Map.of("DB2_OBJECT_TYPE", "MODULE", "MODULETYPE", type, "MEMBER_COUNT", "0"));
		return module;
	}
	private List<String> sql(Package module, SqlType type) {
		SqlFactory<Package> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(module, type);
		return factory.createSql(module).stream().map(SqlOperation::getSqlText).toList();
	}
	@Test void emptyModulesAndPrivateAndPublicAliasesUseNativeSyntax() {
		var module = module("M");
		assertEquals(List.of("CREATE MODULE \"A.Schema\".\"M.Name\""), sql(module, SqlType.CREATE));
		assertEquals(List.of("DROP MODULE \"A.Schema\".\"M.Name\""), sql(module, SqlType.DROP));
		module = module("A");
		module.getSpecifics().putAll(Map.of("BASE_MODULESCHEMA", "Base.Schema", "BASE_MODULENAME", "B\"M"));
		assertEquals(List.of("CREATE ALIAS \"A.Schema\".\"M.Name\" FOR MODULE \"Base.Schema\".\"B\"\"M\""), sql(module, SqlType.CREATE));
		assertEquals(List.of("DROP ALIAS \"A.Schema\".\"M.Name\" FOR MODULE"), sql(module, SqlType.DROP));
		module.setSchemaName("SYSPUBLIC");
		assertTrue(sql(module, SqlType.CREATE).getFirst().startsWith("CREATE PUBLIC ALIAS"));
		assertTrue(sql(module, SqlType.DROP).getFirst().startsWith("DROP PUBLIC ALIAS"));
		module.setRemarks("alias's comment");
		assertEquals("COMMENT ON PUBLIC ALIAS \"SYSPUBLIC\".\"M.Name\" FOR MODULE IS 'alias''s comment'", sql(module, SqlType.CREATE).getLast());
	}
	@Test void membersRequireOrderedCompleteDefinitionsAndSurviveXml() throws Exception {
		var module = module("M"); module.getSpecifics().put("MEMBER_COUNT", "2");
		assertThrows(IllegalArgumentException.class, () -> sql(module, SqlType.CREATE));
		module.getDefinition().add("CREATE MODULE \"A.Schema\".\"M.Name\"");
		module.getDefinition().add("ALTER MODULE \"A.Schema\".\"M.Name\" PUBLISH PROCEDURE P() BEGIN END");
		Package restored = SchemaUtils.readXml(new java.io.StringReader(module.asXml()));
		assertEquals(module, restored); assertEquals(module, module.clone());
		assertEquals(module.getDefinition(), sql(restored, SqlType.CREATE));
	}
	@Test void incompletePackagesAndUnknownCountsAndOldVersionsFailClearly() {
		var module = module("P");
		assertThrows(IllegalArgumentException.class, () -> sql(module, SqlType.CREATE));
		module.getSpecifics().put("MODULETYPE", "M"); module.getSpecifics().remove("MEMBER_COUNT");
		assertThrows(IllegalArgumentException.class, () -> sql(module, SqlType.CREATE));
		SqlFactory<Package> factory = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry().getSqlFactory(module, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(module));
	}
	@Test void diffCannotImplicitlyDropMembersOrReplaceModules() {
		var original = module("M"); var target = original.clone(); target.setRemarks("modified");
		SqlFactory<Package> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(target)));
		assertTrue(factory.createDiffSql(original.diff(original.clone())).isEmpty());
	}
}
