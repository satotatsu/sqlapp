/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BulkUpsertTransactionTest {
	private final List<String> calls = new ArrayList<>();
	private boolean autoCommit = true;
	private boolean failStart;
	private boolean failRollback;
	private boolean failCommit;
	private boolean failReset;
	private boolean failCleanup;

	private Connection connection() {
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p,m,a)->switch(m.getName()){
			case "getAutoCommit" -> autoCommit;
			case "setAutoCommit" -> {
				calls.add("auto="+a[0]);
				if ((boolean)a[0] && failReset) throw new SQLException("reset failed");
				autoCommit=(boolean)a[0];
				if (!(boolean)a[0] && failStart) throw new SQLTransientException("begin failed", "08006", 21);
				yield null;
			}
			case "commit" -> { calls.add("commit"); if(failCommit) throw new SQLException("commit failed","08006",19); yield null; }
			case "rollback" -> { calls.add("rollback"); if(failRollback) throw new SQLException("rollback failed","08006",20); yield null; }
			case "createStatement" -> Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Statement.class},(q,n,b)->switch(n.getName()){
				case "execute" -> {calls.add("cleanup");if(failCleanup)throw new SQLException("cleanup failed");yield false;}
				case "close" -> null;
				default -> throw new AssertionError(n.getName());
			});
			default -> throw new AssertionError(m.getName());
		});
	}

	@Test void failedRollbackDoesNotRestoreAutocommit() throws Exception {
		failRollback=true; var failure=new SQLException("load failed");
		try(var tx=BulkUpsertTransaction.begin(connection(),true)){tx.rollback(failure);}
		assertFalse(autoCommit);assertFalse(calls.contains("auto=true"));
		assertEquals(1,failure.getSuppressed().length);
	}
	@Test void failedRollbackDoesNotExecuteCleanupSql() throws Exception {
		failRollback=true;var failure=new SQLException("insert failed");
		try(var scope=BulkUpsertExecutionScope.begin(connection(),true)){
			scope.addCleanupSql("DROP TABLE stage");scope.rollback(failure);
		}
		assertFalse(calls.contains("cleanup"));assertFalse(calls.contains("auto=true"));
	}
	@Test void completedCommitClosesExactlyOnce() throws Exception {
		var tx=BulkUpsertTransaction.begin(connection(),true);tx.commit();tx.close();tx.close();
		assertEquals(List.of("auto=false","commit","auto=true"),calls);
	}
	@Test void completedRollbackIsNotRepeatedAtClose() throws Exception {
		try(var tx=BulkUpsertTransaction.begin(connection(),true)){tx.rollback(new SQLException("operation failed"));}
		assertEquals(List.of("auto=false","rollback","auto=true"),calls);
	}
	@Test void callerTransactionIsNotCompleted() throws Exception {
		autoCommit=false;
		try(var tx=BulkUpsertTransaction.begin(connection(),true)){tx.commit();tx.rollback(new SQLException("failed"));}
		assertTrue(calls.isEmpty());
	}
	@Test void disabledTransactionIsNotCompleted() throws Exception {
		try(var tx=BulkUpsertTransaction.begin(connection(),false)){tx.commit();tx.rollback(new SQLException("failed"));}
		assertTrue(calls.isEmpty());assertTrue(autoCommit);
	}
	@Test void commitFailureReportsUncertaintyAndRetainsDriverDetails() throws Exception {
		failCommit=true;
		try(var tx=BulkUpsertTransaction.begin(connection(),true)){
			var failure=assertThrows(SQLException.class,tx::commit);
			assertTrue(failure.getMessage().toLowerCase().contains("unknown"),failure.getMessage());
			assertEquals("08006",failure.getSQLState());assertEquals(19,failure.getErrorCode());assertNotNull(failure.getCause());
			tx.rollback(failure);
		}
		assertEquals(List.of("auto=false","commit","rollback","auto=true"),calls);
	}
	@Test void cleanupFailureAfterCommitStillRestoresAutocommit() throws Exception {
		failCleanup=true;
		var failure=assertThrows(SQLException.class,()->{
			try(var scope=BulkUpsertExecutionScope.begin(connection(),true)){
				scope.addCleanupSql("DROP TABLE stage");scope.commit();
			}
		});
		assertTrue(failure.getMessage().contains("cleanup"));assertTrue(autoCommit);
		assertFalse(calls.contains("rollback"));
	}
	@Test void resetFailureAfterCommitDoesNotAttemptRollback() throws Exception {
		failReset=true;
		var tx=BulkUpsertTransaction.begin(connection(),true);tx.commit();
		var failure=assertThrows(SQLException.class,tx::close);
		assertTrue(failure.getMessage().toLowerCase().contains("committed"),failure.getMessage());
		assertFalse(calls.contains("rollback"));
	}
	@Test void rollbackFailureOnUnfinishedCloseIsSurfaced() throws Exception {
		failRollback=true;var tx=BulkUpsertTransaction.begin(connection(),true);
		assertThrows(SQLException.class,tx::close);assertFalse(calls.contains("auto=true"));
	}

	private Connection database() throws SQLException {
		var c=DriverManager.getConnection("jdbc:hsqldb:mem:tx_"+UUID.randomUUID().toString().replace("-",""),"SA","");
		try(var s=c.createStatement()){s.execute("CREATE TABLE target(id INT)");s.execute("CREATE TABLE stage(id INT)");}
		return c;
	}
	private void insert(Connection c) throws SQLException {try(var s=c.createStatement()){s.executeUpdate("INSERT INTO target VALUES(1)");}}
	private long count(Connection c) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM target")){r.next();return r.getLong(1);}}
	@Test void unfinishedTransactionCloseRollsBackInsteadOfCommitting() throws Exception {
		try(var c=database()){
			try(var tx=BulkUpsertTransaction.begin(c,true)){insert(c);}
			assertTrue(c.getAutoCommit());assertEquals(0,count(c));
		}
	}
	@Test void unfinishedScopeRollsBackBeforeCommittingDdlCleanup() throws Exception {
		try(var c=database()){
			try(var scope=BulkUpsertExecutionScope.begin(c,true)){
				insert(c);scope.addCleanupSql("DROP TABLE stage");
			}
			assertEquals(0,count(c));assertTrue(c.getAutoCommit());
		}
	}
	@Test void injectedRollbackFailureCannotCommitRealPendingRows() throws Exception {
		try(var c=database()){
			var wrapped=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
				if(m.getName().equals("rollback"))throw new SQLException("injected rollback failure","08006");
				try{return m.invoke(c,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
			});
			var failure=new SQLException("insert failed");
			try(var scope=BulkUpsertExecutionScope.begin(wrapped,true)){
				insert(c);scope.addCleanupSql("DROP TABLE stage");scope.rollback(failure);
			}
			assertFalse(c.getAutoCommit());
			c.rollback();c.setAutoCommit(true);assertEquals(0,count(c));
			try(var s=c.createStatement()){s.execute("DROP TABLE stage");}
		}
	}
	@Test void commitAndRollbackFailuresDoNotExecuteFurtherSql() throws Exception {
		failCommit=true;failRollback=true;
		var scope=BulkUpsertExecutionScope.begin(connection(),true);scope.addCleanupSql("DROP TABLE stage");
		var failure=assertThrows(SQLException.class,scope::commit);scope.rollback(failure);scope.close();
		assertEquals(List.of("auto=false","commit","rollback"),calls);
		assertEquals(1,failure.getSuppressed().length);assertFalse(autoCommit);
	}
	@Test void closingCompletedScopeCannotCommitLaterCallerWork() throws Exception {
		try(var c=database()){
			var scope=BulkUpsertExecutionScope.begin(c,true);scope.commit();scope.close();
			c.setAutoCommit(false);insert(c);scope.close();
			assertFalse(c.getAutoCommit());c.rollback();c.setAutoCommit(true);assertEquals(0,count(c));
		}
	}
	@Test void failedCommitAcknowledgementDoesNotMeanRowsWereRolledBack() throws Exception {
		try(var c=database()){
			var wrapped=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
				if(m.getName().equals("commit")){c.commit();throw new SQLException("injected lost commit acknowledgement","08006",31);}
				try{return m.invoke(c,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
			});
			SQLException failure;
			try(var scope=BulkUpsertExecutionScope.begin(wrapped,true)){
				insert(c);failure=assertThrows(SQLException.class,scope::commit);scope.rollback(failure);
			}
			assertTrue(failure.getMessage().contains("unknown"));assertEquals(1,count(c));assertTrue(c.getAutoCommit());
		}
	}

	@Test void retryPolicyCannotRetryFailedRollbackEvenForTransientOriginalError() throws Exception {
		failRollback=true;var failure=new SQLTransientException("checkpoint failed","40001",41);
		try(var tx=BulkUpsertTransaction.begin(connection(),true)){tx.rollback(failure);}
		var retry=BulkMigrationRetryOption.builder().maxRetries(2).sqlState("40001").errorCode(41).build();
		assertFalse(retry.shouldRetry(failure,0));
	}
	@Test void retryPolicyCannotRetryUnknownCommitEvenForExplicitSqlState() throws Exception {
		failCommit=true;
		try(var tx=BulkUpsertTransaction.begin(connection(),true)){
			var failure=assertThrows(SQLException.class,tx::commit);tx.rollback(failure);
			var retry=BulkMigrationRetryOption.builder().maxRetries(2).sqlState("08006").errorCode(19).build();
			assertFalse(retry.shouldRetry(failure,0));
			var wrapped=new SQLTransientException("wrapped completion failure");wrapped.initCause(failure);
			assertFalse(retry.shouldRetry(wrapped,0));
			var chained=new SQLTransientException("chained completion failure");chained.setNextException(failure);
			assertFalse(retry.shouldRetry(chained,0));
		}
	}

	@Test void failedBeginWithUnknownConnectionModeCannotBeRetried() {
		failStart=true;
		var failure=assertThrows(SQLException.class,()->BulkUpsertTransaction.begin(connection(),true));
		var retry=BulkMigrationRetryOption.builder().maxRetries(2).sqlState("08006").build();
		assertFalse(retry.shouldRetry(failure,0));assertFalse(autoCommit);
		assertEquals(List.of("auto=false"),calls);
	}

}
