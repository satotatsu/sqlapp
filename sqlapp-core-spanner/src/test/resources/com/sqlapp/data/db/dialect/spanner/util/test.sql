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
  