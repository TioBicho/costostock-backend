# auth-service — Login y registro (Costo Stock)

Microservicio REST en Kotlin + Spring Boot 3 que reemplaza el `UserRepository` en memoria de la app Android.

## Diseño del token

| Token | Qué es | Vida | Dónde se guarda |
|---|---|---|---|
| **Access token** | JWE (AES-256-GCM): el contenido va **cifrado y autenticado** | 15 min | Solo en el cliente |
| **Refresh token** | Valor aleatorio opaco de 256 bits | 7 días | Solo su hash SHA-256 en BD |

**"Modificar" el token, de forma segura.** El cliente no puede alterarlo (si cambia un solo byte, el descifrado falla),
pero el *servidor* sí puede actualizarlo cuando cambian las cosas:

- `POST /api/auth/refresh` rota el refresh token y emite un access token nuevo con los datos actuales (rol, estado).
  Así, si un admin aprueba a un vendedor o cambia un rol, el siguiente refresh ya lo refleja.
- `tokenVersion` en la BD invalida de golpe todos los access tokens de un usuario (cambio de contraseña, cuenta rechazada).
- Si se reutiliza un refresh token ya usado, se asume robo y se cierran todas las sesiones.
- Duración y clave se configuran por variables de entorno.

## Ejecutar en IntelliJ IDEA

Requisitos: IntelliJ IDEA (Community o Ultimate) y conexión a internet la primera vez.

1. **File > Open…** y elige la carpeta `auth-service` (la que contiene `build.gradle.kts`). Acepta "Trust Project".
2. Espera a que termine la sincronización de Gradle (barra de progreso abajo a la derecha). La primera vez descarga
   Gradle 8.10.2, las dependencias y, si no tienes una, la JDK 17.
3. Ejecuta con **uno** de estos métodos:
   - Selecciona la configuración **AuthService** (arriba a la derecha) y pulsa ▶ Run, o
   - Abre `Application.kt` y pulsa el ▶ verde junto a `fun main`.
4. Cuando veas `Started AuthServiceApplicationKt`, el servicio está en **http://localhost:8081**.
5. Prueba los endpoints abriendo `requests.http` y pulsando ▶ en cada petición (o usa `curl`, más abajo).
6. Para correr los tests: clic derecho sobre `src/test` > **Run Tests**.

Por defecto corre con el **perfil `dev`**: ya trae una clave de token de desarrollo y un admin
(`admin@costostock.cl` / `Admin12345`), así no necesitas configurar nada. Los datos se guardan en `./data` (H2).
Al arrancar verás un aviso en consola recordándote que es el perfil de desarrollo.

Si la configuración **AuthService** aparece con error, usa el método del ▶ junto a `main`; es equivalente.
Si Gradle se queja de la JDK: **File > Project Structure > SDK** y elige una JDK 17 o 21.

### Producción

```bash
export SPRING_PROFILES_ACTIVE=prod
export AUTH_TOKEN_KEY=$(openssl rand -base64 32)   # obligatoria; si cambia, se invalidan los tokens
export ADMIN_EMAIL=admin@tudominio.cl
export ADMIN_PASSWORD='una-clave-larga-y-unica'
export DB_URL=jdbc:postgresql://host:5432/auth DB_USER=... DB_PASSWORD=...
```

Fuera del perfil `dev` el servicio no arranca si falta `AUTH_TOKEN_KEY`. En IntelliJ puedes definir estas variables en
**Run > Edit Configurations > Environment variables**.

Nota: el repositorio no incluye `gradlew` (el jar del wrapper es un binario); IntelliJ no lo necesita.
Si quieres usar Gradle desde la terminal, ejecuta una vez `gradle wrapper` con Gradle instalado.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/register` | Crea cuenta. CLIENTE queda aprobada; VENDEDOR queda PENDIENTE y exige `ficha` |
| POST | `/api/auth/login` | Devuelve `accessToken` + `refreshToken` |
| POST | `/api/auth/refresh` | Rota el refresh token y emite tokens nuevos |
| POST | `/api/auth/logout` | Revoca el refresh token |
| GET | `/api/auth/me` | Datos del usuario (requiere `Authorization: Bearer …`) |
| PUT | `/api/auth/me` | Cambia nombre y teléfono del usuario: `{"nombre": "...", "telefono": "..."}` |
| POST | `/api/auth/change-password` | Cambia clave e invalida las sesiones anteriores |
| GET | `/api/admin/requests` | Solicitudes pendientes, con la ficha del vendedor (ADMINISTRADOR). `?todas=true` incluye también las ya aprobadas o rechazadas |
| PATCH | `/api/admin/users/{id}/status` | `{"status":"APROBADA"}` o `"RECHAZADA"` (ADMINISTRADOR; no sobre su propia cuenta) |

Los usuarios se devuelven con `id`, `correo`, `nombre`, `telefono`, `role`, `status`, `requestedAt` (milisegundos, solo si pidió aprobación) y `ficha` (solo vendedores).
Los errores siempre salen como `{"error": "mensaje para mostrar"}`.

```bash
curl -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"correo":"ana@mail.cl","password":"clave1234","nombre":"Ana","telefono":"+56 9 1111 2222"}'

curl -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"correo":"ana@mail.cl","password":"clave1234"}'

curl localhost:8081/api/auth/me -H "Authorization: Bearer <accessToken>"
```

## Integración con la app Android

La app (rama con la conexión a este servicio) ya trae Retrofit configurado. Solo hay que apuntarla a tu PC:

- **Emulador de Android Studio:** `http://10.0.2.2:8081/` (10.0.2.2 es tu PC vista desde el emulador).
- **Celular físico:** `http://IP-DE-TU-PC:8081/`. El PC y el celular deben estar en la misma WiFi; la IP se ve con
  `ipconfig` (Windows, "Dirección IPv4"). Para comprobar que el celular llega al servicio, abre en su navegador
  `http://IP-DE-TU-PC:8081/api/auth/me`: debe mostrar `{"error":"Falta el token"}`.
  Si no carga: permite Java en el Firewall de Windows (redes privadas) y revisa que la WiFi del PC esté como "Privada".
  Las WiFi de universidades o de invitados suelen bloquear la conexión entre equipos: usa el hotspot del celular.
- Desde la app no se pueden registrar administradores: el único admin es el que se crea al arrancar.
- Guarda los tokens en DataStore o EncryptedSharedPreferences, nunca en texto plano.
- Si una llamada devuelve 401, llama a `/refresh` una vez y reintenta; si también falla, manda al login.
- Si otro microservicio necesita validar el token, tiene que compartir la clave `AUTH_TOKEN_KEY`
  (o validar llamando a `/me`). Para evitar compartir secretos, el siguiente paso sería pasar a firma asimétrica (RS256/EdDSA).

## Pendiente antes de producción

- HTTPS obligatorio (el token viaja en cada request).
- Rate limiting en `/login` y `/register` contra fuerza bruta.
- Validar formato de RUT y de los campos de la ficha.
- Limpieza periódica de refresh tokens expirados.
- Tests (el hashing, la rotación y la detección de reutilización son fáciles de cubrir).
