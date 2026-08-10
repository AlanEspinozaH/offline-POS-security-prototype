CREATE TABLE IF NOT EXISTS suppliers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL COLLATE NOCASE,
    tax_id TEXT,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS supplier_product (
    supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
    product_id TEXT NOT NULL,
    supplier_sku TEXT,
    units_per_pack INTEGER NOT NULL CHECK (units_per_pack > 0),
    presentation_name TEXT NOT NULL,
    last_pack_price_cents INTEGER CHECK (last_pack_price_cents IS NULL OR last_pack_price_cents >= 0),
    updated_at TEXT NOT NULL,
    PRIMARY KEY (supplier_id, product_id)
);

CREATE TABLE IF NOT EXISTS product_barcode_alias (
    barcode TEXT PRIMARY KEY,
    product_id TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS purchases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
    purchased_at TEXT NOT NULL,
    reference_number TEXT,
    notes TEXT,
    status TEXT NOT NULL CHECK (status IN ('CONFIRMED')),
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS purchase_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    purchase_id INTEGER NOT NULL REFERENCES purchases(id) ON DELETE RESTRICT,
    product_id TEXT NOT NULL,
    units_per_pack INTEGER NOT NULL CHECK (units_per_pack > 0),
    paid_packs INTEGER NOT NULL CHECK (paid_packs >= 0),
    bonus_packs INTEGER NOT NULL CHECK (bonus_packs >= 0),
    bonus_units INTEGER NOT NULL CHECK (bonus_units >= 0),
    pack_price_cents INTEGER NOT NULL CHECK (pack_price_cents >= 0),
    gross_amount_cents INTEGER NOT NULL CHECK (gross_amount_cents >= 0),
    discount_cents INTEGER NOT NULL CHECK (discount_cents >= 0),
    net_amount_cents INTEGER NOT NULL CHECK (net_amount_cents >= 0),
    total_received_units INTEGER NOT NULL CHECK (total_received_units > 0),
    CHECK (net_amount_cents = gross_amount_cents - discount_cents),
    CHECK (gross_amount_cents = paid_packs * pack_price_cents),
    CHECK (total_received_units = ((paid_packs + bonus_packs) * units_per_pack) + bonus_units)
);

CREATE TABLE IF NOT EXISTS product_cost_summary (
    product_id TEXT PRIMARY KEY,
    latest_purchase_line_id INTEGER NOT NULL REFERENCES purchase_lines(id),
    latest_net_amount_cents INTEGER NOT NULL,
    latest_received_units INTEGER NOT NULL,
    cumulative_net_amount_cents INTEGER NOT NULL,
    cumulative_received_units INTEGER NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (latest_received_units > 0),
    CHECK (cumulative_received_units > 0)
);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id TEXT NOT NULL,
    purchase_id INTEGER NOT NULL REFERENCES purchases(id),
    purchase_line_id INTEGER NOT NULL REFERENCES purchase_lines(id),
    quantity_units INTEGER NOT NULL CHECK (quantity_units > 0),
    movement_type TEXT NOT NULL CHECK (movement_type = 'PURCHASE_RECEIPT'),
    occurred_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sale_price_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id TEXT NOT NULL,
    price_cents INTEGER NOT NULL CHECK (price_cents >= 0 AND price_cents % 10 = 0),
    effective_from TEXT NOT NULL,
    effective_to TEXT,
    reason TEXT NOT NULL,
    created_at TEXT NOT NULL,
    created_by TEXT,
    CHECK (effective_to IS NULL OR effective_to > effective_from),
    UNIQUE (product_id, effective_from)
);

CREATE TABLE IF NOT EXISTS product_price_policy (
    product_id TEXT PRIMARY KEY,
    target_margin_basis_points INTEGER NOT NULL DEFAULT 3000 CHECK (target_margin_basis_points > 0 AND target_margin_basis_points < 10000),
    minimum_margin_basis_points INTEGER NOT NULL DEFAULT 2000 CHECK (minimum_margin_basis_points >= 0 AND minimum_margin_basis_points < 10000),
    locked_until TEXT,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sale_price_change_proposal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id TEXT NOT NULL,
    purchase_id INTEGER REFERENCES purchases(id),
    current_price_cents INTEGER NOT NULL,
    theoretical_price_cents TEXT NOT NULL,
    suggested_price_cents INTEGER NOT NULL CHECK (suggested_price_cents % 10 = 0),
    approved_price_cents INTEGER CHECK (approved_price_cents IS NULL OR approved_price_cents % 10 = 0),
    effective_from TEXT,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
    warning TEXT,
    reason TEXT,
    created_at TEXT NOT NULL,
    created_by TEXT
);

CREATE VIEW IF NOT EXISTS product_cost_history AS
SELECT pl.id,
       pl.product_id,
       p.supplier_id,
       p.id AS purchase_id,
       pl.id AS purchase_line_id,
       pl.net_amount_cents,
       pl.total_received_units,
       p.purchased_at AS recorded_at
FROM purchase_lines pl
JOIN purchases p ON p.id = pl.purchase_id
WHERE p.status = 'CONFIRMED';

CREATE INDEX IF NOT EXISTS idx_purchase_lines_product ON purchase_lines(product_id, id);
CREATE INDEX IF NOT EXISTS idx_purchases_supplier_date ON purchases(supplier_id, purchased_at);
CREATE INDEX IF NOT EXISTS idx_prices_product_effective ON sale_price_history(product_id, effective_from);
CREATE INDEX IF NOT EXISTS idx_proposals_status ON sale_price_change_proposal(status, product_id);

INSERT OR IGNORE INTO sale_price_history(product_id, price_cents, effective_from, effective_to, reason, created_at, created_by)
SELECT id_productos,
       CAST(ROUND(CAST(precio_unitario_venta AS REAL) * 100.0) AS INTEGER),
       '1970-01-01T00:00:00Z',
       NULL,
       'Importado desde productos.precio_unitario_venta',
       strftime('%Y-%m-%dT%H:%M:%fZ', 'now'),
       NULL
FROM productos
WHERE id_productos IS NOT NULL
  AND trim(id_productos) <> ''
  AND CAST(ROUND(CAST(precio_unitario_venta AS REAL) * 100.0) AS INTEGER) % 10 = 0
GROUP BY id_productos;
