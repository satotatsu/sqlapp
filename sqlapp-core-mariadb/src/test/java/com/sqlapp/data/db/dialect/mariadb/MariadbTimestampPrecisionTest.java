package com.sqlapp.data.db.dialect.mariadb;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
class MariadbTimestampPrecisionTest {
	@Test
	void preservesInheritedTimestampPrecision(){
		for(int[] v:new int[][]{{10,0},{10,5},{11,4},{11,8},{12,1}}){
			var dialect=DialectResolver.getInstance().getDialect("mariadb",v[0],v[1],0);
			for(int p=0;p<=6;p++){
				var column=new Column("created");
				dialect.setDbType("timestamp("+p+")",null,null,column);
				assertEquals(DataType.TIMESTAMPVERSION,column.getDataType());
				assertEquals((long)p,column.getLength());
				assertEquals("TIMESTAMP("+p+")",dialect.createSqlBuilder().addTypeDefinition(column).toString());
			}
		}
	}
	@Test
	void retainsNativeTimestampOnUpdateInTheSharedBuilder(){
		var dialect=DialectResolver.getInstance().getDialect("mariadb",10,5,0);
		var column=new Column("modified").setDataType(DataType.TIMESTAMPVERSION).setLength(6)
				.setOnUpdate("CURRENT_TIMESTAMP(6)");
		var sql=dialect.createSqlBuilder().definition(column,false).toString();
		assertTrue(sql.contains("ON UPDATE CURRENT_TIMESTAMP(6)"),sql);
	}

}
