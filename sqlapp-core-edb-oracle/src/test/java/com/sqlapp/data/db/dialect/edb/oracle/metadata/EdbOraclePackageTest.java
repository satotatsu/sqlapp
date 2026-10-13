/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.SchemaUtils;

class EdbOraclePackageTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> api,java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[] {api},handler);
	}
	private Connection connection(int count,List<String> queries,List<Object> bindings,boolean catalog) {
		return proxy(Connection.class,(p,m,a) -> {
			if (!m.getName().equals("prepareStatement")) throw new AssertionError(m.getName());
			String sql=(String)a[0];queries.add(sql);
			return proxy(PreparedStatement.class,(s,sm,sa) -> {
				if(sm.getName().equals("close")) return null;
				if(sm.getName().equals("setObject")) {bindings.add(sa[1]);return null;}
				if(!sm.getName().equals("executeQuery")) throw new AssertionError(sm.getName());
				int[] row={0};
				return proxy(ResultSet.class,(r,rm,ra) -> switch(rm.getName()) {
					case "next" -> ++row[0] <= (sql.contains("pg_catalog.pg_class") ? catalog ? 1 : 0 : count);
					case "getBoolean" -> false;
					case "getString" -> ra[0] instanceof Integer ? "sys" : switch((String)ra[0]) {
						case "schema_name" -> "Other.Schema";
						case "name" -> "pkg"+row[0];
						case "source" -> "PACKAGE pkg"+row[0]+" IS\nPROCEDURE p;\nEND;";
						default -> throw new AssertionError(ra[0]);
					};
					case "close" -> null;
					default -> throw new AssertionError(rm.getName());
				});
			});
		});
	}
	@Test void batchesOneOrManyPackagesAndBindsFilters() {
		for(int count : new int[] {1,8}) {
			var queries=new ArrayList<String>();var bindings=new ArrayList<Object>();
			var context=new ParametersContext();context.put("schemaName",List.of("Other.Schema"));context.put("packageName",new String[]{"pkg1","pkg2"});
			var rows=EdbOraclePackageSource.read(connection(count,queries,bindings,true),context,false);
			assertEquals(count,rows.size());assertEquals(2,queries.size());
			assertEquals(List.of("PACKAGE","Other.Schema","pkg1","pkg2"),bindings);
			assertTrue(queries.get(1).contains("\"sys\".all_source"));
			assertTrue(queries.get(1).contains("ORDER BY line"));
			assertFalse(queries.get(1).contains("Other.Schema"));
		}
	}
	@Test void absentCompatibilityCatalogReturnsNoPackages() {
		var queries=new ArrayList<String>();
		assertTrue(EdbOraclePackageSource.read(connection(0,queries,new ArrayList<>(),false),new ParametersContext(),false).isEmpty());
		assertEquals(1,queries.size());
	}
	@Test void qualifiesDefinitionsAndRetainsXmlAcrossSupportedVersions() throws Exception {
		for(int major=14;major<=18;major++) {
			var dialect=DialectResolver.getInstance().getDialect("edb-oracle",major,0,null);
			assertInstanceOf(EdbOraclePackageReader.class,dialect.getCatalogReader().getSchemaReader().getPackageReader());
			assertInstanceOf(EdbOraclePackageBodyReader.class,dialect.getCatalogReader().getSchemaReader().getPackageBodyReader());
			for(boolean body : new boolean[]{false,true}) {
				String source="PACKAGE "+(body ? "BODY " : "")+"\"old.schema\".\"P\" AS\n"+(body ? "PROCEDURE p IS BEGIN NULL; END;\n" : "PROCEDURE p;\n")+"END;";
				String ddl=EdbOraclePackageSource.definition(dialect,new EdbOraclePackageSource.Source("Other.Schema","P",source),body);
				assertTrue(ddl.startsWith("CREATE OR REPLACE PACKAGE "+(body ? "BODY " : "")+"\"Other.Schema\".\"P\" AS"),ddl);
				if(body) {
					var object=new com.sqlapp.data.schemas.PackageBody("P");object.setSchemaName("Other.Schema");object.setDefinition(ddl);
					com.sqlapp.data.schemas.PackageBody restored=SchemaUtils.readXml(new java.io.StringReader(object.asXml()));
					assertTrue(dialect.createSqlFactoryRegistry().createSql(restored,SqlType.CREATE).getFirst().getSqlText().contains(ddl));
					assertTrue(dialect.createSqlFactoryRegistry().createSql(restored,SqlType.DROP).getFirst().getSqlText().contains(body ? "DROP PACKAGE BODY" : "DROP PACKAGE"));
				} else {
					var object=new com.sqlapp.data.schemas.Package("P");object.setSchemaName("Other.Schema");object.setDefinition(ddl);
					com.sqlapp.data.schemas.Package restored=SchemaUtils.readXml(new java.io.StringReader(object.asXml()));
					assertTrue(dialect.createSqlFactoryRegistry().createSql(restored,SqlType.CREATE).getFirst().getSqlText().contains(ddl));
					assertTrue(dialect.createSqlFactoryRegistry().createSql(restored,SqlType.DROP).getFirst().getSqlText().contains(body ? "DROP PACKAGE BODY" : "DROP PACKAGE"));
				}
			}
		}
	}
	@Test void rejectsIncompleteOrWrongKindSources() {
		var dialect=DialectResolver.getInstance().getDialect("edb-oracle",17,0,null);
		for(String source : new String[]{"","BEGIN NULL; END;","PACKAGE BODY p AS END;"}) {
			assertThrows(IllegalArgumentException.class,() -> EdbOraclePackageSource.definition(dialect,new EdbOraclePackageSource.Source("s","p",source),false));
		}
		assertThrows(IllegalArgumentException.class,() -> dialect.createSqlFactoryRegistry().createSql(new com.sqlapp.data.schemas.Package("p"),SqlType.CREATE));
	}
	@Test void propagatesCatalogFailuresInsteadOfReturningAnEmptyList() {
		var failure=new SQLException("connection failed","08006");
		Connection connection=proxy(Connection.class,(p,m,a) -> {throw failure;});
		var error=assertThrows(IllegalStateException.class,() -> EdbOraclePackageSource.read(connection,new ParametersContext(),false));
		assertSame(failure,error.getCause());
	}

}
