# Diseño de compras, costos y pricing

## Objetivo y alcance

Esta iteración amplía PepitoApp como monolito modular offline-first. Todos los módulos viven en el mismo proceso JavaFX y usan la misma base SQLite de productos. No se añadieron servidor, API, Spring, Docker, cloud ni un segundo programa. El módulo criptográfico existente no fue reescrito.

Los flujos incorporados son:

- recepción rápida por lector USB HID o teclado;
- registro transaccional de compras, promociones e inventario;
- historial y proyecciones de costo;
- propuestas de revisión por deterioro de margen;
- precios de venta independientes, programables y protegibles por fecha;
- centro administrativo de revisión de precios.

## Arquitectura

El código nuevo está separado por responsabilidad:

```text
presentation / controller
  PurchaseReceptionController
  PriceReviewController
        |
application
  ProductQueryService / AddPurchaseLineService
  RegisterPurchaseService / PriceReviewService
  ScheduleSalePriceService / SupplierService
        |
domain
  product / supplier / purchase / pricing
        |
infrastructure.sqlite
  migraciones y adaptadores SQLite
```

Los controladores recogen entradas, llaman casos de uso y renderizan resultados. No contienen SQL. `BodegaFXMLController` fue ajustado sólo lo necesario para consultar catálogo/precio vigente mediante `ProductQueryService`; el carrito y la boleta legacy se conservaron.

`ApplicationServices` es el punto de composición sencillo apropiado para el tamaño actual. Inicializa la migración antes de cargar FXML y construye los servicios. No se introdujo un framework de inyección de dependencias.

## Migración y compatibilidad

La migración versionada es `src/main/resources/db/migration/V001__purchase_pricing.sql`. `SQLiteMigrationRunner` registra versiones en `schema_migrations` y ejecuta cada versión una sola vez dentro de una transacción.

La tabla legacy `productos` no se elimina ni cambia. Sus columnas monetarias siguen siendo `TEXT` por compatibilidad. La migración importa los precios válidos existentes a `sale_price_history`. El código nuevo usa centavos enteros y las columnas legacy quedan como puente temporal.

La base observada contiene códigos de producto sin clave primaria y dos códigos duplicados (`7622201389284` y `7750106003094`) asociados a productos distintos. Para no corromper stock, las rutas nuevas rechazan un código ambiguo en vez de elegir una fila arbitraria. Corregir esos duplicados requiere una decisión de catálogo y queda para mantenimiento de datos.

## Tablas

### Proveedores y presentaciones

- `suppliers`: proveedor y RUC opcional.
- `supplier_product`: presentación habitual por proveedor/producto, unidades por pack, SKU opcional y último precio de pack.
- `product_barcode_alias`: permite asociar un código escaneado nuevo a un producto existente sin convertirlo a número.

### Compras e inventario

- `purchases`: proveedor, fecha real, documento, notas y estado confirmado.
- `purchase_lines`: fuente de verdad de packs pagados/bonificados, unidades bonificadas, precio de pack, descuento, bruto, neto y unidades recibidas.
- `inventory_movements`: movimiento positivo auditable por cada línea confirmada.
- `product_cost_summary`: proyección de último costo y acumulados para promedio histórico.

La confirmación ejecuta en una única transacción:

```text
BEGIN
  validar proveedor y códigos únicos
  INSERT purchases
  INSERT purchase_lines
  UPSERT product_cost_summary
  INSERT inventory_movements
  UPDATE productos.stock_unidades
  UPSERT supplier_product
  INSERT propuesta si corresponde
COMMIT
```

Ante cualquier fallo se hace `ROLLBACK`. El test de integración comprueba que una segunda línea inválida no deja cabecera, línea ni stock parcial.

### Costos

`product_cost_history` es una vista derivada de compras confirmadas. Se eligió una vista porque `purchase_lines` ya contiene el numerador (`net_amount_cents`) y denominador (`total_received_units`) necesarios. Guardar además un decimal de costo duplicaría información y podría desincronizarse. La vista conserva vínculo a proveedor, compra y línea.

`product_cost_summary` es una proyección deliberada para consultas rápidas:

- último costo: `latest_net_amount_cents / latest_received_units`;
- promedio histórico ponderado: `cumulative_net_amount_cents / cumulative_received_units`.

Este promedio es de compras históricas recibidas. No se presenta como costo promedio del stock disponible: el modelo legacy no registra salidas por lote/capa de costo y no permite demostrar FIFO, promedio móvil de existencias u otra valorización exacta.

### Pricing

- `sale_price_history`: precio en centavos, vigencia, motivo y creador opcional.
- `product_price_policy`: margen objetivo, margen mínimo y `locked_until`.
- `sale_price_change_proposal`: costo impactó margen; registra precio matemático, sugerencia comercial, advertencia y estado.

Los estados previstos son `PENDING`, `APPROVED`, `REJECTED` y `APPLIED`. Esta iteración crea propuestas `PENDING` y programa precios en el historial. La aprobación por usuario autenticado y el cambio automático de estado a `APPLIED` quedan pendientes porque el prototipo no tiene usuarios/roles.

Los períodos de vigencia pueden solaparse físicamente; la consulta vigente selecciona la entrada más reciente cuyo `effective_from` ya llegó. Esto permite programar escalones (por ejemplo 3.7 en septiembre y 3.9 en noviembre) sin modificar el precio actual antes de tiempo.

## Fórmulas y precisión monetaria

Para una línea:

```text
paidUnits            = paidPacks * unitsPerPack
bonusPackUnits       = bonusPacks * unitsPerPack
totalReceivedUnits   = paidUnits + bonusPackUnits + bonusUnits
grossAmountCents     = paidPacks * packPriceCents
netAmountCents       = grossAmountCents - discountCents
effectiveUnitCost    = netAmountCents / totalReceivedUnits
```

Cantidades e importes pagados se almacenan como `INTEGER`. Los cocientes se calculan con `BigDecimal`, `MathContext(16, HALF_UP)`. No se usa `double` o `float` en reglas monetarias nuevas ni se almacena un costo efectivo redondeado.

Ejemplo: 10 cajas pagadas, 1 bonificada, 24 unidades y S/ 60 por caja producen 264 unidades, 60000 centavos netos y `227.2727272727273...` centavos por unidad (`S/ 2.272727...`).

El margen usado es margen bruto, no markup:

```text
grossMargin = (salePrice - unitCost) / salePrice
```

La variación de costo es:

```text
(latestCost - previousCost) / previousCost
```

El precio matemático para un margen objetivo es:

```text
unitCost / (1 - targetMargin)
```

La sugerencia comercial usa `roundUpTo10Cents`: divide entre 10, aplica `CEILING` y multiplica por 10. Así, 360 permanece 360 y 361/369 suben a 370.

Todo precio final de venta se valida con `priceCents % 10 == 0`. La UI lo muestra sin ceros innecesarios (`S/ 3.5`). El costo conserva más decimales.

## Separación costo / precio de venta

Registrar una compra nunca actualiza `productos.precio_unitario_venta` ni inserta un precio activo. Sólo actualiza costos, inventario, presentación habitual y, si el margen cae bajo el mínimo, una propuesta. El precio cambia únicamente al programarlo explícitamente desde el centro de revisión y sólo resulta vigente cuando llega `effective_from`.

Si `locked_until` es posterior a la fecha de compra, el sistema crea la advertencia “Precio protegido hasta …” y mantiene el precio. El administrador puede cambiar la política o programar un override con motivo; no existe una imposición automática.

## Uso

1. Configure `local-data/productos2.db` o `PEPITO_PRODUCTOS_DB` como antes.
2. Ejecute `mvn javafx:run`.
3. Desde la pantalla principal abra **Recepción de compras**.
4. Seleccione o cree proveedor, escanee, complete cantidades/precio/descuento y use Enter hasta añadir la línea.
5. Confirme la compra para persistir todas las líneas de forma atómica.
6. Abra **Revisión de precios** para ver costos, margen, variación y propuestas; seleccione un producto y programe un precio válido.

Un código desconocido muestra un panel no modal. Puede asociarse a un código existente, crearse con nombre/precio inicial o cancelarse para volver inmediatamente al escáner. Los códigos se mantienen como `String`, incluidos ceros iniciales.

## Verificación

```bash
mvn test
```

La suite cubre promociones, descuentos, costos no exactos, promedio ponderado, variaciones, validación/redondeo comercial, independencia costo/precio, precios futuros, protección, ceros iniciales y rollback transaccional.

## Limitaciones conocidas y siguiente iteración

- No existe autenticación/roles en el POS; `created_by` queda nulo y las pantallas administrativas no tienen autorización real.
- La venta legacy aún usa `Item` con `double` y un archivo de historial. No se amplió esa deuda porque no bloquea compras/pricing y reescribir ventas habría puesto en riesgo el módulo de seguridad.
- La actualización de stock suma a `productos.stock_unidades`; no existe todavía kardex completo de ventas ni costo del stock disponible.
- Los dos códigos duplicados del catálogo deben depurarse manualmente con criterio de negocio.
- Las propuestas aún no tienen botones explícitos de rechazar/aprobar ni actor autenticado. Programar un precio registra la decisión en historial, pero no enlaza/cierra automáticamente una propuesta.
- Debe añadirse una prueba UI automatizada en un entorno con display JavaFX para validar navegación y foco del lector HID de extremo a extremo.
- SQLCipher sigue siendo una capacidad futura del módulo de seguridad; esta iteración usa el mismo SQLite de productos y no cambia las afirmaciones criptográficas existentes.
