package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.converter.StringConverter;

class DbDataTypeJdbcTypeHandlerTest {
	@Test
	void converterHandlersBindNullWithOverriddenJdbcType() throws Exception {
		List<Object> nullTypes = new ArrayList<>();
		PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
					if (method.getName().equals("setNull")) {
						nullTypes.add(args[1]);
					}
					return null;
				});
		VarcharType type = new VarcharType();
		type.setJdbcType(java.sql.JDBCType.NVARCHAR);

		type.setJdbcTypeHandler(new StringConverter());
		type.getJdbcTypeHandler().setObject(statement, 1, null);

		type.setJdbcTypeHandler(new StringConverter(), new StringConverter());
		type.getJdbcTypeHandler().setObject(statement, 2, null);

		assertEquals(List.of(Types.NVARCHAR, Types.NVARCHAR), nullTypes);
	}
}
