/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.swing.JdbcUrlBuilder.Database.*;
import org.junit.jupiter.api.Test;

class JdbcUrlBuilderTest {
	@Test
	void buildsCommonSingleServerUrlsWithoutCredentialsOrOptionalProperties() {
		assertEquals("jdbc:postgresql://db.example:5432/sales",
				JdbcUrlBuilder.build(POSTGRESQL, "db.example", "5432", "sales"));
		assertEquals("jdbc:mysql://db.example:3306/sales", JdbcUrlBuilder.build(MYSQL, "db.example", "3306", "sales"));
		assertEquals("jdbc:sqlserver://db.example:1433;databaseName={sales}",
				JdbcUrlBuilder.build(SQL_SERVER, "db.example", "1433", "sales"));
		assertEquals("jdbc:oracle:thin:@//db.example:1521/sales.example",
				JdbcUrlBuilder.build(ORACLE_SERVICE, "db.example", "1521", "sales.example"));
	}

	@Test
	void encodesDatabaseNamesWithoutInterpretingThemAsConnectionProperties() {
		for (var type : new JdbcUrlBuilder.Database[] { POSTGRESQL, MYSQL }) {
			assertEquals(type.prefix + "//db:1234/%20a%2Fb%3Fpassword%3Dx%26ssl%3Dfalse%23%2B%2A%20",
					JdbcUrlBuilder.build(type, "db", "1234", " a/b?password=x&ssl=false#+* "));
			assertTrue(JdbcUrlBuilder.build(type, "db", "1", "販売").endsWith("/%E8%B2%A9%E5%A3%B2"));
		}
		assertEquals("jdbc:sqlserver://db:1433;databaseName={ sales;encrypt=false }",
				JdbcUrlBuilder.build(SQL_SERVER, "db", "1433", " sales;encrypt=false "));
		assertThrows(IllegalArgumentException.class,
				() -> JdbcUrlBuilder.build(SQL_SERVER, "db", "1433", "sales};encrypt=false"));
		assertThrows(IllegalArgumentException.class,
				() -> JdbcUrlBuilder.build(ORACLE_SERVICE, "db", "1521", "sales?password=x"));
	}

	@Test
	void supportsLiteralIpv6AndInternationalHostNamesWithoutResolvingDns() {
		assertEquals("jdbc:postgresql://[::1]:5432/sales", JdbcUrlBuilder.build(POSTGRESQL, "::1", "5432", "sales"));
		assertEquals("jdbc:mysql://[2001:db8::1]:3306/sales",
				JdbcUrlBuilder.build(MYSQL, "[2001:db8::1]", "3306", "sales"));
		assertEquals("jdbc:sqlserver://;serverName=2001:db8::1;portNumber=1433;databaseName={sales}",
				JdbcUrlBuilder.build(SQL_SERVER, "2001:db8::1", "1433", "sales"));
		assertEquals("jdbc:mysql://xn--bcher-kva.example:3306/sales",
				JdbcUrlBuilder.build(MYSQL, "bücher.example", "3306", "sales"));
	}

	@Test
	void rejectsIncompleteOrAmbiguousInputsAndPropertyInjectionInHost() {
		assertThrows(IllegalArgumentException.class, () -> JdbcUrlBuilder.build(null, "db", "5432", "sales"));
		for (String host : new String[] { "", "db:5432", "jdbc:postgresql://db", "user@db", "db;encrypt=false",
				"db/path", "db?x=y", "[::not-an-ip]", "fe80::1%eth0" })
			assertThrows(IllegalArgumentException.class, () -> JdbcUrlBuilder.build(POSTGRESQL, host, "5432", "sales"),
					host);
		for (String port : new String[] { "", "0", "65536", "-1", "5432/sales", "5432;password=x", "999999999999" })
			assertThrows(IllegalArgumentException.class, () -> JdbcUrlBuilder.build(POSTGRESQL, "db", port, "sales"),
					port);
		for (String name : new String[] { "", " ", "sales\n", "a\u0000b", "\uD800" })
			assertThrows(IllegalArgumentException.class, () -> JdbcUrlBuilder.build(POSTGRESQL, "db", "5432", name));
	}
}
