
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

CREATE OR REPLACE PROCEDURE check_func(limit NUMBER) AS
BEGIN
   IF limit=1 THEN
     DBMS_OUTPUT.PUT_LINE( 'Credit check OK');
   ELSE
     DBMS_OUTPUT.PUT_LINE( 'Credit check NG');
   END IF;
END;/

CREATE OR REPLACE PROCEDURE check_func2(limit NUMBER) AS
BEGIN
   IF limit=1 THEN
     DBMS_OUTPUT.PUT_LINE( 'Credit check OK');
   ELSE
     DBMS_OUTPUT.PUT_LINE( 'Credit check NG');
   END IF;
END;
/
