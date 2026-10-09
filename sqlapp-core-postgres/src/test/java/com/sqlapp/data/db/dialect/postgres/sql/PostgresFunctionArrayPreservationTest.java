package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.SchemaUtils;

class PostgresFunctionArrayPreservationTest {
	@Test
	void generatesSchemaQualifiedFunctionWithArrayArgumentsAndReturns() throws Exception {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, major == 8 ? 3 : 0, null);
			Function function = new Function("f").setSchemaName("Other Schema");
			function.getArguments().add(new NamedArgument("items").setDataType(DataType.UUID).setArrayDimension(1));
			function.getReturning().setDataType(DataType.UUID).setArrayDimension(2);
			function.setLanguage("sql").setStatement("SELECT ARRAY[items]");
			Function restored = SchemaUtils.readXml(new java.io.StringReader(function.asXml()));
			var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
			String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("\"Other Schema\".f"), sql);
			assertTrue(sql.contains("items UUID[]"), sql);
			assertTrue(sql.contains("RETURNS UUID[][]"), sql);
		}
	}
}
