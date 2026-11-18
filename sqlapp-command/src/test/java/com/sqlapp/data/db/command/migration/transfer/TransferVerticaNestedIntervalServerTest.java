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

@EnabledIfSystemProperty(named="sqlapp.interval.vertica.url",matches="jdbc:vertica://127[.]0[.]0[.]1:15433/VMart")
class TransferVerticaNestedIntervalServerTest {
	@TempDir Path directory;
	@ParameterizedTest
	@CsvSource({"CSV,1,AUTO,false,false","CSV,2,AUTO,false,false","TSV,3,AUTO,false,false","JSONL,2,NATIVE,false,false","DIRECT,2,AUTO,false,false",
			"CSV,2,JDBC_BATCH,false,false","CSV,2,AUTO,true,false","CSV,2,AUTO,false,true"})
	void nestedIntervalsUseReviewedNativeTransport(String format,int dimensions,String method,boolean fullScan,boolean excessPrecision)throws Exception {
		var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID());source.setUser("sa");DataSource target=new Fixture();
		String table="i_nested_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
		try(var from=source.getConnection();var to=target.getConnection();var sql=from.createStatement();var destination=to.createStatement()) {
			sql.execute("CREATE TABLE \""+table+"\"(ID INT"+(fullScan?"":" PRIMARY KEY")+",C INTERVAL DAY(9) TO SECOND(9)"+" ARRAY".repeat(dimensions)+")");
			String value="ARRAY[ARRAY[INTERVAL '0 00:00:00."+(excessPrecision?"000000001":"123456000")+"' DAY(9) TO SECOND(9),NULL,INTERVAL '-1' SECOND],ARRAY[],NULL]";
			if(dimensions==1)value="ARRAY[INTERVAL '0 00:00:00.123456' DAY TO SECOND,NULL,INTERVAL '-1' SECOND]";
			if(dimensions==3)value="ARRAY["+value+",ARRAY[],NULL]";
			sql.execute("INSERT INTO \""+table+"\" VALUES(1,"+value+"),(2,ARRAY[]),(3,NULL)");
			if(fullScan)sql.execute("INSERT INTO \""+table+"\" SELECT * FROM \""+table+"\" WHERE ID=1");
			Path job=Files.writeString(directory.resolve("job.yaml"),"formatVersion: 1\nsource: from\ntarget: to\ntables: ["+table+"]\nsourceSchema: PUBLIC\ntargetSchema: public\nschemaMode: CREATE_MISSING\ninsertMethod: "+method+
					"\ndataMode: "+(format.equals("DIRECT")?"JDBC_DIRECT":"FILE")+"\n"+(format.equals("DIRECT")?"":"files: {directory: package, format: "+format+(format.equals("JSONL")?"":", nullValue: __NULL__")+"}\n"));
			try {
				if(format.equals("DIRECT")) {
					var service=new TransferJobExecutionService();var bindings=Map.of("from",source,"to",target);var review=service.review(job,bindings,directory);
					assertTrue(review.prepared().warnings().stream().anyMatch(warning->warning.contains("INTERVAL_NATIVE_BINDING")));
					var result=service.execute(review,bindings);assertEquals(TransferJobExecutionService.Phase.COMPLETE,result.phase());assertTrue(result.verificationResult().isMatch());
				} else {
					var saved=new TransferFileExportService().export(job,Map.of("from",source));var service=new TransferFileImportService();
					if(method.equals("JDBC_BATCH") || excessPrecision) {
						var failure=assertThrows(IllegalArgumentException.class,()->service.review(saved.file(),saved.fingerprint(),Map.of("to",target)));
						assertTrue(failure.getMessage().contains(excessPrecision?"INTERVAL_VALUE_NOT_REPRESENTABLE":"NATIVE"));
						try(var rows=to.getMetaData().getTables(to.getCatalog(),"public",table,new String[]{"TABLE"})){assertFalse(rows.next());}return;
					}
					var review=service.review(saved.file(),saved.fingerprint(),Map.of("to",target));if(dimensions>1)assertTrue(review.schemaPreparation().warnings().stream().anyMatch(warning->warning.contains("INTERVAL_NATIVE_BINDING")));
					var result=service.execute(review,Map.of("to",target),directory);assertEquals(TransferJobExecutionService.Phase.COMPLETE,result.phase());assertTrue(result.verification().values().stream().allMatch(valueResult->valueResult.isMatch()));
					if(dimensions>1)assertTrue(Files.readString(result.auditFile()).contains("INTERVAL_NATIVE_BINDING"));
				}
				try(var rows=destination.executeQuery("SELECT COUNT(*) FROM \""+table+"\"")){assertTrue(rows.next());assertEquals(fullScan?4:3,rows.getInt(1));}
			} finally {try(var rows=to.getMetaData().getTables(to.getCatalog(),"public",table,new String[]{"TABLE"})){if(rows.next())destination.execute("DROP TABLE \""+table+"\" CASCADE");}}
		}
	}
	private record Fixture()implements DataSource {
		@Override public Connection getConnection()throws SQLException{return DriverManager.getConnection(System.getProperty("sqlapp.interval.vertica.url"),"dbadmin","");}
		@Override public Connection getConnection(String user,String password)throws SQLException{throw new SQLFeatureNotSupportedException();}
		@Override public java.io.PrintWriter getLogWriter(){return null;} @Override public void setLogWriter(java.io.PrintWriter value){}
		@Override public void setLoginTimeout(int value){} @Override public int getLoginTimeout(){return 0;}
		@Override public java.util.logging.Logger getParentLogger(){return java.util.logging.Logger.getGlobal();}
		@Override public boolean isWrapperFor(Class<?> type){return type.isInstance(this);}
		@Override public <T>T unwrap(Class<T> type)throws SQLException{if(type.isInstance(this))return type.cast(this);throw new SQLException("Not a wrapper");}
	}
}
