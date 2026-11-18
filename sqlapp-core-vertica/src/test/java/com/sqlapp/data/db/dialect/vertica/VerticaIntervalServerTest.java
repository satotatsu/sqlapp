/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

@EnabledIfSystemProperty(named="sqlapp.interval.vertica.url", matches="jdbc:vertica://127[.]0[.]0[.]1:15433/VMart")
class VerticaIntervalServerTest {
	@Test void serverRejectsNestedArrayParametersBeforeAnyInsert() throws Exception {
		try (var connection=DriverManager.getConnection(System.getProperty("sqlapp.interval.vertica.url"),"dbadmin","");
				var sql=connection.createStatement()) {
			sql.execute("CREATE LOCAL TEMP TABLE i_nested_parameter(id INT,c ARRAY[ARRAY[INTERVAL DAY TO SECOND(6)]]) ON COMMIT PRESERVE ROWS");
			for (String parameter : new String[]{"?", "CAST(? AS VARCHAR(65000))"}) {
				var failure=assertThrows(SQLException.class,()-> {
					try (var insert=connection.prepareStatement("INSERT INTO i_nested_parameter VALUES(1,CAST("+parameter+" AS ARRAY[ARRAY[INTERVAL DAY TO SECOND(6)]]))")) {
						insert.setString(1,"[[\"0 01:00:00.000001\",null],[],null]");insert.executeUpdate();
					}
				});
				assertEquals("42846",failure.getSQLState());
			}
			try (var rows=sql.executeQuery("SELECT COUNT(*) FROM i_nested_parameter")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
		}
	}

	@Test void arraysPreserveNestedShapeNullsMicrosecondsAndLiteralValues() throws Exception {
		try (var connection=DriverManager.getConnection(System.getProperty("sqlapp.interval.vertica.url"),"dbadmin","");var sql=connection.createStatement()) {
			var handler=new VerticaIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND);
			for(int dimensions:new int[]{1}) {
				System.out.println("Vertica array dimensions="+dimensions);
				var column=new Column("c").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(6).setArrayDimension(dimensions);
				Object[] leaf={IntervalValue.parse("PT86400.000001S"),null,IntervalValue.parse("PT-3600S"),IntervalValue.ZERO};
				Object[] input=dimensions==1?leaf:new Object[]{leaf,new Object[0],null};
				assertFalse(handler.supportsJdbcBinding(column.clone().setArrayDimension(2)));
				String table="i_array_"+dimensions;
				sql.execute("CREATE LOCAL TEMP TABLE "+table+"(id INT,c "+"ARRAY[".repeat(dimensions)+"INTERVAL DAY TO SECOND(6)"+"]".repeat(dimensions)+") ON COMMIT PRESERVE ROWS");
				var dialect=com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
				var reader=dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
				reader.setSchemaName("v_temp_schema");reader.setObjectName(table);
				var captured=reader.getAll(connection).stream().filter(item->item.getName().equals("c")).findFirst().orElseThrow();
				assertEquals(DataType.INTERVAL_DAY_TO_SECOND,captured.getDataType(),captured.asXml());assertEquals(dimensions,captured.getArrayDimension());assertEquals(6L,captured.getLength());
				var recreated=new com.sqlapp.data.schemas.Table(table+"_copy");recreated.getColumns().add(captured.clone());
				for(var operation:dialect.createSqlFactoryRegistry().createSql(recreated,com.sqlapp.data.db.sql.SqlType.CREATE))sql.execute(operation.getSqlText());
				sql.execute("DROP TABLE "+table+"_copy CASCADE");
				var inputTable=new com.sqlapp.data.schemas.Table(table);inputTable.getColumns().add(new Column("id").setDataType(DataType.INT));inputTable.getColumns().add(column.clone());
				var registry=dialect.createSqlFactoryRegistry();registry.getTableOptions().setParameterExpression((item,defaultValue)->"?");
				String insertSql=registry.createSqlNodes(inputTable,com.sqlapp.data.db.sql.SqlType.INSERT).getFirst().eval(inputTable.newRow()).getSql();
				try(var insert=connection.prepareStatement(insertSql)) {
					insert.setInt(1,1);handler.setObject(insert,2,input,column);insert.addBatch();
					insert.setInt(1,2);handler.setObject(insert,2,null,column);insert.addBatch();insert.executeBatch();
				}
				sql.execute("INSERT INTO "+table+" VALUES(3,"+handler.literal(input,column)+")");
				try(var rows=sql.executeQuery("SELECT "+handler.readExpression("c",column)+" FROM "+table+" ORDER BY id")) {
					assertTrue(rows.next());assertArrayEquals(input,(Object[])handler.getObject(rows,1,column));
					assertTrue(rows.next());assertNull(handler.getObject(rows,1,column));
					assertTrue(rows.next());assertArrayEquals(input,(Object[])handler.getObject(rows,1,column));
				}
			}
		}
	}

	@Test void everyQualifierUsesNativeDriverBindingsWithoutLosingFractions() throws Exception {
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.vertica.url"), "dbadmin", "");
				var sql = connection.createStatement()) {
			for (DataType type : DataType.values()) {
				if (type.getMetaTtype()!=MetaType.INTERVAL || type==DataType.INTERVAL || type==DataType.INTERVAL_YEAR_TO_DAY) continue;
				System.out.println("Vertica qualifier " + type);
				String qualifier = type.name().substring(9).replace('_',' ');
				String table = "i_s9_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
				var column = new Column("c").setDataType(type).setLength(qualifier.endsWith("SECOND")?6:0);
				var handler = new VerticaIntervalJdbcTypeHandler(type);
				var value = qualifier.contains("YEAR") || qualifier.equals("MONTH")
						? new IntervalValue(-1200,0,Duration.ZERO) : new IntervalValue(0,0,Duration.ofDays(-100));
				sql.execute("CREATE TABLE "+table+"(id INT,c INTERVAL "+qualifier+(qualifier.endsWith("SECOND")?"(6)":"")+")");
				try {
					try (var insert = connection.prepareStatement("INSERT INTO "+table+" VALUES(?,?)")) {
						insert.setInt(1,1); handler.setObject(insert,2,value,column); insert.addBatch();
						insert.setInt(1,2); handler.setObject(insert,2,null,column); insert.addBatch(); insert.executeBatch();
					}
					try (var rows = sql.executeQuery("SELECT " + handler.readExpression("c", column) + " FROM "+table+" ORDER BY id")) {
						assertTrue(rows.next()); assertEquals(value,handler.getObject(rows,1,column),type.name());
						assertTrue(rows.next()); assertNull(handler.getObject(rows,1,column));
					}
					try (var rows = sql.executeQuery("SELECT "+handler.literal(value,column))) {
						assertTrue(rows.next()); assertEquals(value,handler.getObject(rows,1));
					}
				} finally { sql.execute("DROP TABLE "+table+" CASCADE"); }
			}
		}
	}
	@Test void nativeMicrosecondEndpointsRoundTrip() throws Exception {
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.vertica.url"), "dbadmin", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE LOCAL TEMP TABLE interval_bounds(c INTERVAL DAY TO SECOND(6)) ON COMMIT PRESERVE ROWS");
			var handler = new VerticaIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND);
			assertThrows(ArithmeticException.class, () -> handler.nativeValue(new IntervalValue(0,0,Duration.ofSeconds(-9223372036855L,224192000)), new Column().setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(6)));
			for (var time : new Duration[] {Duration.ofSeconds(9223372036854L,775807000),Duration.ofSeconds(-9223372036855L,224193000),Duration.ofNanos(-1000)}) {
				var value = new IntervalValue(0,0,time);
				sql.execute("DELETE FROM interval_bounds");
				try (var insert = connection.prepareStatement("INSERT INTO interval_bounds VALUES(?)")) {
					handler.setObject(insert,1,value); insert.executeUpdate();
				}
				try (var rows = sql.executeQuery("SELECT c FROM interval_bounds")) {
					assertTrue(rows.next()); assertEquals(value,handler.getObject(rows,1));
				}
			}
		}
	}
}
