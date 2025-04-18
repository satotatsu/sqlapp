package com.sqlapp.data.db.command;

import java.sql.Connection;

import com.sqlapp.jdbc.ReleaseConnectionAndCloseDataSourceHandler;
import com.sqlapp.jdbc.ReleaseConnectionHandler;
import com.sqlapp.jdbc.ReleaseConnectionOnlyHandler;
import com.sqlapp.jdbc.function.SQLConsumer;

public final class CommandDefaultUtils {

	private static ReleaseConnectionHandler releaseConnectionAndCloseDataSourceHandler = new ReleaseConnectionAndCloseDataSourceHandler();

	private static ReleaseConnectionHandler releaseConnectionHandler = new ReleaseConnectionOnlyHandler();

	private static SQLConsumer<Connection> commitHandler = conn -> conn.commit();

	private static SQLConsumer<Connection> lastCommitHandler = conn -> conn.commit();

	private static SQLConsumer<Connection> rollbackHandler = conn -> conn.rollback();

	/**
	 * @return the releaseConnectionAndCloseDataSourceHandler
	 */
	public static ReleaseConnectionHandler getReleaseConnectionAndCloseDataSourceHandler() {
		return releaseConnectionAndCloseDataSourceHandler;
	}

	/**
	 * @param releaseConnectionAndCloseDataSourceHandler the
	 *                                                   releaseConnectionAndCloseDataSourceHandler
	 *                                                   to set
	 */
	public static void setReleaseConnectionAndCloseDataSourceHandler(
			ReleaseConnectionHandler releaseConnectionAndCloseDataSourceHandler) {
		CommandDefaultUtils.releaseConnectionAndCloseDataSourceHandler = releaseConnectionAndCloseDataSourceHandler;
	}

	/**
	 * @return the releaseConnectionHandler
	 */
	public static ReleaseConnectionHandler getReleaseConnectionHandler() {
		return releaseConnectionHandler;
	}

	/**
	 * @param releaseConnectionHandler the releaseConnectionHandler to set
	 */
	public static void setReleaseConnectionHandler(ReleaseConnectionHandler releaseConnectionHandler) {
		CommandDefaultUtils.releaseConnectionHandler = releaseConnectionHandler;
	}

	/**
	 * @return the commitHandler
	 */
	public static SQLConsumer<Connection> getCommitHandler() {
		return commitHandler;
	}

	/**
	 * @param commitHandler the commitHandler to set
	 */
	public static void setCommitHandler(SQLConsumer<Connection> commitHandler) {
		CommandDefaultUtils.commitHandler = commitHandler;
	}

	/**
	 * @return the lastCommitHandler
	 */
	public static SQLConsumer<Connection> getLastCommitHandler() {
		return lastCommitHandler;
	}

	/**
	 * @param lastCommitHandler the lastCommitHandler to set
	 */
	public static void setLastCommitHandler(SQLConsumer<Connection> lastCommitHandler) {
		CommandDefaultUtils.lastCommitHandler = lastCommitHandler;
	}

	/**
	 * @return the rollbackHandler
	 */
	public static SQLConsumer<Connection> getRollbackHandler() {
		return rollbackHandler;
	}

	/**
	 * @param rollbackHandler the rollbackHandler to set
	 */
	public static void setRollbackHandler(SQLConsumer<Connection> rollbackHandler) {
		CommandDefaultUtils.rollbackHandler = rollbackHandler;
	}

}
