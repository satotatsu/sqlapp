/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

class FirebirdRoutineArgumentsTest {
	private FirebirdProcedureArgumentReader reader(List<List<String>> batches) {
		return new FirebirdProcedureArgumentReader(DialectHolder.defaultDialect25) {
			@Override public List<NamedArgument> getAllFull(Connection connection, ParametersContext context) {
				@SuppressWarnings("unchecked") List<String> names = (List<String>) context.get("routineName");
				batches.add(List.copyOf(names)); List<NamedArgument> result = new ArrayList<>();
				for (String name : names) {
					Procedure procedure = new Procedure(name);
					procedure.getArguments().add(new NamedArgument("A")); procedure.getArguments().add(new NamedArgument("B"));
					result.addAll(procedure.getArguments());
				}
				return result;
			}
		};
	}
	@Test void batches1201RoutinesAndRetainsNamesArgumentOrderAndContext() {
		List<List<String>> batches = new ArrayList<>(); List<Procedure> routines = new ArrayList<>();
		for (int i=0; i<1201; i++) routines.add(new Procedure("quoted ' \" routine " + i));
		ParametersContext context = new ParametersContext(); context.put("routineName", "original");
		var result = FirebirdRoutineArguments.read(null, context, routines, reader(batches));
		assertEquals(List.of(500,500,201), batches.stream().map(List::size).toList());
		assertEquals("original", context.get("routineName")); assertEquals(1201, result.size());
		assertEquals(List.of("A","B"), result.get(routines.get(1200).getName()).stream().map(NamedArgument::getName).toList());
	}
	@Test void emptySelectionIssuesNoArgumentQuery() {
		List<List<String>> batches = new ArrayList<>();
		assertTrue(FirebirdRoutineArguments.read(null, new ParametersContext(), List.<Procedure>of(), reader(batches)).isEmpty());
		assertTrue(batches.isEmpty());
	}
	@Test void procedureReaderAttachesOnlySelectedArgumentsInBulk() throws Exception {
		List<List<String>> batches = new ArrayList<>();
		var reader = new FirebirdProcedureReader(DialectHolder.defaultDialect25) {
			@Override protected com.sqlapp.data.db.metadata.RoutineArgumentReader<?> newRoutineArgumentReader() { return FirebirdRoutineArgumentsTest.this.reader(batches); }
		};
		List<Procedure> selected = List.of(new Procedure("p one"), new Procedure("p two"));
		reader.setMetadataDetail(null, new ParametersContext(), selected);
		assertEquals(List.of(List.of("p one", "p two")), batches);
		for (var procedure : selected) assertEquals(List.of("A","B"), procedure.getArguments().stream().map(NamedArgument::getName).toList());
	}
	@Test void allVersionQueriesApplyBoundRoutineNamePredicates() {
		var legacy = new FirebirdProcedureArgumentReader(DialectHolder.defaultDialect20);
		List<com.sqlapp.jdbc.sql.node.SqlNode> nodes = new ArrayList<>();
		for (int[] version : new int[][] { {1,5}, {2,0}, {2,1}, {2,5} }) nodes.add(legacy.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(version[0]).setMinorVersion(version[1])));
		nodes.add(((FirebirdProcedureArgumentReader)new Firebird30ProcedureReader(DialectHolder.defaultDialect30).newRoutineArgumentReader()).getSqlSqlNode(new ProductVersionInfo()));
		nodes.add(new FirebirdFunctionArgumentReader(DialectHolder.defaultDialect25).getSqlSqlNode(new ProductVersionInfo()));
		nodes.add(new Firebird30FunctionArgumentReader(DialectHolder.defaultDialect30).getSqlSqlNode(new ProductVersionInfo()));
		for (var node : nodes) {
			ParametersContext context = new ParametersContext(); context.put("routineName", List.of("one ' name", "two name"));
			SqlParameterCollection sql = new SqlParameterCollection(); node.eval(context, sql);
			assertTrue(sql.getSql().contains(" IN "), sql.getSql()); assertEquals(2, sql.getSql().chars().filter(c -> c=='?').count(), sql.getSql());
			assertFalse(sql.getSql().contains("one ' name"));
		}
	}
	@Test void packagePredicateIsSelectedOnlyForActualVersionThreeAndLater() {
		var reader = new FirebirdProcedureArgumentReader(DialectHolder.defaultDialect30);
		for (int major : new int[] { 2, 3, 4, 5 }) {
			SqlParameterCollection sql = new SqlParameterCollection();
			reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(major).setMinorVersion(5)).eval(new ParametersContext(), sql);
			assertEquals(major >= 3, sql.getSql().contains("pp.RDB$PACKAGE_NAME IS NULL"), sql.getSql());
		}
	}
}
