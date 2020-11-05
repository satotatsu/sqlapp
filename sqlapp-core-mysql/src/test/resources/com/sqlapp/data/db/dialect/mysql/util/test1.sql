
CREATE TABLE employee (id INT, 
                       name VARCHAR(10), 
                       salary DECIMAL(9,2));

INSERT INTO employee VALUES (1, 'aaaa', 35000), 
                            (2, 'bbbb', 35000);

/*comment1*/                          
                            
CREATE TABLE former_employee (id INT, name VARCHAR(10));

/*!50500 SELECT 1 */

/*comment2
 * 
 * 
 */                          


delimiter //

CREATE PROCEDURE func1(p1 INT)
 BEGIN
  -- Find the current maximum ID;
   SET @x = 0;
   REPEAT SET @x = @x + 1; UNTIL @x > p1 END REPEAT;
 END//
 
 CREATE PROCEDURE func2(p1 INT)
 BEGIN
  -- Find the current maximum ID;
   SET @x = 0;
   REPEAT SET @x = @x + 1; UNTIL @x > p1 END REPEAT;
 END
 //

 DELIMITEr ;
 
 SELECT * FROM employee;
