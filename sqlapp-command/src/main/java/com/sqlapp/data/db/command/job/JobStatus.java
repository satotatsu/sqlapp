package com.sqlapp.data.db.command.job;

import java.time.LocalDateTime;

public class JobStatus {
	private String jobName;
	private String rootTable;
	private String rootKey;
	private long rootSequence;
	private LocalDateTime updatedAt;
}
