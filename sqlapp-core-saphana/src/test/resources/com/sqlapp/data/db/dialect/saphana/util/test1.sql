/*create table comment*/CREATE TABLE employee (id INT, 
                       name VARCHAR(10), 
                       salary DECIMAL(9,2));

INSERT INTO employee VALUES (1, 'aaaa', 35000), 
                            (2, 'bbbb', 35000);

/*comment1*/                          
                            
CREATE TABLE former_employee (id INT, name VARCHAR(10));

/*comment2
 * 
 * 
 */                          


CREATE FUNCTION FUNC1 RETURNS TABLE (COL1 INT, COL2 INT) AS
BEGIN
    RETURN SELECT * FROM TAB;
END
WITH CACHE RETENTION 10;

--comment3

CREATE FUNCTION FUNC2 RETURNS TABLE (COL1 INT, COL2 INT) AS
BEGIN
    RETURN SELECT * FROM TAB;
END
;

CREATE FUNCTION FUNC3 RETURNS TABLE (COL1 INT, COL2 INT) AS
BEGIN
    RETURN SELECT * FROM TAB;
END;

CREATE TRIGGER TEST_TRIGGER_WHILE_UPDATE
 AFTER UPDATE ON TARGET
 BEGIN
     DECLARE found INT := 1;
     DECLARE val INT := 1;
     WHILE :found <> 0 DO
         SELECT count(*) INTO found FROM sample WHERE a = :val;
         IF :found = 0 THEN
             INSERT INTO sample VALUES(:val);
         END IF;
         val := :val + 1;
     END WHILE;
 END;
 
 CREATE PROCEDURE orchestrationProc
 LANGUAGE SQLSCRIPT AS
 BEGIN
   DECLARE v_id BIGINT;
   DECLARE v_val BIGINT;
   DECLARE v_name VARCHAR(30);
   DECLARE  v_pmnt BIGINT;
   DECLARE v_msg VARCHAR(200);
   DECLARE CURSOR c_cursor1 (p_payment BIGINT) FOR
     SELECT id, name, payment FROM control_tab
       WHERE payment > :p_payment ORDER BY id ASC;
   CALL init_proc();
   OPEN c_cursor1(250000);
   FETCH c_cursor1 INTO v_id, v_name, v_pmnt; v_msg := :v_name || ' (id ' || :v_id || ') earns ' || :v_pmnt || ' $.';
   CALL ins_msg_proc(:v_msg);
   CLOSE c_cursor1;
 END 
 ;
 
CREATE REMOTE SOURCE HOSTA1 ADAPTER
   "hadoop" CONFIGURATION 'webhdfs.url=http://hostA:50070/;webhcat.url=http://hostA:50111'  WITH CREDENTIAL
   TYPE 'PASSWORD' USING 'user=dbuser;password=dbtest';
 
CREATE VIRTUAL FUNCTION word_count() RETURNS TABLE ( word
   NVARCHAR(60),  count INT) PACKAGE "SYSTEM"."WORD_COUNT" CONFIGURATION
   'sap.hana.hadoop.mapper=com.sap.hadoop.examples.WordCountMapper;
   sap.hana.hadoop.reducer=com.sap.hadoop.examples.WordCountReducer;sap.hana.hadoop.input=''/path/to/input'''
   AT HOSTA1;
  