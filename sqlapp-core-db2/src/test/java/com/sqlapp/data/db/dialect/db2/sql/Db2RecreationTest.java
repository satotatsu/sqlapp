/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.ParameterDirection;

class Db2RecreationTest extends AbstractDb2SqlFactoryTest {
	@Test
	void temporaryIndexesFollowSessionNameAndDoNotMutateSource() {
		Table table = new Table("T").setSchemaName("APP").setDialect(dialect);
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getIndexes().add("IX", table.getColumns().get("ID"));
		sqlFactoryRegistry.getTableOptions().setTemporaryTableNamePrefix(t -> "TMP_");
		sqlFactoryRegistry.getTableOptions().setTemporaryTableNameSuffix(t -> "_COPY");
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE_TEMPORARY);
		var statements = factory.createSql(table);
		assertEquals(2, statements.size());
		assertTrue(statements.get(0).getSqlText().contains("SESSION.TMP_T_COPY"));
		assertTrue(statements.get(1).getSqlText().contains("ON SESSION.TMP_T_COPY"), statements.toString());
		assertEquals("APP", table.getSchemaName());
		assertEquals("APP", table.getIndexes().get("IX").getSchemaName());
	}

	@Test
	void routineDropsUseSpecificNamesAndTableCreateSeparatesIndexesAndComments() {
		Function function = new Function("F").setSchemaName("APP").setSpecificNameLiteral("F_INT");
		assertEquals("DROP SPECIFIC FUNCTION APP.F_INT", sql(function, SqlType.DROP));
		Procedure procedure = new Procedure("P").setSchemaName("APP").setSpecificNameLiteral("P_INT");
		assertEquals("DROP SPECIFIC PROCEDURE APP.P_INT", sql(procedure, SqlType.DROP));
		Table table = new Table("T").setSchemaName("APP").setDialect(dialect).setRemarks("table's comment");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT).setRemarks("column's comment"));
		table.getIndexes().add("IX", table.getColumns().get("ID"));
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		var statements = factory.createSql(table).stream().map(o -> o.getSqlText().replaceAll("\\s+", " ").trim())
				.toList();
		assertEquals(4, statements.size());
		assertFalse(statements.get(0).contains("INDEX"));
		assertTrue(statements.contains("CREATE INDEX APP.IX ON APP.T ( ID )"), statements.toString());
		assertTrue(statements.contains("COMMENT ON TABLE APP.T IS 'table''s comment'"), statements.toString());
		assertTrue(statements.contains("COMMENT ON COLUMN APP.T.ID IS 'column''s comment'"), statements.toString());
		Index unsupported = table.getIndexes().get("IX").setIndexType(IndexType.Hash);
		assertThrows(IllegalArgumentException.class, () -> sql(unsupported, SqlType.CREATE));
	}

	@Test
	void functionSupportsEmptySignatureAndModeledReturnTypes() {
		Function function = new Function("F").setDialect(dialect).setSchemaName("APP");
		function.getReturning().setDataType(DataType.INT);
		function.setStatement("RETURN 1");
		String ddl = sql(function, SqlType.CREATE);
		assertTrue(ddl.contains("CREATE FUNCTION APP.F () RETURNS INTEGER"), ddl);
		assertTrue(ddl.endsWith("LANGUAGE SQL RETURN 1"), ddl);
		function.setFunctionType(FunctionType.Table);
		function.getReturning().toTable();
		function.getReturning().getTable().getColumns().add("C",
				c -> c.setDataType(DataType.DECIMAL).setLength(12).setScale(4));
		function.setStatement("RETURN SELECT 1 FROM SYSIBM.SYSDUMMY1");
		ddl = sql(function, SqlType.CREATE);
		assertTrue(ddl.contains("RETURNS TABLE ( C DECIMAL(12,4)"), ddl);
	}

	@Test
	void catalogReplaysRolesAndAllThreePrivilegeCollectionsAfterXml() throws Exception {
		Catalog catalog = new Catalog();
		catalog.getRoles().add(new Role("READER"));
		ObjectPrivilege table = new ObjectPrivilege().setSchemaName("APP").setObjectName("T").setPrivilege("SELECT")
				.setGranteeName("READER").setGrantable(true);
		table.getSpecifics().put("GRANTEE_TYPE", "R");
		catalog.getObjectPrivileges().add(table);
		ColumnPrivilege column = new ColumnPrivilege().setSchemaName("APP").setObjectName("T").setColumnName("C")
				.setPrivilege("UPDATE").setGranteeName("TEAM");
		column.getSpecifics().put("GRANTEE_TYPE", "G");
		catalog.getColumnPrivileges().add(column);
		RoutinePrivilege routine = new RoutinePrivilege().setSchemaName("APP").setObjectName("F")
				.setSpecificName("F_INT").setPrivilege("EXECUTE").setGranteeName("PUBLIC");
		routine.getSpecifics().put("ROUTINE_TYPE", "F");
		catalog.getRoutinePrivileges().add(routine);
		RoleMember member = new RoleMember().setMemberRoleName("READER").setGranteeName("USER1").setAdmin(true);
		member.getSpecifics().put("GRANTEE_TYPE", "U");
		catalog.getRoleMembers().add(member);
		Catalog restored = SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
		SqlFactory<Catalog> factory = sqlFactoryRegistry.getSqlFactory(restored, SqlType.CREATE);
		var statements = factory.createSql(restored).stream().map(o -> o.getSqlText().replaceAll("\\s+", " ").trim())
				.toList();
		assertEquals("CREATE ROLE READER", statements.get(0));
		assertTrue(statements.contains("GRANT SELECT ON TABLE APP.T TO ROLE READER WITH GRANT OPTION"),
				statements.toString());
		assertTrue(statements.contains("GRANT UPDATE ( C) ON TABLE APP.T TO GROUP TEAM"), statements.toString());
		assertTrue(statements.contains("GRANT EXECUTE ON SPECIFIC FUNCTION APP.F_INT TO PUBLIC"),
				statements.toString());
		assertTrue(statements.contains("GRANT ROLE READER TO USER USER1 WITH ADMIN OPTION"), statements.toString());
		assertEquals("REVOKE EXECUTE ON SPECIFIC FUNCTION APP.F_INT FROM PUBLIC RESTRICT",
				sql(routine, SqlType.REVOKE));
		assertThrows(IllegalArgumentException.class, () -> sql(column, SqlType.REVOKE));
		table.getSpecifics().remove("GRANTEE_TYPE");
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.GRANT));
	}

	private <T> String sql(T object, SqlType type) {
		SqlFactory<T> factory = sqlFactoryRegistry.getSqlFactory(object, type);
		return factory.createSql(object).get(0).getSqlText().replaceAll("\\s+", " ").trim();
	}

	@Test
	void procedureRetainsCompleteDefinition() {
		Procedure procedure = new Procedure("P");
		String ddl = "CREATE PROCEDURE APP.P() LANGUAGE SQL BEGIN VALUES 1; END";
		procedure.setDefinition(ddl);
		procedure.setStatement("VALUES 2");
		assertEquals(ddl, sql(procedure, SqlType.CREATE));
	}

	@Test
	void nativeProcedurePreservesBodyAndParameterModes() {
		Procedure procedure = new Procedure("P");
		procedure.setDialect(dialect);
		procedure.setSchemaName("APP");
		procedure.getArguments()
				.add(new NamedArgument("VALUE").setDataType(DataType.INT).setDirection(ParameterDirection.Output));
		procedure.setStatement("BEGIN SET VALUE = 1; END");
		String ddl = sql(procedure, SqlType.CREATE);
		assertTrue(ddl.contains("OUT VALUE INTEGER"), ddl);
		assertTrue(ddl.endsWith("LANGUAGE SQL BEGIN SET VALUE = 1; END"), ddl);
		assertFalse(ddl.contains(" AS "), ddl);
		assertThrows(IllegalArgumentException.class, () -> sql(new Procedure("EMPTY"), SqlType.CREATE));
	}

	@Test
	void includesAreSeparateFromKeysAndIndexSchemaIsRetained() {
		Table table = new Table("T").setSchemaName("APP");
		table.setDialect(dialect);
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getColumns().add("PAYLOAD", c -> c.setDataType(DataType.INT));
		Index index = new Index("IX").setUnique(true).setIndexType(IndexType.Clustered);
		table.getIndexes().add(index);
		index.getSpecifics().put("INDEX_SCHEMA", "OTHER");
		index.getColumns().add(table.getColumns().get("ID"), Order.Desc);
		index.getColumns().add(table.getColumns().get("PAYLOAD"), true);
		String ddl = sql(index, SqlType.CREATE);
		assertTrue(ddl.contains("OTHER.IX ON APP.T"), ddl);
		assertTrue(ddl.contains("ID DESC ) INCLUDE ( PAYLOAD ) CLUSTER"), ddl);
		index.setUnique(false);
		assertThrows(IllegalArgumentException.class, () -> sql(index, SqlType.CREATE));
	}

	@Test
	void aliasKindsSurviveCloneAndUseQualifiedTargets() {
		for (String type : java.util.List.of("TABLE", "SEQUENCE", "MODULE")) {
			Synonym alias = new Synonym("A").setSchemaName("APP").setObjectSchemaName("TARGET").setObjectName("BASE");
			alias.getSpecifics().put("ALIAS_TYPE", type);
			assertEquals("CREATE ALIAS APP.A FOR " + type + " TARGET.BASE", sql(alias.clone(), SqlType.CREATE));
			assertEquals("DROP ALIAS APP.A FOR " + type, sql(alias, SqlType.DROP));
		}
	}
}
