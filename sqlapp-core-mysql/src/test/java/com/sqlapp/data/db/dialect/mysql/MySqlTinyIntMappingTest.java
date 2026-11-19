package com.sqlapp.data.db.dialect.mysql;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class MySqlTinyIntMappingTest {
	private final DialectResolver resolver = DialectResolver.getInstance();
	private static final String OPTION = "mysql.tinyInt1Mapping";
	private Column column(Dialect d,String name){var c=new Column("v");assertTrue(d.setDbType(name,null,null,c));return c;}
	@Test
	void numericModePreservesVersionAndDefaultIsolation(){
		for(int[] v:new int[][]{{5,1,0},{5,6,4},{5,6,5},{5,7,0},{8,0,0},{8,0,1},{8,0,19},{8,4,0},{9,0,0}}){
			var normal=resolver.getDialect("mysql",v[0],v[1],v[2]);
			var numeric=resolver.getDialect("mysql",v[0],v[1],v[2],Map.of(OPTION,"NUMERIC"));
			assertEquals(normal.getClass(),numeric.getClass());assertNotSame(normal,numeric);
			assertNotSame(normal.getDbDataTypes(),numeric.getDbDataTypes());
			var next=numeric;
			for(int count=0;next!=null;count++){
				assertTrue(count<30,"next-version cycle");
				assertEquals("NUMERIC",((MySql)next).getTinyInt1Mapping().name());next=next.getNextVersionDialect();
			}
			assertEquals(DataType.TINYINT,column(numeric,"tinyint(1)").getDataType());
			assertEquals(DataType.TINYINT,column(numeric," TINYINT ( 1 ) ").getDataType());
			assertEquals(DataType.UTINYINT,column(numeric,"tinyint(1) unsigned").getDataType());
			assertEquals(DataType.BOOLEAN,column(numeric,"BOOLEAN").getDataType());
			assertEquals(DataType.BOOLEAN,column(normal,"tinyint(1)").getDataType());
			assertSame(normal,resolver.applyDialectOptions(numeric,Map.of(OPTION,"BOOLEAN")));
		}
	}
	@Test
	void numericHandlersBypassDriverBooleanConversion() throws Exception {
		var d=resolver.getDialect("mysql",8,4,0);
		for(DataType type:new DataType[]{DataType.TINYINT,DataType.UTINYINT}){
			var handler=d.getDbDataTypes().getDbType(type).getJdbcTypeHandler();
			for(int value:type==DataType.TINYINT?new int[]{-128,-1,0,1,2,127}:new int[]{0,1,2,127,255}){
				var rs=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()){
					case "getInt" -> value;case "wasNull" -> false;case "getObject" -> true;default -> throw new AssertionError(m.getName());});
				Object result=handler.getObject(rs,1);assertEquals(value,((Number)result).intValue());
				assertEquals(type.getDefaultClass(),result.getClass());
				assertEquals(result,handler.getObject(rs,"v"));
			}
			var nil=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()){
				case "getInt" -> 0;case "wasNull" -> true;default -> throw new AssertionError(m.getName());});
			assertNull(handler.getObject(nil,"v"));
		}
	}
	@Test
	void invalidOptionsFailClearly(){
		for(String value:new String[]{"", "TRUE","NUMERCI"})assertThrows(IllegalArgumentException.class,
			()->resolver.getDialect("mysql",8,4,0,Map.of(OPTION,value)));
		assertThrows(IllegalArgumentException.class,()->resolver.getDialect("mysql",8,4,0,Map.of("mysql.typo","NUMERIC")));
	}
	@Test
	void acceptsCaseInsensitiveOptionAndDisconnectedResolution(){
		var normal=resolver.getDialect("mysql",8,4,0);
		var numeric=resolver.applyDialectOptions(normal,Map.of("MYSQL.TINYINT1MAPPING"," numeric "));
		assertEquals(DataType.TINYINT,column(numeric,"tinyint(1)").getDataType());
		assertEquals(DataType.BOOLEAN,column(normal,"tinyint(1)").getDataType());
	}
	@Test
	void rejectsValuesOutsideTheModeledNumericRange() throws Exception {
		var d=resolver.getDialect("mysql",8,4,0);
		for(DataType type:new DataType[]{DataType.TINYINT,DataType.UTINYINT}){
			var rs=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()){
				case "getInt" -> type==DataType.TINYINT?128:-1;case "wasNull" -> false;default -> throw new AssertionError(m.getName());});
			var handler=d.getDbDataTypes().getDbType(type).getJdbcTypeHandler();
			assertThrows(java.sql.SQLDataException.class,()->handler.getObject(rs,1));
		}
	}

	@Test
	void differentMappingsHaveDifferentDialectIdentity(){
		var normal=resolver.getDialect("mysql",8,4,0);
		var numeric=resolver.applyDialectOptions(normal,Map.of(OPTION,"NUMERIC"));
		assertNotEquals(normal,numeric);
		assertEquals(2,new java.util.HashSet<>(java.util.List.of(normal,numeric)).size());
	}

}
