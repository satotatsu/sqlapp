/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.bulk;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.vertica.VerticaIntervalJdbcTypeHandler;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.bulk.*;

@EnabledIfSystemProperty(named="sqlapp.interval.vertica.url",matches="jdbc:vertica://127[.]0[.]0[.]1:15433/VMart")
class VerticaJsonCopyServerTest {
	private Connection connection()throws SQLException{return DriverManager.getConnection(System.getProperty("sqlapp.interval.vertica.url"),"dbadmin","");}
	@ParameterizedTest @CsvSource({"false,1","false,2","false,3"})
	void nativeCopyPreservesNestedValuesAndCallerTransactions(boolean yearMonth,int dimensions)throws Exception {
		String name="i_json_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
		try(var connection=connection();var sql=connection.createStatement()) {
			var dialect=DialectResolver.getInstance().getDialect(connection);
			DataType type=yearMonth?DataType.INTERVAL_YEAR_TO_MONTH:DataType.INTERVAL_DAY_TO_SECOND;
			var handler=new VerticaIntervalJdbcTypeHandler(type);
			var column=new Column("c").setDataType(type).setLength(6).setArrayDimension(dimensions);
			String scalar=yearMonth?"INTERVAL YEAR TO MONTH":"INTERVAL DAY TO SECOND(6)";
			sql.execute("CREATE TABLE "+name+"(id INT,txt VARCHAR(1000),b VARBINARY(32),n NUMERIC(30,9),s "+scalar+",c "+handler.arrayType(column)+")");
			try {
				var table=new Table(name);table.setDialect(dialect);
				table.getColumns().add(new Column("id").setDataType(DataType.INT));table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
				table.getColumns().add(new Column("b").setDataType(DataType.VARBINARY));table.getColumns().add(new Column("n").setDataType(DataType.DECIMAL).setLength(30).setScale(9));
				table.getColumns().add(new Column("s").setDataType(type).setLength(6));table.getColumns().add(column);
				var largest=IntervalValue.parse(yearMonth?"P25769803775M":"PT9223372036854.775807S");
				var negative=IntervalValue.parse(yearMonth?"P-1M":"PT-0.000001S");
				Object[] nested={new Object[]{largest,null,negative,negative},new Object[0],null};
				Object[] value=dimensions==1?(Object[])nested[0]:dimensions==2?nested:new Object[]{nested,new Object[0],null};
				String text="日本語😀\"\\\n\t";
				for(int id=1;id<=3;id++) {
					var row=table.newRow();row.put("id",id);row.put("txt",id==2?"":text);row.put("b",new byte[]{0,(byte)255});row.put("n",new BigDecimal("123456789012345678901.123456789"));
					row.put("s",negative);row.put("c",id==1?value:id==2?new Object[0]:null);table.getRows().add(row);
				}
				var executor=new VerticaBulkInsertExecutor(dialect);assertTrue(executor.supportsCallerTransactionAtomicity());
				connection.setAutoCommit(false);sql.execute("INSERT INTO "+name+"(id) VALUES(99)");
				assertEquals(3,executor.execute(connection,table,BulkOption.defaults()));assertFalse(connection.getAutoCommit());
				connection.rollback();try(var rows=sql.executeQuery("SELECT COUNT(*) FROM "+name)){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
				connection.setAutoCommit(true);assertEquals(3,executor.execute(connection,table,BulkOption.defaults()));assertTrue(connection.getAutoCommit());
				try(var observer=connection();var query=observer.createStatement();var rows=query.executeQuery("SELECT txt,b,n,"+handler.readExpression("s",table.getColumns().get("s"))+","+handler.readExpression("c",column)+" FROM "+name+" ORDER BY id")) {
					for(int id=1;id<=3;id++) {
						assertTrue(rows.next());assertEquals(id==2?"":text,rows.getString(1));assertArrayEquals(new byte[]{0,(byte)255},rows.getBytes(2));assertEquals(new BigDecimal("123456789012345678901.123456789"),rows.getBigDecimal(3));
						assertEquals(negative,handler.getObject(rows,4,table.getColumns().get("s")));
						Object actual=handler.getObject(rows,5,column);if(id==3)assertNull(actual);else assertArrayEquals(id==1?value:new Object[0],(Object[])actual);
					}
				}
				var unsupported=table.clone();unsupported.setRowIteratorHandler(rows->{throw new AssertionError("JDBC rejection must not consume source rows");});
				if(dimensions>1)assertThrows(IllegalArgumentException.class,()->new JdbcBatchBulkInsertExecutor(dialect).execute(connection,unsupported,BulkOption.defaults()));
			} finally {if(!connection.getAutoCommit())connection.rollback();connection.setAutoCommit(true);sql.execute("DROP TABLE "+name+" CASCADE");}
		}
	}
	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> qualifiers() {
		return java.util.stream.Stream.of(DataType.INTERVAL_YEAR,DataType.INTERVAL_MONTH,DataType.INTERVAL_YEAR_TO_MONTH,DataType.INTERVAL_DAY,DataType.INTERVAL_HOUR,DataType.INTERVAL_MINUTE,DataType.INTERVAL_SECOND,DataType.INTERVAL_DAY_TO_HOUR,DataType.INTERVAL_DAY_TO_MINUTE,DataType.INTERVAL_DAY_TO_SECOND,DataType.INTERVAL_HOUR_TO_MINUTE,DataType.INTERVAL_HOUR_TO_SECOND,DataType.INTERVAL_MINUTE_TO_SECOND)
				.flatMap(type->java.util.stream.Stream.of(false,true)
						.filter(json->!json || !type.name().contains("YEAR") && type!=DataType.INTERVAL_MONTH)
						.map(json->org.junit.jupiter.params.provider.Arguments.of(type,json)));
	}
	@ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("qualifiers")
	void copyPreservesEveryQualifierAndBinary(DataType type,boolean json)throws Exception {
		String name="i_csv_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
		try(var connection=connection();var sql=connection.createStatement()) {
			var handler=new VerticaIntervalJdbcTypeHandler(type);var column=new Column("c").setDataType(type).setLength(6).setArrayDimension(json?2:0);
			sql.execute("CREATE TABLE "+name+"(id INT,b VARBINARY(32),c "+handler.arrayType(column)+")");
			try {
				var table=new Table(name);table.setDialect(DialectResolver.getInstance().getDialect(connection));
				table.getColumns().add(new Column("id").setDataType(DataType.INT));table.getColumns().add(new Column("b").setDataType(DataType.VARBINARY));table.getColumns().add(column);
				boolean months=type.name().contains("YEAR") || type==DataType.INTERVAL_MONTH;
				var scalar=IntervalValue.parse(months?"P-12M":"PT-86400S");
				Object value=json?new Object[]{new Object[]{scalar,null},new Object[0],null}:scalar;
				for(int id=1;id<=3;id++){var row=table.newRow();row.put("id",id);row.put("b",id==1?new byte[]{0,(byte)255}:id==2?new byte[0]:null);row.put("c",id==3?null:value);table.getRows().add(row);}
				var executor=new VerticaBulkInsertExecutor(table.getDialect());
				connection.setAutoCommit(false);assertEquals(3,executor.execute(connection,table,BulkOption.defaults()));connection.rollback();
				try(var rows=sql.executeQuery("SELECT COUNT(*) FROM "+name)){rows.next();assertEquals(0,rows.getInt(1));}
				connection.setAutoCommit(true);assertEquals(3,executor.execute(connection,table,BulkOption.defaults()));
				try(var rows=sql.executeQuery("SELECT b,"+handler.readExpression("c",column)+" FROM "+name+" ORDER BY id")) {
					for(int id=1;id<=3;id++){assertTrue(rows.next());assertArrayEquals(id==1?new byte[]{0,(byte)255}:id==2?new byte[0]:null,rows.getBytes(1));Object actual=handler.getObject(rows,2,column);if(json && id!=3)assertArrayEquals((Object[])value,(Object[])actual);else assertEquals(id==3?null:value,actual);}
				}
			} finally {if(!connection.getAutoCommit())connection.rollback();connection.setAutoCommit(true);sql.execute("DROP TABLE "+name+" CASCADE");}
		}
	}

	@ParameterizedTest @CsvSource({"false,false","true,false","false,true"})
	void streamingFailuresRollbackOwnedWorkAndLeaveConnectionUsable(boolean failOnClose,boolean callerTransaction)throws Exception {
		String name="i_json_io_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
		try(var connection=connection();var sql=connection.createStatement()) {
			sql.execute("CREATE TABLE "+name+"(id INT,txt VARCHAR(65000),c ARRAY[ARRAY[INTERVAL DAY TO SECOND(6)]])");
			try {
				var table=new Table(name);table.setDialect(DialectResolver.getInstance().getDialect(connection));
				table.getColumns().add(new Column("id").setDataType(DataType.INT));table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
				table.getColumns().add(new Column("c").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(6).setArrayDimension(2));
				var row=table.newRow();row.put("id",1);row.put("txt","x".repeat(60000));row.put("c",new Object[]{new Object[]{IntervalValue.parse("PT1S")}});
				class FailingRows implements Iterator<Row>,AutoCloseable {
					int count;boolean closed;
					@Override public boolean hasNext(){if(count>0 && !failOnClose)throw new IllegalArgumentException("fixture read failure");return count==0;}
					@Override public Row next(){count++;return row;}
					@Override public void close(){closed=true;if(failOnClose)throw new IllegalArgumentException("fixture close failure");}
				}
				var source=new FailingRows();table.setRowIteratorHandler(rows->source);
				if(callerTransaction){connection.setAutoCommit(false);sql.execute("INSERT INTO "+name+"(id) VALUES(99)");}
				assertThrows(Exception.class,()->new VerticaBulkInsertExecutor(table.getDialect()).execute(connection,table,BulkOption.defaults()));
				assertTrue(source.closed);assertEquals(!callerTransaction,connection.getAutoCommit());
				if(callerTransaction)connection.rollback();
				try(var rows=sql.executeQuery("SELECT COUNT(*) FROM "+name)){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
			} finally {if(!connection.getAutoCommit())connection.rollback();connection.setAutoCommit(true);sql.execute("DROP TABLE "+name+" CASCADE");}
		}
	}

	@Test void oversizedJsonArrayFailsWithoutNullSubstitutionOrPartialCommit()throws Exception {
		String name="i_json_fail_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
		try(var connection=connection();var sql=connection.createStatement()) {
			sql.execute("CREATE TABLE "+name+"(id INT,c ARRAY[ARRAY[INTERVAL DAY TO SECOND(6)]](64))");
			try {
				var table=new Table(name);table.setDialect(DialectResolver.getInstance().getDialect(connection));
				table.getColumns().add(new Column("id").setDataType(DataType.INT));table.getColumns().add(new Column("c").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(6).setArrayDimension(2));
				for(int id=1;id<=2;id++){var row=table.newRow();row.put("id",id);var values=new Object[id==1?1:100];Arrays.fill(values,IntervalValue.parse("PT1S"));row.put("c",new Object[]{values});table.getRows().add(row);}
				assertThrows(SQLException.class,()->new VerticaBulkInsertExecutor(table.getDialect()).execute(connection,table,BulkOption.defaults()));
				assertTrue(connection.getAutoCommit());try(var rows=sql.executeQuery("SELECT COUNT(*) FROM "+name)){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
			} finally {sql.execute("DROP TABLE "+name+" CASCADE");}
		}
	}
}
