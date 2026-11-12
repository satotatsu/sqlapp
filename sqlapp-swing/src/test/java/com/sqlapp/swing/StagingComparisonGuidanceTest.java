/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement.*;

class StagingComparisonGuidanceTest {
	@ParameterizedTest
	@CsvSource({ "true,true,All compared target rows match", "false,true,The target differs",
			"true,false,Staging data has changed", "false,false,Staging data has changed" })
	void interpretsTargetAndRecordedStagingIndependently(boolean matchesTarget, boolean matchesRecorded,
			String expected) {
		var comparison = new Comparison(List.of(table("matched", true, true),
				table("checked", matchesTarget, matchesRecorded)));
		assertTrue(StagingComparisonGuidance.describe(comparison).startsWith(expected));
		if (!matchesRecorded)
			assertTrue(StagingComparisonGuidance.describe(comparison).contains("does not establish a match"));
	}

	@Test
	void emptyComparisonDoesNotClaimAllTablesMatch() {
		assertTrue(StagingComparisonGuidance.describe(new Comparison(List.of())).startsWith("No tables were compared"));
	}

	private TableComparison table(String id, boolean matchesTarget, boolean matchesRecorded) {
		return new TableComparison(new Target(id, null, "PUBLIC", id, "STAGE_" + id), 1, 1,
				matchesTarget, matchesRecorded);
	}
}
