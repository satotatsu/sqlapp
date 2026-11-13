/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

/** Table count and next-step guidance shown under the {@link JobDraftPanel} table. */
final class JobDraftSelectionGuidance {
	/** What the selection is used for once it is ready. */
	enum Purpose {
		/** Choose tables for a saved transfer job. */
		TABLE_SELECTION,
		/** Save a file transfer job. */
		FILE_JOB,
		/** Create a draft in the job editor. */
		EDITOR_DRAFT,
		/** Save a new migration and review its plan. */
		SAVED_MIGRATION
	}

	/**
	 * @param schemaRead      a Schema has been read
	 * @param visibleRows     rows shown after filtering
	 * @param totalRows       all table rows
	 * @param selected        selected tables, including hidden ones
	 * @param hidden          selected tables hidden by the filter
	 * @param eligible        tables eligible for the current copy or export mode
	 * @param schemaTables    tables in the Schema
	 * @param fileExport      eligibility means export eligibility
	 * @param schemaOnly      schema only: every table can be selected
	 * @param unavailableOnly the "unavailable" filter is on
	 * @param selectedOnly    the "selected only" filter is on
	 */
	record State(boolean schemaRead, int visibleRows, int totalRows, int selected, int hidden, long eligible,
			int schemaTables, boolean fileExport, boolean schemaOnly, boolean unavailableOnly, boolean selectedOnly,
			Purpose purpose) {
	}

	private JobDraftSelectionGuidance() {
	}

	static String count(final State state) {
		final String text = state.visibleRows() + " / " + state.totalRows() + " tables; " + state.selected()
				+ " selected (" + state.hidden() + " hidden)";
		if (!state.schemaRead())
			return text;
		return text + (state.fileExport() ? "; export eligible: " : "; data-copy eligible: ") + state.eligible()
				+ "; unavailable: " + (state.schemaTables() - state.eligible());
	}

	static String help(final State state) {
		if (!state.schemaRead())
			return "Read Schema to see the tables available for selection.";
		if (state.totalRows() == 0)
			return "No tables in this Schema. Choose another Schema or source schema.";
		if (!state.schemaOnly() && state.eligible() == 0)
			return state.fileExport() ? "No exportable tables. Review Availability for supported file types."
					: "No eligible tables. Review Availability: primary keys and supported copy types are required.";
		if (state.unavailableOnly())
			return "Review Availability for each restriction. Existing selections remain included; this filter changes no copy rules.";
		if (state.selectedOnly() && state.selected() == 0)
			return "No tables selected. Turn off Selected tables only to choose tables.";
		if (state.visibleRows() == 0)
			return state.selected() == 0 ? "No tables match. Clear the filter to see available tables."
					: "No tables match. Selected tables are hidden and will still be included; clear the filter to review them.";
		if (state.selected() == 0)
			return state.fileExport() ? "Tick Include for each table to export; a primary key is not required."
					: "Tick Include for each table to copy, or use Select visible eligible.";
		if (state.hidden() > 0)
			return "Hidden selections will also be included. Clear the filter to review every selected table.";
		return switch (state.purpose()) {
		case TABLE_SELECTION -> "Selection ready. Use selected tables, then save job settings and review.";
		case FILE_JOB -> "Selection ready. Save the shared transfer job; saving does not move data.";
		case EDITOR_DRAFT -> "Selection ready. Create a draft to review the generated settings in the editor.";
		case SAVED_MIGRATION -> "Selection ready. Save and review plan checks the target; it does not copy rows.";
		};
	}
}
