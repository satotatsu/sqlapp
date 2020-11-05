CREATE OR REPLACE PROCEDURE "Purchasing"."test_goto" (p1 INTEGER, out1 OUT VARCHAR(10))
AS
BEGIN
	<<LABEL2ABOVE>>
	IF p1 = 1 THEN
		out1 := out1 || 'one';
		GOTO LABEL1BELOW;
	END IF;
	if out1 IS NULL THEN
		out1 := out1 || 'two';
		GOTO LABEL2ABOVE;
	END IF;
	
	out1 := out1 || 'three';
	
	<<LABEL1BELOW>>
	out1 := out1 || 'four';
END