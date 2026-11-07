# Facturación – App Android para cualquier negocio

App Android (Kotlin + Jetpack Compose + Room) para crear facturas tipo tirilla e imprimirlas en impresoras térmicas de red (ESC/POS, puerto 9100).
Incluye una segunda APK (`keygen`) que genera las llaves de uso.

## Compilar con GitHub
1. Sube **todo** el contenido de esta carpeta a un repositorio (incluida la carpeta oculta `.github`).
2. Pestaña **Actions** → "Compilar APK" (corre al hacer push o con *Run workflow*).
3. Descarga los dos artefactos:
   - **Facturacion-APK** → `app-debug.apk` (la app que se instala a los clientes).
   - **GeneradorLlaves-APK** → `keygen-debug.apk` (solo para ti; no la repartas).

## Novedades de la versión 2.1
- **Método de pago**: en *Crear Factura* y *Factura por Voz*, debajo de Dirección hay un menú desplegable (Efectivo, Transferencia QR, Tarjeta, Sistecrédito, Addi). Se guarda en la factura y sale en la tirilla.
- **Facturas → VENTAS**: filtro por método de pago y panel *Ventas por método de pago* (al tocar un método se filtra). Las facturas antiguas aparecen como «Sin definir».
- **Botón EXPORTAR EXCEL DESCARGAS** (pestañas FACTURAS y VENTAS): genera un .xlsx en la carpeta Descargas respetando los filtros activos. Estructura: FECHA | DESCRIPCION (producto vendido) | una columna por empleado registrado en Base de Datos (el valor va en la columna del vendedor de la factura) + fila de TOTAL.
- La base de datos migra sola de la versión 2 a la 3.

## Novedades de la versión 2.0
- **Configuración → Datos del Negocio**: nombre del negocio (sale en el menú superior y en la factura), logo .png y casilla para imprimirlo arriba de la factura junto al nombre. Sin la casilla, el logo solo aparece en la barra superior de la app.
- **Colores**: color primario (barras y botones) y secundario (letras e iconos), en hexadecimal (`#FF0000`).
- **Modo oscuro**: botón con luna en el menú superior.
- **Base de Datos**: cada producto tiene *Cantidad en existencias* (se descuenta al facturar; se puede editar con el lápiz) y botón **Agregar Empleado**.
- **Vendedor**: al facturar hay un menú desplegable; queda seleccionado el último vendedor usado.
- **Facturas → pestaña VENTAS**: total vendido, filtro por día y por vendedor, ventas por vendedor, productos vendidos y detalle de facturas.
- **Activación**: la primera vez la app pide una llave de uso; queda ligada al teléfono y no se vuelve a pedir.

## Llaves de uso
1. Abre la APK **Generador de Llaves** y toca *Generar llave*.
2. Entrega la llave al cliente; la escribe en la pantalla de activación de la app.
- **Llave general** (código vacío): válida en cualquier teléfono, pero sin servidor no hay forma de impedir que la misma llave se use en varios teléfonos.
- **Llave exclusiva**: la pantalla de activación muestra un *Código de este dispositivo* (8 caracteres). Escríbelo en el generador y la llave solo funcionará en ese teléfono.
- El secreto de firma está en `common/src/com/lapaola/common/LicenseCore.kt` (`SECRET_PARTS`). Cámbialo **una sola vez antes de repartir la app** (cualquier llave anterior deja de servir) y no compartas la APK del generador.

## Notas
- Datos existentes: la base de datos migra sola (productos quedan con existencias 0, facturas antiguas sin vendedor).
- Los productos de ejemplo ya no se crean en instalaciones nuevas.
- El celular y la impresora deben estar en la misma red. Si salen símbolos raros, activa "Imprimir sin tildes".
- El dictado usa el reconocimiento de voz de Google (necesita internet) en español de Colombia.
