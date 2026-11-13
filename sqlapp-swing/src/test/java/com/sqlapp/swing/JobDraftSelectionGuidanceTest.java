/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.swing.JobDraftSelectionGuidance.Purpose;
import com.sqlapp.swing.JobDraftSelectionGuidance.State;

class JobDraftSelectionGuidanceTest {
	private static State state(final int visible, final int selected, final int hidden, final long eligible,
			final boolean fileExport, final boolean schemaOnly, final boolean unavailableOnly,
			final boolean selectedOnly, final Purpose purpose) {
		return new State(true, visible, 5, selected, hidden, eligible, 5, fileExport, schemaOnly, unavailableOnly,
				selectedOnly, purpose);
	}

	@Test
	void countsIncludeEligibilityOnlyAfterSchemaIsRead() {
		final var unread = new State(false, 0, 0, 0, 0, 0, 0, false, false, false, false, Purpose.EDITOR_DRAFT);
		assertEquals("0 / 0 tables; 0 selected (0 hidden)", JobDraftSelectionGuidance.count(unread));
		assertEquals("Read Schema to see the tables available for selection.", JobDraftSelectionGuidance.help(unread));
		assertEquals("3 / 5 tables; 2 selected (1 hidden); data-copy eligible: 4; unavailable: 1",
				JobDraftSelectionGuidance.count(state(3, 2, 1, 4, false, false, false, false, Purpose.EDITOR_DRAFT)));
		assertEquals("3 / 5 tables; 2 selected (1 hidden); export eligible: 4; unavailable: 1",
				JobDraftSelectionGuidance.count(state(3, 2, 1, 4, true, false, false, false, Purpose.FILE_JOB)));
	}

	@Test
	void helpFollowsTheFirstApplicableCondition() {
		assertTrue(JobDraftSelectionGuidance.help(new State(true, 0, 0, 0, 0, 0, 0, false, false, false, false,
				Purpose.EDITOR_DRAFT)).startsWith("No tables in this Schema"));
		assertTrue(help(state(5, 0, 0, 0, false, false, false, false, Purpose.EDITOR_DRAFT)).startsWith("No eligible tables"));
		assertTrue(help(state(5, 0, 0, 0, true, false, false, false, Purpose.FILE_JOB)).startsWith("No exportable tables"));
		assertTrue(help(state(5, 0, 0, 0, false, true, false, false, Purpose.EDITOR_DRAFT))
				.startsWith("Tick Include for each table to copy"), "schema only ignores eligibility");
		assertTrue(help(state(2, 1, 0, 3, false, false, true, false, Purpose.EDITOR_DRAFT))
				.startsWith("Review Availability for each restriction"));
		assertTrue(help(state(0, 0, 0, 3, false, false, false, true, Purpose.EDITOR_DRAFT)).startsWith("No tables selected"));
		assertTrue(help(state(0, 0, 0, 3, false, false, false, false, Purpose.EDITOR_DRAFT))
				.equals("No tables match. Clear the filter to see available tables."));
		assertTrue(help(state(0, 2, 2, 3, false, false, false, false, Purpose.EDITOR_DRAFT))
				.contains("Selected tables are hidden and will still be included"));
		assertTrue(help(state(3, 0, 0, 3, true, false, false, false, Purpose.FILE_JOB))
				.startsWith("Tick Include for each table to export"));
		assertTrue(help(state(3, 2, 1, 3, false, false, false, false, Purpose.EDITOR_DRAFT))
				.startsWith("Hidden selections will also be included"));
	}

	@Test
	void readySelectionNamesTheNextStepForEachPurpose() {
		assertTrue(help(state(3, 2, 0, 3, false, false, false, false, Purpose.TABLE_SELECTION))
				.contains("Use selected tables"));
		assertTrue(help(state(3, 2, 0, 3, true, false, false, false, Purpose.FILE_JOB))
				.contains("Save the shared transfer job"));
		assertTrue(help(state(3, 2, 0, 3, false, false, false, false, Purpose.EDITOR_DRAFT))
				.contains("Create a draft"));
		assertTrue(help(state(3, 2, 0, 3, false, false, false, false, Purpose.SAVED_MIGRATION))
				.contains("Save and review plan"));
	}

	private static String help(final State state) {
		return JobDraftSelectionGuidance.help(state);
	}
}
