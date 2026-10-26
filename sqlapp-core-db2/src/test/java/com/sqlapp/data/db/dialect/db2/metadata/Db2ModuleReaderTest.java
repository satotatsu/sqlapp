/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2ModuleReaderTest {
	@Test
	void twentyModulesUseTwoBulkQueriesIncludingMembers() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2ModuleReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				if (node.toString().contains("CONDITION_SQLSTATE")) {
					for (int i = 0; i < 20; i++)
						for (int j = 0; j < 3; j++)
							try {
								handler.handleResultSetNext(Db2MetadataParityTest.row(Map.of("OBJECTSCHEMA", "APP",
										"OBJECTMODULENAME", "M" + i, "OBJECTNAME", "C" + j, "OBJECTTYPE", "CONDITION",
										"PUBLISHED", "Y", "USERDEFINED", "Y", "CONDITION_SQLSTATE", "75001")));
							} catch (SQLException exception) {
								throw new IllegalStateException(exception);
							}
					return null;
				}
				assertTrue(node.toString().contains("SYSCAT.MODULEOBJECTS"));
				assertTrue(node.toString().contains("m.MODULETYPE IN ('M', 'P')"));
				assertTrue(node.toString().contains("packageName"));
				for (int i = 0; i < 20; i++) {
					try {
						handler.handleResultSetNext(
								Db2MetadataParityTest.row(Map.of("MODULESCHEMA", "APP", "MODULENAME", "M" + i,
										"MODULETYPE", "M", "DIALECT", "DB2 SQL PL", "MEMBER_COUNT", "3")));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var modules = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(20, modules.size());
		assertEquals(2, queries.get());
		assertEquals("MODULE", modules.getFirst().getSpecifics().get("DB2_OBJECT_TYPE"));
		assertEquals("3", modules.getFirst().getSpecifics().get("MEMBER_COUNT"));
		assertTrue(modules.getFirst().getDefinition().isEmpty());
		assertNotNull(DialectHolder.Db2_1110Dialect.getCatalogReader().getSchemaReader().getPackageReader());
		assertNull(DialectHolder.Db2_1050Dialect.getCatalogReader().getSchemaReader().getPackageReader());
	}

	@Test
	void modernRoutineQueriesExcludeModuleMembersAndLegacyQueriesRemainCompatible() {
		var modern = DialectHolder.Db2_1110Dialect;
		var legacy = DialectHolder.Db2_1050Dialect;
		var version = new ProductVersionInfo();
		assertTrue(new Db2FunctionReader(modern).getSqlNode(version).toString().contains("r.ROUTINEMODULEID IS NULL"));
		assertTrue(new Db2ProcedureReader(modern).getSqlNode(version).toString().contains("r.ROUTINEMODULEID IS NULL"));
		assertTrue(new Db2FunctionArgumentReader(modern).getSqlNode(version).toString()
				.contains("r.ROUTINEMODULEID IS NULL"));
		assertTrue(new Db2ProcedureArgumentReader(modern).getSqlNode(version).toString()
				.contains("r.ROUTINEMODULEID IS NULL"));
		assertFalse(new Db2FunctionReader(legacy).getSqlNode(version).toString().contains("ROUTINEMODULEID"));
		assertFalse(new Db2ProcedureReader(legacy).getSqlNode(version).toString().contains("ROUTINEMODULEID"));
		assertTrue(new Db2TypeReader(modern).getSqlNode(version).toString().contains("d.TYPEMODULEID IS NULL"));
		assertTrue(new Db2DomainReader(modern).getSqlNode(version).toString().contains("d.TYPEMODULEID IS NULL"));
		assertFalse(new Db2TypeReader(legacy).getSqlNode(version).toString().contains("TYPEMODULEID"));
		assertFalse(new Db2DomainReader(legacy).getSqlNode(version).toString().contains("TYPEMODULEID"));
	}
}
