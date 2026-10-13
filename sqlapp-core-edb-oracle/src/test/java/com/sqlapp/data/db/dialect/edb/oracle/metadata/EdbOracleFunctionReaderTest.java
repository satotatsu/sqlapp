/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.edb.oracle.EdbOracle17;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class EdbOracleFunctionReaderTest {
	private static ExResultSet row(String name,String kind,String ddl) {
		return new ExResultSet(null) {
			@Override public String getString(String key) {
				return switch(key) {case "function_name" -> name;case "function_schema" -> "public";
				case "function_catalog" -> "edb";case "lanname" -> "edbspl";case "prokind" -> kind;
				case "function_identity_arguments" -> "n numeric";case "functiondef" -> ddl; default -> null;};
			}
		};
	}
	@Test void preservesExecutableSplDefinitionsAndXmlWithoutDuplicateArguments() throws Exception {
		var dialect=new EdbOracle17();
		var reader=new EdbOracleFunctionReader(dialect);
		reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		for (String kind : new String[] {"f","p"}) {
			String ddl="CREATE OR REPLACE " + (kind.equals("p") ? "PROCEDURE p(n numeric)" : "FUNCTION p(n numeric) RETURNS numeric") + " LANGUAGE edbspl AS $$ BEGIN " + (kind.equals("p") ? "NULL;" : "RETURN n;") + " END $$";
			var function=reader.createFunction(row("p",kind,ddl));
			assertTrue(function.getArguments().isEmpty());
			assertEquals("p(n numeric)",function.getSpecificName());
			Function restored=SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
			String sql=dialect.createSqlFactoryRegistry().createSql(restored,SqlType.CREATE).getFirst().getSqlText();
			assertTrue(sql.contains(ddl),sql);
			assertEquals(kind.equals("p") ? "PROCEDURE" : null,function.getSpecifics().get("ROUTINE_KIND"));
		}
	}
	@Test void failsWhenRequestedSplDefinitionIsUnavailable() {
		var reader=new EdbOracleFunctionReader(new EdbOracle17());
		reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		assertThrows(SQLException.class,() -> reader.createFunction(row("p","f",null)));
		reader.getReaderOptions().setReadStatement(false);
		reader.getReaderOptions().setReadDefinition(false);
		assertDoesNotThrow(() -> reader.createFunction(row("p","f",null)));
	}
	@Test void batchesSplRoutinesWithoutTypeLookupQueries() {
		var queries=new AtomicInteger();
		var reader=new EdbOracleFunctionReader(new EdbOracle17()) {
			java.util.List<Function> read() { return doGetAll(null,new ParametersContext(),new ProductVersionInfo().setMajorVersion(17)); }
			@Override protected JdbcQueryHandler execute(Connection c,SqlNode sql,ParametersContext context,ResultSetNextHandler handler) {
				queries.incrementAndGet();
				try { for(int i=0;i<8;i++) handler.handleResultSetNext(row("p"+i,"f","CREATE FUNCTION p"+i+"() RETURNS integer LANGUAGE edbspl AS $$BEGIN RETURN 1; END$$")); }
				catch(SQLException e) {throw new RuntimeException(e);}
				return null;
			}
		};
		reader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		assertEquals(8,reader.read().size());
		assertEquals(1,queries.get());
	}
}
