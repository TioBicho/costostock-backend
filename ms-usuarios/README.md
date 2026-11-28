# ms-usuarios: direcciones y preferencias de cada usuario (Costo Stock)

> Es parte de [`costostock-backend`](../README.md): ahí se explica cómo abrir y ejecutar todos los microservicios juntos.

Microservicio REST hecho en Kotlin + Spring Boot 3. Corre en el **puerto 8083**.

Guarda los datos propios de cada usuario que ms-auth no maneja: sus **direcciones** y sus **preferencias**. El nombre, el teléfono, la ficha del vendedor y la aprobación de cuentas siguen en ms-auth.

**Cualquier cuenta con sesión puede usarlo (CLIENTE, VENDEDOR o ADMINISTRADOR), pero cada una solo ve y edita lo suyo.** Sin token se recibe **401**.

## Cómo sabe quién es el usuario

No llama a ms-auth: lee el **mismo token** que ms-auth entrega al iniciar sesión, igual que ms-productos.

1. La app hace login en ms-auth y recibe el accessToken, un JWE cifrado con la clave AUTH_TOKEN_KEY.
2. La app envía ese token a ms-usuarios en Authorization: Bearer <token>.
3. RolInterceptor (en Security.kt) revisa **todas** las rutas /api/direcciones/... y /api/preferencias antes de que lleguen al controlador:
   - **sin token**, o un token alterado, vencido o de otra clave → **401**;
   - **token válido** → entra, y el controlador sabe su id, correo y role.

El id del token es el dueño de cada dirección y de cada preferencia. No hay forma de pedir los datos de otro usuario, ni siquiera siendo ADMINISTRADOR (→ **403**).

Para descifrar el token, ms-usuarios necesita **la misma clave** que ms-auth. En el perfil dev los tres microservicios traen la misma clave de desarrollo, así que no hay que configurar nada.

El token dura 15 minutos. Si un admin rechaza una cuenta, su token puede seguir sirviendo aquí hasta que venza.

## Reglas

**Direcciones**

- Cada usuario puede guardar hasta **5** (la sexta responde 400).
- La primera que se guarda queda como **principal**. Solo puede haber una principal por usuario: al guardar o editar una con "principal": true, la anterior deja de serlo.
- Enviar "principal": false en una edición no desmarca la principal; para cambiarla se marca otra.
- Si se elimina la principal, pasa a serlo la más reciente que quede.

**Preferencias**

- Hay un solo registro por usuario. Mientras no se guarde nada, GET devuelve los valores por defecto: sin categorías, maxDistanceKm 5 y offerAlerts true.
- PUT reemplaza todos los campos; lo que no se envía vuelve al valor por defecto. DELETE restablece todo.
- favoriteCategories: hasta 10, de hasta 40 caracteres. Se ignoran las vacías y las repetidas (sin distinguir mayúsculas).
- maxDistanceKm: entre 1 y 50.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/direcciones` | Mis direcciones (la principal primero) |
| GET | `/api/direcciones/{id}` | Una dirección mía |
| POST | `/api/direcciones` | Guarda una dirección (201) |
| PUT | `/api/direcciones/{id}` | Edita una dirección (todos los campos) |
| DELETE | `/api/direcciones/{id}` | Elimina una dirección (204) |
| GET | `/api/preferencias` | Mis preferencias |
| PUT | `/api/preferencias` | Guarda mis preferencias |
| DELETE | `/api/preferencias` | Restablece las preferencias (204) |

Cuerpo de POST y PUT de direcciones. street, commune y region se llaman igual que en la ficha del vendedor de la app:

```json
{
  "alias": "Casa",
  "street": "Av. Pedro Montt 1820",
  "commune": "Valparaíso",
  "region": "Valparaíso",
  "reference": "Depto 5, timbre 2",
  "principal": false
}
```

Campos obligatorios: alias (hasta 30 caracteres), street (80), commune (60) y region (60). reference es opcional (hasta 120).

La respuesta agrega id, createdAt y updatedAt (en milisegundos).

Cuerpo de PUT de preferencias (maxDistanceKm usa el mismo nombre que distanceKm de los productos):

```json
{
  "favoriteCategories": ["Lácteos", "Panadería"],
  "maxDistanceKm": 10,
  "offerAlerts": true
}
```

La respuesta trae los mismos campos más updatedAt (en milisegundos, null si nunca se guardaron).

Los errores salen como {"error": "mensaje"}, igual que en ms-auth y ms-productos.

## Ejecutar y probar

1. Ejecuta **ms-auth** y **ms-usuarios**: ▶ en Application.kt de cada uno, o con las configuraciones ms-auth y ms-usuarios.
2. Cuando veas Started UsuariosApplicationKt, el servicio está en **http://localhost:8083**.
3. Abre requests.http y ejecuta las peticiones **en orden**:
   1. se registra un cliente y se hace login (también el del administrador);
   2. el cliente guarda direcciones y preferencias;
   3. el administrador intenta ver una dirección ajena y recibe **403**.
4. Pruebas automáticas: clic derecho sobre src/test → **Run Tests**. No necesitan ms-auth corriendo.

Desde el emulador la dirección es `http://10.0.2.2:8083/`; desde un celular físico, `http://IP-DE-TU-PC:8083/`.

### Producción

```bash
export SPRING_PROFILES_ACTIVE=prod
export AUTH_TOKEN_KEY=...   # LA MISMA de ms-auth (si no, todos los tokens darán 401)
export DB_URL=jdbc:postgresql://host:5432/usuarios DB_USER=... DB_PASSWORD=...
```
