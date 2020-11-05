CREATE PROCEDURE dbo.uspProductByVendor @Name varchar(30) = '%'
WITH RECOMPILE
AS
    SET NOCOUNT ON;
    SELECT v.Name AS 'Vendor name', p.Name AS 'Product name'
    FROM Purchasing.Vendor AS v 
    JOIN Purchasing.ProductVendor AS pv 
      ON v.BusinessEntityID = pv.BusinessEntityID 
    JOIN Production.Product AS p 
      ON pv.ProductID = p.ProductID
    WHERE v.Name LIKE @Name;