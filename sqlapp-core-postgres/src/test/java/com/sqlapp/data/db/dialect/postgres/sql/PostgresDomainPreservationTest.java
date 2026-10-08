package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.SchemaUtils;

class PostgresDomainPreservationTest {
	@Test
	void preservesQualifiedQuotedBaseTypeAndExactlyOneArraySuffixPerDimension() throws Exception {
		String typeName = "\"Other.Schema\".\"Type \"\"Name\"\"\"";
		for (int major : new int[] { 8, 11, 15, 18 }) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, major == 8 ? 3 : 0, null);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			for (int dimension : new int[] { 0, 1, 2 }) {
				Domain domain = new Domain("Domain Name").setSchemaName("Target Schema");
				dialect.setDbType(typeName, null, null, domain);
				domain.setArrayDimension(dimension);
				domain.setRemarks("日本語 O'Brien");
				Domain restored = SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
				var operations = registry.createSql(restored, SqlType.CREATE);
				assertEquals(2, operations.size());
				assertTrue(operations.get(0).getSqlText().endsWith("AS " + typeName + "[]".repeat(dimension)),
						operations.get(0).getSqlText());
				assertEquals(SqlType.SET_COMMENT, operations.get(1).getSqlType());
				assertTrue(operations.get(1).getSqlText().contains("O''Brien"));
			}
		}
	}
}
