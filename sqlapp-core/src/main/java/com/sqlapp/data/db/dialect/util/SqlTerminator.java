package com.sqlapp.data.db.dialect.util;

import lombok.Getter;
import lombok.Setter;

/**
 * SQLの区切り文字を格納するIF
 */
@Getter
@Setter
public class SqlTerminator {
	/**
	 * SQL terminator START
	 */
	private String startStatementTerminator = null;
	/**
	 * SQL terminator END
	 */
	private String endStatementTerminator = null;
	/**
	 * SQL terminator
	 */
	private String terminator = null;
}
