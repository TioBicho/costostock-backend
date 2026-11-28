# costostock-backend: microservicios de Costo Stock

Backend de la app **Costo Stock**. Cada carpeta `ms-*` es un microservicio independiente, hecho en Kotlin + Spring Boot 3. Cada uno tiene su propio puerto y su propia base de datos.

| Microservicio | Puerto | Qué hace | Quién puede usarlo |
|---|---|---|---|
| [`ms-auth`](ms-auth) | 8081 | Registro, login y **TOKEN**; perfil; aprobación de vendedores | Público (login/registro); con token (perfil); ADMINISTRADOR (solicitudes) |
| [`ms-productos`](ms-productos) | 8082 | **CRUD de productos** | Solo **VENDEDOR** o **ADMINISTRADOR** (CLIENTE → 403) |
| [`ms-usuarios`](ms-usuarios) | 8083 | **Direcciones y preferencias** de cada usuario | Cualquier cuenta con sesión; cada una solo ve y edita lo suyo |

```text
costostock-backend/
├── settings.gradle.kts      ← junta los microservicios para abrirlos juntos en IntelliJ
├── .run/                    ← configuraciones de ejecución "ms-auth", "ms-productos" y "ms-usuarios"
├── ms-auth/                 ← login y registro (Vicente)
│   ├── build.gradle.kts
│   ├── requests.http
│   └── src/...
├── ms-productos/            ← CRUD de productos
│   ├── build.gradle.kts
│   ├── requests.http
│   └── src/...
└── ms-usuarios/             ← direcciones y preferencias de cada usuario
    ├── build.gradle.kts
    ├── requests.http
    └── src/...
```

## Cómo funciona el token entre microservicios

```text
App ──login──► ms-auth (8081) ──► entrega accessToken (cifrado con AUTH_TOKEN_KEY)
 │
 └─ Authorization: Bearer <token> ──► ms-productos (8082)
                                        └─ lo descifra con la MISMA clave, lee el rol
                                           y deja entrar solo a VENDEDOR / ADMINISTRADOR
```

Todos los microservicios comparten la clave `AUTH_TOKEN_KEY`. En el perfil de desarrollo ya traen la misma, así que **no hay que configurar nada para probar en el PC**.

## Ejecutar en IntelliJ IDEA

1. **File → Open…** y elige **esta carpeta** (`costostock-backend`). Acepta "Trust Project".
2. Espera la sincronización de Gradle. Aparecen `ms-auth`, `ms-productos` y `ms-usuarios` en la pestaña Gradle. La primera vez se descargan Gradle 8.10.2, las librerías y la JDK 17 si no la tienes.
3. **Gradle JVM:** en **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM**, elige 17 o 21. Con 24 o 25, Gradle 8.10.2 no sincroniza.
4. **Ejecuta los dos microservicios:**
   - Elige **ms-auth** arriba a la derecha y presiona ▶. Luego elige **ms-productos** y presiona ▶. Cada uno abre su pestaña en la consola.
   - Si una configuración aparece con error, abre el `Application.kt` de ese microservicio y usa el ▶ verde junto a `fun main`.
   - Otra opción: en la pestaña Gradle, `ms-auth` → Tasks → application → **bootRun**, y lo mismo en `ms-productos`.
5. **Comprueba que arrancaron:** en la consola deben aparecer `Started AuthServiceApplicationKt` (puerto 8081) y `Started ProductosApplicationKt` (puerto 8082).

Cada microservicio también se puede abrir solo (**File → Open…** → `ms-auth` o `ms-productos`).

## Probar

- **Pruebas automáticas:** en cada microservicio, clic derecho sobre `src/test` → **Run Tests**.
- **A mano:** abre el `requests.http` de cada microservicio y presiona ▶ en cada petición, en orden. Esto necesita el cliente HTTP de IntelliJ Ultimate, gratis con la licencia de estudiante; también puedes usar Postman.
  - `ms-productos/requests.http` recorre todo el flujo: el admin aprueba a un vendedor, el vendedor hace el CRUD, y un cliente intenta entrar y recibe **403**.
- **Desde el celular:** el PC y el celular deben estar en la misma WiFi.
  1. En el PC ejecuta `ipconfig` y copia la IPv4.
  2. En el navegador del celular abre `http://ESA-IP:8081/api/auth/me` y `http://ESA-IP:8082/api/productos`.
  3. Los dos deben responder `{"error":"Falta el token"}`.

  Si no cargan, permite Java en el Firewall de Windows (redes privadas). En WiFi de universidades o de invitados, usa el hotspot del celular.

Los datos se guardan en la carpeta `data/` de cada microservicio (base H2). Para empezar de cero, detén el servicio y borra esa carpeta.

## Agregar otro microservicio

1. Copia `ms-productos` con otro nombre (ej: `ms-ventas`) y cambia lo siguiente:
   - en `settings.gradle.kts` de la copia: `rootProject.name`;
   - en `application.yml`: el puerto (8083, 8084…), `spring.application.name` y el nombre del archivo de la base;
   - el paquete `cl.costostock.productos`.
2. Agrégalo en el `settings.gradle.kts` de esta carpeta: `includeBuild("ms-ventas")`.
3. `Security.kt` ya trae la lectura del token y el control de roles. Cambia `ROLES_PERMITIDOS` y las rutas en `WebConfig` según lo que necesite tu microservicio.
