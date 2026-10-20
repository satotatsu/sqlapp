/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.sqlite;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import com.sqlapp.data.db.command.SqlExecuteCommand;

/** Host SQLite file integration; no Docker container or external database. */
class SqliteScriptExecutionTest {
	@TempDir Path directory;

	private SQLiteDataSource dataSource() {
		var source = new SQLiteDataSource();
		source.setUrl("jdbc:sqlite:" + directory.resolve("data.sqlite"));
		return source;
	}

	private void run(SQLiteDataSource source, String sql) throws Exception {
		Path script = directory.resolve("schema.sql");
		Files.writeString(script,sql,java.nio.charset.StandardCharsets.UTF_8);
		var command = new SqlExecuteCommand();
		command.setDataSource(source);
		command.setFiles(List.of(script.toFile()));
		command.run();
	}

	@Test
	void fileCommandExecutesTheWholeTriggerAndTheFollowingStatement() throws Exception {
		var source = dataSource();
		run(source,"CREATE TABLE t(id INT);CREATE TABLE audit(value TEXT);"
				+ "create trigger tr after insert on t begin "
				+ "insert into audit values(case when NEW.id=1 then 'one;END' else 'other' end);"
				+ "insert into audit values('last');end;INSERT INTO t VALUES(1);");
		try (var connection = source.getConnection(); var statement = connection.createStatement();
				var result = statement.executeQuery("SELECT group_concat(value,'|') FROM audit")) {
			assertTrue(result.next()); assertEquals("one;END|last",result.getString(1));
		}
	}

	@Test
	void malformedTriggerIsRejectedBeforeEarlierFileStatementsExecute() throws Exception {
		var source = dataSource();
		assertThrows(RuntimeException.class,() -> run(source,
				"CREATE TABLE must_not_exist(id INT);CREATE TRIGGER tr AFTER INSERT ON must_not_exist BEGIN SELECT 1;"));
		try (var connection = source.getConnection(); var statement = connection.createStatement();
				var result = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='must_not_exist'")) {
			assertTrue(result.next()); assertEquals(0,result.getInt(1));
		}
	}
}
