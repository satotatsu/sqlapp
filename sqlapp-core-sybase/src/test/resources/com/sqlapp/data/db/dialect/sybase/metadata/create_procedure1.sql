CREATE PROCEDURE HumanResources.uspGetEmployees2 
    @LastName nvarchar(50) = N'D%', 
    @FirstName nvarchar(50) = N'%'
AS 
    SET NOCOUNT ON;
    SELECT FirstName, LastName, Department
    FROM HumanResources.vEmployeeDepartmentHistory
    WHERE FirstName LIKE @FirstName AND LastName LIKE @LastName;