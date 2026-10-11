/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.work.DisableCachingByDefault;

/** Exports a SQLite DB/SQLite/SQLite3 file as sqlapp Schema XML. */
@DisableCachingByDefault(because = "Command execution has not been validated for build caching")
public abstract class ExportSqliteSchemaXmlTask extends AbstractExportSchemaFileXmlTask {
}
