/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.normalization;

/**
 * Generation mechanism for surrogate primary-key values.
 */
public enum SurrogateKeyGenerationType {
	IDENTITY,
	SEQUENCE
}
