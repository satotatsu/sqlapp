/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Dedicated loopback fixture only; the Oracle JDBC driver must be on the test runtime. */
@EnabledIfSystemProperty(named="sqlapp.interval.oracle.url", matches="jdbc:oracle:thin:@//127[.]0[.]0[.]1:11521/FREEPDB1")
class TransferOracleIntervalCollectionServerTest {
	@TempDir Path directory;
	@ParameterizedTest
	@CsvSource({"CSV,false,false", "TSV,false,false", "JSONL,false,false", "CSV,true,false", "TSV,true,false", "JSONL,true,false",
			"CSV,false,true", "TSV,false,true", "JSONL,false,true", "CSV,true,true", "TSV,true,true", "JSONL,true,true"})
	void namedCollectionsRoundTripThroughFilesInBothDirections(String format, boolean nested, boolean yearMonth) throws Exception {
		String table="I_FC_"+UUID.randomUUID().toString().replace("-", "").substring(0,10).toUpperCase(Locale.ROOT);
		String type=table+"_TYPE";
		var from=h2();var restored=h2();DataSource oracle=new OracleFixture();
		try(var source=from.getConnection();var destination=oracle.getConnection();var check=restored.getConnection();
				var sql=source.createStatement();var target=destination.createStatement()) {
			String element=yearMonth?"INTERVAL YEAR(9) TO MONTH":"INTERVAL DAY(9) TO SECOND(9)";
			sql.execute("CREATE TABLE "+table+"(ID INT PRIMARY KEY,C "+element+" ARRAY)");
			String a=yearMonth?"INTERVAL '999999999-11' YEAR(9) TO MONTH":"INTERVAL '999999999 01:02:03.123456789' DAY(9) TO SECOND(9)";
			String b=yearMonth?"INTERVAL '-0-1' YEAR TO MONTH":"INTERVAL '-0 00:00:00.000000001' DAY TO SECOND(9)";
			sql.execute("INSERT INTO "+table+" VALUES(1,ARRAY["+a+",NULL,"+b+","+b+"]),(2,ARRAY[]),(3,NULL)");
			target.execute("CREATE TYPE "+type+" AS "+(nested?"TABLE":"VARRAY(8)")+" OF "+element);
			try {
				target.execute("CREATE TABLE "+table+"(ID NUMBER(10) PRIMARY KEY,C "+type+")"+(nested?" NESTED TABLE C STORE AS "+table+"_S":""));
				try {
					var dialect=com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(destination);
					var columns=dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
					columns.setSchemaName("INTERVAL_TEST");columns.setObjectName(table);
					assertEquals(List.of("ID","C"),columns.getAll(destination).stream().map(com.sqlapp.data.schemas.Column::getName).toList());
					var forward=transfer(job("forward",table,"PUBLIC","INTERVAL_TEST","REQUIRE_EXISTING",format),from,oracle);
					assertEquals(nested,forward.schemaPreparation().warnings().stream().anyMatch(warning->warning.contains("INTERVAL_COLLECTION_UNORDERED")));
					transfer(job("reverse",table,"INTERVAL_TEST","PUBLIC","CREATE_MISSING",format),oracle,restored);
					try(var rows=check.createStatement().executeQuery("SELECT COUNT(*) FROM "+table)) {assertTrue(rows.next());assertEquals(3,rows.getInt(1));}
				} finally {target.execute("DROP TABLE "+table+" PURGE");}
			} finally {target.execute("DROP TYPE "+type);}
		}
	}
	private JdbcDataSource h2() {var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID());source.setUser("sa");return source;}
	private Path job(String name,String table,String source,String target,String mode,String format)throws Exception {
		return Files.writeString(directory.resolve(name+".yaml"),"formatVersion: 1\nsource: from\ntarget: to\ntables: ["+table+"]\nsourceSchema: "+source+"\ntargetSchema: "+target+
				"\nschemaMode: "+mode+"\ndataMode: FILE\ninsertMethod: JDBC_BATCH\nfiles: {directory: "+name+"-package, format: "+format+(format.equals("JSONL")?"":", nullValue: __NULL__")+"}\n");
	}
	private TransferFileImportService.Review transfer(Path job,DataSource from,DataSource to)throws Exception {
		var saved=new TransferFileExportService().export(job,Map.of("from",from));
		var service=new TransferFileImportService();var review=service.review(saved.file(),saved.fingerprint(),Map.of("to",to));
		var outcome=service.execute(review,Map.of("to",to),directory);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE,outcome.phase());
		assertFalse(outcome.verification().isEmpty());assertTrue(outcome.verification().values().stream().allMatch(value->value.isMatch()));
		return review;
	}
	private record OracleFixture() implements DataSource {
		@Override public Connection getConnection()throws SQLException{return DriverManager.getConnection(System.getProperty("sqlapp.interval.oracle.url"),"interval_test","interval_test_2026");}
		@Override public Connection getConnection(String user,String password)throws SQLException{throw new SQLFeatureNotSupportedException();}
		@Override public java.io.PrintWriter getLogWriter(){return null;}
		@Override public void setLogWriter(java.io.PrintWriter value){}
		@Override public void setLoginTimeout(int value){}
		@Override public int getLoginTimeout(){return 0;}
		@Override public java.util.logging.Logger getParentLogger(){return java.util.logging.Logger.getGlobal();}
		@Override public boolean isWrapperFor(Class<?> type){return type.isInstance(this);}
		@Override public <T>T unwrap(Class<T> type)throws SQLException{if(type.isInstance(this))return type.cast(this);throw new SQLException("Not a wrapper");}
	}
}
