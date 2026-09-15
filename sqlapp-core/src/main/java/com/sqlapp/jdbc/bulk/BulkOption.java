package com.sqlapp.jdbc.bulk;

import java.io.Serializable;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@ToString
@EqualsAndHashCode
@Getter
public class BulkOption implements Serializable {
	private static final long serialVersionUID = 1L;

	/** Returns options with vendor defaults. */
	public static BulkOption defaults() {
		return builder().build();
	}
	private final Integer batchSize;
	private final Integer bulkCopyTimeout;
	private final boolean checkConstraints;
	private final boolean fireTriggers;
	private final boolean keepIdentity;
	private final boolean keepNulls;
	private final boolean tableLock;
	private final boolean useTransaction;
	private final boolean allowEncryptedValueModifications;

	private BulkOption(final Integer batchSize, final Integer bulkCopyTimeout,
			final boolean checkConstraints, final boolean fireTriggers,
			final boolean keepIdentity, final boolean keepNulls, final boolean tableLock,
			final boolean useTransaction,
			final boolean allowEncryptedValueModifications) {
		if (batchSize != null && batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be greater than zero");
		}
		if (bulkCopyTimeout != null && bulkCopyTimeout < 0) {
			throw new IllegalArgumentException("bulkCopyTimeout must not be negative");
		}
		this.batchSize = batchSize;
		this.bulkCopyTimeout = bulkCopyTimeout;
		this.checkConstraints = checkConstraints;
		this.fireTriggers = fireTriggers;
		this.keepIdentity = keepIdentity;
		this.keepNulls = keepNulls;
		this.tableLock = tableLock;
		this.useTransaction = useTransaction;
		this.allowEncryptedValueModifications = allowEncryptedValueModifications;
	}
}
