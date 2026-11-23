# ms-productos: CRUD de productos (Costo Stock)

Microservicio REST hecho en Kotlin + Spring Boot 3 con el mismo estilo que `ms-auth`. Corre en el **puerto 8082**.

**Solo las cuentas VENDEDOR o ADMINISTRADOR pueden usarlo.** Un CLIENTE recibe **403** en todas las rutas, y sin token se recibe **401**.

## Cómo sabe quién es el usuario

No llama a `ms-auth`: lee el **mismo token** que `ms-auth` entrega al iniciar sesión.

1. La app hace login en `ms-auth` y recibe el `accessToken`, un JWE cifrado con la clave `AUTH_TOKEN_KEY`.
2. La app envía ese token a `ms-productos` en `Authorization: Bearer <token>`.
3. `RolInterceptor` (en `Security.kt`) revisa **todas** las rutas `/api/productos/...` antes de que lleguen al controlador:
   - **sin token**, o un token alterado, vencido o de otra clave → **401**;
   - **rol CLIENTE** → **403** `"Solo las cuentas de VENDEDOR o ADMINISTRADOR pueden gestionar productos"`;
   - **VENDEDOR o ADMINISTRADOR** → entra, y el controlador sabe su `id`, `correo` y `role`.

Para descifrar el token, ms-productos necesita **la misma clave** que ms-auth. En el perfil `dev` ambos traen la misma clave de desarrollo, así que no hay que configurar nada.

El token dura 15 minutos. Si un admin rechaza una cuenta, su token puede seguir sirviendo aquí hasta que venza: ms-auth ya no se lo renueva.

## Reglas del CRUD

| Acción | VENDEDOR | ADMINISTRADOR | CLIENTE |
|---|---|---|---|
| Ver y listar productos | ✅ todos | ✅ todos | ❌ 403 |
| Crear producto | ✅ queda a su nombre | ✅ queda a su nombre | ❌ 403 |
| Editar / eliminar | ✅ **solo los suyos** (403 si es ajeno) | ✅ cualquiera | ❌ 403 |

**Ofertas:** `offerPrice` es opcional y debe ser menor que `originalPrice`. Al poner una oferta nueva hay que indicar `offerDurationMinutes`, entre 1 y 10080 minutos (7 días), y el servicio calcula `offerEndsAt`, `discountPercent`, `onOffer` y `currentPrice`.
Al editar un producto que ya está en oferta sin enviar `offerDurationMinutes`, la oferta mantiene su fin. Si no se envía `offerPrice`, la oferta se quita.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/productos` | Lista todos (del más nuevo al más antiguo). Filtros: `?mios=true` (solo los propios) y `?q=texto` (busca en nombre, categoría y tienda) |
| GET | `/api/productos/{id}` | Un producto |
| POST | `/api/productos` | Crea un producto (201) |
| PUT | `/api/productos/{id}` | Edita un producto (todos los campos) |
| DELETE | `/api/productos/{id}` | Elimina un producto (204) |

Cuerpo de POST y PUT. Los nombres de los campos son los mismos de `ProductOffer` en la app:

```json
{
  "title": "Pan amasado x6",
  "description": "Horneado hoy",
  "category": "Panadería",
  "storeName": "Tienda Central",
  "address": "Av. Pedro Montt 1820",
  "originalPrice": 2000,
  "offerPrice": 1200,
  "offerDurationMinutes": 60,
  "stock": 10,
  "imageUrl": "https://...",
  "pickupHours": "Lunes a viernes 9:00 a 18:00",
  "co2SavedKg": 0.5
}
```

Campos obligatorios: `title`, `category`, `storeName`, `originalPrice` (mayor que 0) y `stock` (0 o más).

La respuesta agrega estos campos:
- `id`, `sellerId` y `sellerEmail` (quién lo publicó);
- `offerEndsAt` (en milisegundos), `discountPercent`, `onOffer` y `currentPrice`;
- `createdAt` y `updatedAt`.

Los errores salen como `{"error": "mensaje"}`, igual que en `ms-auth`.

En el perfil `dev`, si la base está vacía, se cargan los 3 productos de ejemplo de la app. Quedan a nombre de una tienda demo, así que solo el administrador puede editarlos.

## Ejecutar y probar

1. Ejecuta **ms-auth** y **ms-productos**: ▶ en `Application.kt` de cada uno, o con las configuraciones `ms-auth` y `ms-productos`.
2. Cuando veas `Started ProductosApplicationKt`, el servicio está en **http://localhost:8082**.
3. Abre `requests.http` y ejecuta las peticiones **en orden**:
   1. el admin inicia sesión;
   2. se registra un vendedor y el admin lo aprueba;
   3. se registra un cliente;
   4. se prueba el CRUD con cada uno.
4. Pruebas automáticas: clic derecho sobre `src/test` → **Run Tests**.

Desde el emulador la dirección es `http://10.0.2.2:8082/`; desde un celular físico, `http://IP-DE-TU-PC:8082/`.

### Producción

```bash
export SPRING_PROFILES_ACTIVE=prod
export AUTH_TOKEN_KEY=...   # LA MISMA de ms-auth (si no, todos los tokens darán 401)
export DB_URL=jdbc:postgresql://host:5432/productos DB_USER=... DB_PASSWORD=...
```
