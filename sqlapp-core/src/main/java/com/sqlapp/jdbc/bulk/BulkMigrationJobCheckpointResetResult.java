/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;

import lombok.Value;

/** Checkpoints successfully deleted from a validated migration job plan. */
@Value
public class BulkMigrationJobCheckpointResetResult {
	String planFingerprint;
	List<String> resetTaskIds;
}
