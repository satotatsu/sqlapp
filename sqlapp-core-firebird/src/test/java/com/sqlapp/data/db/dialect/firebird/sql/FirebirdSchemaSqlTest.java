/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdSchemaSqlTest {
	@Test
	void recreatesModernTypesUsingTheResolvedVersionFourDialect() {
		var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("Firebird", 4, 0, 0);
		assertInstanceOf(com.sqlapp.data.db.dialect.firebird.Firebird40.class, dialect);
		int[] codes = { 24, 25, 26, 28, 29 };
		String[] types = { "DECFLOAT(16)", "DECFLOAT(34)", "INT128", "TIME WITH TIME ZONE",
				"TIMESTAMP WITH TIME ZONE" };
		for (int i = 0; i < codes.length; i++) {
			Table table = new Table("modern_types");
			Column column = new Column("value");
			com.sqlapp.data.db.dialect.firebird.metadata.FirebirdUtils.setDbType(column, codes[i], 0, 16, 0, 0, 0);
			table.getColumns().add(column);
			String text = sql(dialect, table);
			assertTrue(text.contains(types[i]), text);
		}
		Table numeric = new Table("amounts");
		numeric.getColumns().add(new Column("amount").setDataType(com.sqlapp.data.db.datatype.DataType.DECIMAL)
				.setDataTypeName("DECIMAL").setLength(38).setScale(4));
		assertTrue(sql(dialect, numeric).contains("DECIMAL(38,4)"), sql(dialect, numeric));
	}

	@Test
	void recreatesProcedureInputsAndOutputsAndFunctionReturnType() {
		Procedure procedure = new Procedure("lookup");
		procedure.getArguments()
				.add(new NamedArgument("input_id").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		procedure.getArguments()
				.add(new NamedArgument("result_id").setDataType(com.sqlapp.data.db.datatype.DataType.INT)
						.setDirection(com.sqlapp.jdbc.sql.ParameterDirection.Output));
		procedure.setStatement("BEGIN result_id = input_id; SUSPEND; END");
		String text = sql(DialectHolder.defaultDialect25, procedure);
		assertTrue(text.contains("RETURNS"), text);
		assertTrue(text.indexOf("RETURNS") > text.indexOf("input_id"), text);
		assertTrue(text.indexOf("RETURNS") < text.indexOf("result_id"), text);
		assertFalse(text.contains("OUT"), text);
		assertTrue(text.contains("AS\nBEGIN"), text);
		Function function = new Function("double_id");
		function.getArguments()
				.add(new NamedArgument("input_id").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		function.getReturning().setDataType(com.sqlapp.data.db.datatype.DataType.INT);
		function.setStatement("AS\nBEGIN RETURN input_id * 2; END");
		text = sql(DialectHolder.defaultDialect30, function);
		assertTrue(text.contains("RETURNS INTEGER"), text);
		assertFalse(text.contains("AS\nAS"), text);
		Function missingBody = new Function("missing");
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect30, missingBody));
	}

	private String sql(Dialect dialect, DbObject<?> object) {
		return dialect.createSqlFactoryRegistry().createSql(object, SqlType.CREATE).get(0).getSqlText();
	}

	@Test
	void rendersTriggerPositionAfterEventsAndOmitsTableForDatabaseTriggers() {
		Trigger trigger = new Trigger("audit");
		trigger.setTableName("orders");
		trigger.setActionTiming("BEFORE");
		trigger.addEventManipulation("INSERT", "UPDATE");
		trigger.setStatement("BEGIN NEW.id = 1; END");
		trigger.getSpecifics().put("POSITION", "7");
		String text = sql(DialectHolder.defaultDialect25, trigger);
		assertTrue(text.contains("POSITION 7"), text);
		assertFalse(text.contains("PARTITION"), text);
		assertTrue(text.indexOf("POSITION") > text.indexOf("UPDATE"), text);
		Trigger databaseTrigger = new Trigger("connect_audit");
		databaseTrigger.setEventManipulation(new String[] { "ON CONNECT" });
		databaseTrigger.setStatement("BEGIN END");
		text = sql(DialectHolder.defaultDialect25, databaseTrigger);
		assertFalse(text.contains("FOR"), text);
		assertTrue(text.contains("ON CONNECT"), text);
		trigger.getSpecifics().put("POSITION", "-1");
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect25, trigger));
	}

	@Test
	void rendersDescendingComputedAndPartialIndexes() {
		Table table = new Table("orders");
		Index index = new Index("ix_orders");
		table.getIndexes().add(index);
		index.setIndexType(IndexType.Function);
		index.getColumns().add("UPPER(customer)", Order.Desc);
		index.setWhere("customer IS NOT NULL");
		String text = sql(DialectHolder.defaultDialect50, index);
		assertTrue(text.contains("DESCENDING INDEX"), text);
		assertTrue(text.contains("COMPUTED BY (UPPER(customer))"), text);
		assertTrue(text.contains("WHERE customer IS NOT NULL"), text);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect30, index));
		index.setWhere(null);
		assertTrue(sql(DialectHolder.defaultDialect20, index).contains("COMPUTED BY"));
	}

	@Test
	void rejectsMixedDirectionsAndRestoresInactiveState() {
		Table table = new Table("orders");
		Index index = new Index("ix_orders");
		table.getIndexes().add(index);
		index.getColumns().add("a", Order.Desc);
		index.getColumns().add("b", Order.Asc);
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect50, index));
		index.getColumns().get(1).setOrder(Order.Desc);
		index.setEnable(false);
		var operations = DialectHolder.defaultDialect50.createSqlFactoryRegistry().createSql(index, SqlType.CREATE);
		assertEquals(2, operations.size());
		assertTrue(operations.get(1).getSqlText().contains("INACTIVE"));
	}
}
