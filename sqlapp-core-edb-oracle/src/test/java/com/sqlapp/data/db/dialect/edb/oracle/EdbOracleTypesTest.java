/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;

class EdbOracleTypesTest {
	@Test void recognizesAliasesAcrossVersionsWithoutChangingPostgresDefaults() {
		for (int major=14;major<=18;major++) {
			var dialect=DialectResolver.getInstance().getDialect("edb-oracle",major,0,null);
			for (String type : new String[] {"VARCHAR2(80)","NVARCHAR2(80)"}) {
				var info=dialect.matchDbType(type,null,null).orElseThrow();
				assertEquals(DataType.VARCHAR,info.getDataType().orElseThrow());
				assertEquals(80L,info.getLength().orElseThrow());
			}
			var number=dialect.matchDbType("NUMBER(20,-2)",null,null).orElseThrow();
			assertEquals(DataType.NUMERIC,number.getDataType().orElseThrow());
			assertEquals(20L,number.getLength().orElseThrow());
			assertEquals(-2,number.getScale().orElseThrow());
			assertEquals(DataType.BLOB,dialect.matchDbType("RAW(16)",null,null).orElseThrow().getDataType().orElseThrow());
			assertEquals(DataType.DATE,dialect.matchDbType("DATE",null,null).orElseThrow().getDataType().orElseThrow());
			assertEquals(DataType.TIMESTAMP,dialect.matchDbType("TIMESTAMP(0)",null,null).orElseThrow().getDataType().orElseThrow());
			assertEquals(DataType.NUMERIC,dialect.matchDbType("NUMERIC(12,3)",null,null).orElseThrow().getDataType().orElseThrow());
		}
	}
}
