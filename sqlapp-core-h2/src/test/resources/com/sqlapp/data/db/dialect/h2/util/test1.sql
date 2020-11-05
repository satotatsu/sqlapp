
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

CREATE ALIAS MY_SQRT FOR "java.lang.Math.sqrt";
CREATE ALIAS GET_SYSTEM_PROPERTY FOR "java.lang.System.getProperty";
CREATE ALIAS REVERSE AS $$ String reverse(String s) { return new StringBuilder(s).reverse().toString(); } $$;
CALL REVERSE('Test');
CREATE ALIAS tr AS $$@groovy.transform.CompileStatic
    static String tr(String str, String sourceSet, String replacementSet){
        return str.tr(sourceSet, replacementSet);
    }
$$
;
CALL GET_SYSTEM_PROPERTY('java.class.path');
CALL GET_SYSTEM_PROPERTY('com.acme.test', 'true');
