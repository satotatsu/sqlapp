package com.sqlapp.jdbc.bulk;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
@Getter
public class BulkOption implements Serializable {
	private static final long serialVersionUID = 1L;

	/** Returns options with vendor defaults. */
	public static BulkOption defaults() {
		return builder().build();
	}
	private Integer batchSize;
	private Integer bulkCopyTimeout;
	private boolean checkConstraints;
	private boolean fireTriggers;
	private boolean keepIdentity;
	private boolean keepNulls;
	private boolean tableLock;
	private boolean useTransaction;
	private boolean allowEncryptedValueModifications;
}
