/**
 * Copyright (C) 2026-2027 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core. If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.sql.SqlParser;

public class SqlParserTest {
	@Test
	public void test1() {
		Dialect dialect = DialectResolver.getInstance().getDefaultDialect();
		final String sql = """
				MERGE "tableA" AS _target_
				USING ( /*VALUES*/VALUES ( '', '', LOCALTIMESTAMP, LOCALTIMESTAMP, 0 )/*END*/ ) AS _source_ ( "colb", "colc", "created_at", "updated_at", "lock_version" )
				ON (
					_target_."cola" = _source_."cola"
				)
				WHEN MATCHED
					THEN UPDATE
						SET _target_."colb" = _source_."colb"
						, _target_."colc" = _source_."colc"
						, _target_."updated_at" =/*update_updated_at*/
						, _target_."lock_version" = _source_."lock_version"
				WHEN NOT MATCHED
					THEN INSERT
					(
						"colb"
						, "colc"
						, "created_at"
						, "updated_at"
						, "lock_version"
					)
					VALUES
					(
						_source_."colb"
						, _source_."colc"
						, COALESCE(/*insert_created_at*/, CURRENT_TIMESTAMP )
						, _source_."updated_at"
						, _source_."lock_version"
					)
				""";
		Node node = SqlParser.getInstance().parse(dialect, sql);
		for (Node child : node.getChildNodes()) {
			System.out.println(child.getClass().getName() + ":" + child);
		}
		final String expected = """
				MERGE "tableA" AS _target_
				USING ( /*VALUES*/VALUES  ) AS _source_ ( "colb", "colc", "created_at", "updated_at", "lock_version" )
				ON (
					_target_."cola" = _source_."cola"
				)
				WHEN MATCHED
					THEN UPDATE
						SET _target_."colb" = _source_."colb"
						, _target_."colc" = _source_."colc"
						, _target_."updated_at" =/*update_updated_at*/
						, _target_."lock_version" = _source_."lock_version"
				WHEN NOT MATCHED
					THEN INSERT
					(
						"colb"
						, "colc"
						, "created_at"
						, "updated_at"
						, "lock_version"
					)
					VALUES
					(
						_source_."colb"
						, _source_."colc"
						, COALESCE(/*insert_created_at*/, CURRENT_TIMESTAMP )
						, _source_."updated_at"
						, _source_."lock_version"
					)
				""";
		assertEquals(expected, node.toString());
	}
}
