
CREATE TABLE employee (id INT, 
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

 ALTER SPECIFIC ROUTINE child_arr_one
   BEGIN ATOMIC
     DECLARE id_list INT ARRAY DEFAULT ARRAY[];
     for_loop:
     FOR SELECT id FROM ptree WHERE pid = p_pid DO
       SET id_list[CARDINALITY(id_list) + 1] = id;
       SET id_list = id_list || child_arr(id);
     END FOR for_loop;
     RETURN id_list;
   END
;

 CREATE PROCEDURE test_proc(INOUT val_p INT, IN lastname_p VARCHAR(20)) 
 MODIFIES SQL DATA
 BEGIN ATOMIC
   SET val_p = 0;
   for_label: FOR SELECT * FROM customer WHERE lastname = lastname_p DO
     IF  val_p > 0 THEN
       DELETE FROM customer WHERE customer.id = id;
     END IF;
     SET val_p = val_p + 1;
   END FOR for_label;
 END;
 
  CREATE FUNCTION zero_pad(x BIGINT, digits INT, maxsize INT)
   RETURNS CHAR VARYING(100)
   LANGUAGE JAVA DETERMINISTIC NO SQL
   EXTERNAL NAME 'CLASSPATH:org.hsqldb.lib.StringUtil.toZeroPaddedString';

CREATE AGGREGATE FUNCTION udavg(IN x INTEGER, IN flag BOOLEAN, INOUT addup BIGINT, INOUT counter INT)
   RETURNS INTEGER
   CONTAINS SQL
   BEGIN ATOMIC
     IF flag THEN
       RETURN addup / counter;
     ELSE
       SET counter = COALESCE(counter, 0) + 1;
       SET addup = COALESCE(addup, 0) + COALESCE(x, 0);
       RETURN NULL;
     END IF;
   END; 
