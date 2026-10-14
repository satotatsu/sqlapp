package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;

class PostgresSchemaObjectQueryTest {
	@Test
	void isolatesVersionSpecificColumnsAndFormatsEveryQuery() {
		for(int version:new int[]{10,11,12,13,14,15,16,17,18}) {
			var reader=new PostgresSchemaObjectReader(DialectResolver.getInstance().getDialect("postgres",version,0,null));
			String collation=reader.collationSql();
			assertEquals(version>=17,collation.contains("c.colllocale"));
			assertEquals(version>=15&&version<17,collation.contains("c.colliculocale"));
			assertEquals(version>=16,collation.contains("c.collicurules"));
			assertEquals(version>=12,collation.contains("c.collisdeterministic"));
			assertEquals(version>=15,reader.publicationSql().contains("pr.prattrs"));
			assertEquals(version>=18,reader.publicationSql().contains("p.pubgencols"));
			assertEquals(version>=14,reader.subscriptionSql().contains("s.substream"));
			assertEquals(version>=17,reader.subscriptionSql().contains("s.subfailover"));
			assertEquals(version>=13,reader.statisticsSql().contains("s.stxstattarget"));
			assertDoesNotThrow(reader::fdwSql);
			assertDoesNotThrow(reader::userMappingSql);
			assertDoesNotThrow(reader::foreignTableSql);
			assertDoesNotThrow(reader::policySql);
		}
	}
}
