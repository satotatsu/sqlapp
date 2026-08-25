/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.TableNameRowCollectionFilter;
import com.sqlapp.data.schemas.loader.SchemaFileLoaderResolver;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

import lombok.Getter;
import lombok.Setter;

/** Exports a database/schema file supported by a SchemaFileLoader to XML. */
@Getter
@Setter
public class ExportSchemaFileXmlCommand extends AbstractCommand {

	private File inputFile;
	private File outputFile;
	private String schemaName;
	private boolean dumpRows = true;
	private String[] includeRowDumpTables;
	private String[] excludeRowDumpTables;

	@Override
	protected void doRun() {
		execute(() -> {
			if (inputFile == null) {
				throw new IllegalArgumentException("inputFile is required");
			}
			if (outputFile == null) {
				throw new IllegalArgumentException("outputFile is required");
			}
			final Schema schema = schemaName == null
					? SchemaFileLoaderResolver.loadSchema(inputFile)
					: SchemaFileLoaderResolver.loadSchema(inputFile, schemaName);
			final TableNameRowCollectionFilter filter = new TableNameRowCollectionFilter();
			filter.setIncludes(includeRowDumpTables);
			filter.setExcludes(excludeRowDumpTables);
			final boolean filterRows = !CommonUtils.isEmpty(includeRowDumpTables)
					|| !CommonUtils.isEmpty(excludeRowDumpTables);
			schema.getTables().forEach(table -> {
				if (!dumpRows || (filterRows && !filter.test(table.getRows()))) {
					table.setRowIteratorHandler(null);
				}
			});
			FileUtils.createParentDirectory(outputFile.getAbsolutePath());
			try (Writer writer = new OutputStreamWriter(
					new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
				schema.writeXml(writer);
			}
		});
	}

	public void setIncludeRowDumpTables(final String... values) {
		this.includeRowDumpTables = values;
	}

	public void setExcludeRowDumpTables(final String... values) {
		this.excludeRowDumpTables = values;
	}
}
