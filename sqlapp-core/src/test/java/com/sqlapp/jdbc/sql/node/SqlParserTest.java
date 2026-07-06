package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.jdbc.sql.SqlParser;

public class SqlParserTest {
	@Test
	public void test1() {
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
		Node node = SqlParser.getInstance().parse(sql);
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
