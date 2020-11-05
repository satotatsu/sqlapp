    SET NOCOUNT ON;
    SELECT v.Name AS Vendor, p.Name AS 'Product name', 
      v.CreditRating AS 'Rating', 
      v.ActiveFlag AS Availability
    FROM Purchasing.Vendor v 
    INNER JOIN Purchasing.ProductVendor pv
      ON v.BusinessEntityID = pv.BusinessEntityID 
    INNER JOIN Production.Product p
      ON pv.ProductID = p.ProductID 
    ORDER BY v.Name ASC