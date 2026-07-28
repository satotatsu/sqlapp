package com.sqlapp.data.schemas.migration;

import java.time.LocalDateTime;
import java.util.Map;

public class CopyStatus {
	private String jobName;
	private String rootTableName;
	private Long rootSequence;
	private Status status;
	private LocalDateTime updatedAt;
	private Map<String, Object> lastRootKey;

	public static enum Status {
		CREATED, RENDING, RUNNING, COMPLETED, FAILED,
	}
}
