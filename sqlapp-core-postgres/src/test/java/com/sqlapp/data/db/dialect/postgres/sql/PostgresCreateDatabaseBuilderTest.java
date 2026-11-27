package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.Catalog;

class PostgresCreateDatabaseBuilderTest {
	@Test
	void defaultsToServerStrategyAndUsesCatalogNameAndEncoding() {
		var builder = new PostgresCreateDatabaseBuilder(DialectHolder.postgreSQL180);
		assertEquals("CREATE DATABASE app", builder.build(new Catalog("app")));
		assertEquals("CREATE DATABASE \"App Data\" TEMPLATE = template0 ENCODING = 'UTF8' STRATEGY = FILE_COPY", builder
				.template("template0").strategy("file_copy").build(new Catalog("App Data").setCharacterSet("UTF8")));
	}

	@Test
	void checksRealStrategyBoundaryAndRejectsInvalidInput() {
		assertEquals("CREATE DATABASE app STRATEGY = WAL_LOG",
				new PostgresCreateDatabaseBuilder(DialectHolder.postgreSQL150).strategy("wal_log")
						.build(new Catalog("app")));
		var old = new PostgresCreateDatabaseBuilder(DialectHolder.postgreSQL140);
		assertEquals("CREATE DATABASE app", old.build(new Catalog("app")));
		assertThrows(IllegalArgumentException.class, () -> old.strategy("FILE_COPY"));
		var builder = new PostgresCreateDatabaseBuilder(DialectHolder.postgreSQL180);
		assertThrows(IllegalArgumentException.class, () -> builder.strategy("FILE_COPY; DROP DATABASE app"));
		assertThrows(IllegalArgumentException.class, () -> builder.template(" "));
		assertThrows(IllegalArgumentException.class, () -> builder.build(new Catalog("")));
		assertThrows(IllegalArgumentException.class, () -> builder.build(new Catalog("bad\0name")));
	}
}
