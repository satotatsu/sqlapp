CREATE TABLE inventory_balances (
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (
        warehouse_id,
        product_id
    ),
    CONSTRAINT fk_inventory_balances_warehouse
    FOREIGN KEY(warehouse_id)
    REFERENCES warehouses(warehouse_id),
    CONSTRAINT fk_inventory_balances_products
    FOREIGN KEY(product_id)
    REFERENCES products(product_id)
);
