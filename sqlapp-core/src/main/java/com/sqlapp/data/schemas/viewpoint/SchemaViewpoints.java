/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.schemas.viewpoint;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Portable viewpoint definitions over the shared Schema model.
 */
@Getter
@Setter
public class SchemaViewpoints {

	public static final String FORMAT = "sqlapp-schema-viewpoints";

	public static final int CURRENT_VERSION = 1;

	private String format = FORMAT;

	private int version = CURRENT_VERSION;

	private List<SchemaViewpoint> viewpoints = new ArrayList<>();
}
