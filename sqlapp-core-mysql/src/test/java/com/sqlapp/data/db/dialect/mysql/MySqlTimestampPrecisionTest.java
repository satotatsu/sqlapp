package com.sqlapp.data.db.dialect.mysql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class MySqlTimestampPrecisionTest {
	@Test
	void preservesTimestampTypeAndFractionalPrecision() {
		for (int[] version : new int[][] {{5,6,4},{5,6,5},{5,7,0},{8,0,0},{8,0,1},{8,4,0},{9,0,0}}) {
			for (int precision = 0; precision <= 6; precision++) {
				var dialect = DialectResolver.getInstance().getDialect("mysql",version[0],version[1],version[2]);
				var column = new Column("created");
				assertTrue(dialect.setDbType("timestamp("+precision+")",null,null,column));
				assertEquals(DataType.TIMESTAMPVERSION,column.getDataType());
				assertEquals((long)precision,column.getLength());
				assertEquals("TIMESTAMP("+precision+")",dialect.createSqlBuilder().addTypeDefinition(column).toString());
				var value=column.getConverter().convertObject("2026-11-18 12:34:56.123456");
				assertEquals(Timestamp.class,value.getClass());
				assertEquals(123456000,((Timestamp)value).getNanos());
			}
		}
	}
	@Test
	void olderMysqlDoesNotClaimFractionalSupport() {
		var dialect=DialectResolver.getInstance().getDialect("mysql",5,6,3);
		var column=new Column("created");
		dialect.setDbType("timestamp(6)",null,null,column);
		assertEquals(DataType.OTHER,column.getDataType());
		assertTrue(dialect.setDbType("timestamp",null,null,column));
		assertEquals(DataType.TIMESTAMPVERSION,column.getDataType());
		assertNull(column.getLength());
		assertEquals("TIMESTAMP",dialect.createSqlBuilder().addTypeDefinition(column).toString());
	}
	@Test
	void numericTinyintOptionDoesNotChangeTimestampSemantics() {
		var dialect=DialectResolver.getInstance().getDialect("mysql",8,4,0,Map.of("mysql.tinyInt1Mapping","NUMERIC"));
		var column=new Column("created");
		assertTrue(dialect.setDbType("timestamp(6)",null,null,column));
		assertEquals(DataType.TIMESTAMPVERSION,column.getDataType());
		assertEquals(6L,column.getLength());
	}
	@Test
	void doesNotSilentlyClampInvalidDeclaredPrecision() {
		var dialect=DialectResolver.getInstance().getDialect("mysql",8,4,0);
		for(String declaration:new String[]{"timestamp(7)","timestamp(-1)","timestamp(9)"}){
			var column=new Column("created");
			dialect.setDbType(declaration,null,null,column);
			assertEquals(DataType.OTHER,column.getDataType());
		}
	}

	@Test
	void nullableTimestampDdlDoesNotDependOnImplicitServerDefaults(){
		var dialect=DialectResolver.getInstance().getDialect("mysql",5,7,0);
		var column=new Column("created").setDataType(DataType.TIMESTAMPVERSION).setLength(6).setNullable(true);
		var sql=dialect.createSqlBuilder().definition(column,false).toString();
		assertTrue(sql.contains("TIMESTAMP(6) NULL"),sql);
		column.setNullable(false);
		assertTrue(dialect.createSqlBuilder().definition(column,false).toString().contains("TIMESTAMP(6) NOT NULL"));
	}

	@Test
	void olderNativeTimestampRetainsOnUpdate(){
		var dialect=DialectResolver.getInstance().getDialect("mysql",5,1,0);
		var column=new Column("modified").setDataType(DataType.TIMESTAMPVERSION).setOnUpdate("CURRENT_TIMESTAMP");
		assertTrue(dialect.createSqlBuilder().definition(column,false).toString().contains("ON UPDATE CURRENT_TIMESTAMP"));
	}

}
