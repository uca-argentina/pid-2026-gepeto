# Mapa de archivos

## Registro público de visitantes

- `aparcar-front/app/register/page.jsx`: formulario público con datos personales,
  contraseña y confirmación, errores de duplicados y enlace al login. Reutiliza
  Axios, React Hook Form, Zod, Sonner y los colores de ambos temas.
- `aparcar-front/app/login/page.jsx`: acceso al registro y envío de credenciales
  HTTP Basic en UTF-8.
- `AuthController` / `AuthService` / `RegistrationDto`: alta compartida pública y
  administrativa, siempre activa y USER; no crea vehículos ni reservas.
- `DevSecurityConfig` / `ProdSecurityConfig`: `/register` público, protegido por
  el `RateLimitFilter` existente. `JWTValidationFilter` permite registrarse aunque
  el navegador conserve un token vencido.
- `VisitanteRepository`: consultas de identidad normalizadas; los usuarios nuevos
  aparecen en el listado administrativo existente.
- `005-identidad-visitante-unica.yaml`: índices PostgreSQL para email sin distinguir
  mayúsculas y documento sin espacios extremos, también ante altas concurrentes.
- `RegistrationTests.java` / `RegistrationFlowTests.java`: validaciones, permisos,
  visibilidad administrativa, login real con JWT y límite de intentos.
- `aparcar-front/test/register/page.test.jsx`: envío, validaciones, errores,
  bloqueo durante el alta y visibilidad de contraseñas.

Este flujo reemplaza la descripción histórica de `/register` como exclusivo de
ADMIN que figura más abajo. Se conserva el alta operativa del admin con reserva.

Referencia de qué hace cada archivo del proyecto, para no perderse en el repo. No incluye `node_modules/`, `.next/`, `target/`, `.git/` ni `.idea/` (carpetas generadas, no código del programa).

Convenciones:

- 🆕 = archivo agregado desde la versión anterior de este documento.
- ✏️ = archivo existente que fue modificado desde entonces.

---

## Qué cambió en esta tanda

Resumen de lo nuevo, para no tener que leer todo el archivo buscando las marcas.

**Reservas por franja horaria** 🆕

- Una reserva dejó de ser "por día" y pasó a ser un rango `[desde, hasta)`. Se puede reservar el tiempo que se quiera, incluidos varios días.
- **Toda la regla de "no se pisan" es una sola condición**, en `ReservaRepository`: `r.desde < :hasta AND r.hasta > :desde`. Es el test estándar de intersección de intervalos semiabiertos: cubre los cuatro casos de superposición y deja pasar el borde que hay que permitir — si una reserva termina justo cuando arranca la siguiente, no se pisan y la cochera se puede volver a entregar en ese instante.
- **Las cocheras se liberan solas.** Sale de lo mismo: una reserva vencida ya no intersecta ningún rango futuro, así que deja de ocupar en el instante exacto en que termina, sin depender de que corra ninguna tarea.
- `ReservasVencidas.java` 🆕 marca las vencidas como `FINALIZADA`, pero es **solo informativo**: mantiene el listado legible. Si el proceso se cae un fin de semana, nadie se queda sin poder reservar.
- Un mismo vehículo no puede ocupar dos cocheras a la vez, aunque las dos estén libres.
- El formulario arranca el "desde" en el momento actual y deja elegir día y hora del "hasta", en los dos dashboards.

**⚠️ Zona horaria**

- `ZonaHorariaConfig.java` 🆕 fija la zona de la aplicación (`app.zona-horaria`, por defecto `America/Argentina/Buenos_Aires`).
- Hace falta porque las franjas se guardan como `LocalDateTime`, un reloj de pared sin zona. Es el modelo correcto para un predio —"de 10 a 12" son las 10 y las 12 *del lugar*— pero solo funciona si backend y navegador coinciden en cuál es ese reloj. Sin esto el contenedor arrancaba en UTC mientras el navegador mandaba hora local: una reserva de las 16 a las 17 llegaba a un servidor que creía que eran las 19 y la rechazaba por "terminada en el pasado".

**Integración continua**

- `.github/workflows/ci.yml` 🆕: GitHub Actions corre los checks en cada pull request a `main` o `dev`.

**Seguridad**

- ⚠️ `DevAuthenticationProvider` **ahora valida la contraseña con BCrypt**, igual que producción. Antes autenticaba a cualquiera con solo existir el email. Si venías probando con contraseñas inventadas, ya no funciona.
- `CustomAccessDeniedHandler` 🆕: los 403 ahora devuelven JSON con el mismo formato que los 401, en vez de una página de error de Spring.

**Gestión de datos propios (dashboard USER)**

- `PUT /api/v1/visitantes/me`: el visitante edita su teléfono y su email.
- `PUT /api/v1/vehiculos/{id}` y `DELETE /api/v1/vehiculos/{id}`: editar y borrar vehículos, con control de propietario (un USER solo toca los suyos; un ADMIN, todos). No se puede borrar un vehículo con reservas asociadas.

**Frontend**

- `app/page.jsx` 🆕 reemplaza a `app/page.js`: landing pública, con redirección automática al dashboard si ya hay sesión.
- `app/not-found.js` 🆕: página 404 propia.
- `components/LogoutButton.jsx` 🆕: botón de cerrar sesión, presente en ambos dashboards.
- `MiPerfilContent.jsx` ✏️: pasó de solo cargar datos a gestionarlos (editar perfil, editar y eliminar vehículos).

**Bugfix: no se podía reservar desde el panel admin**

- El formulario de reservas vivía solo en `app/dashboard-user/`, una ruta protegida con `requireAuth(["USER"])`. Una cuenta ADMIN quedaba sin ningún lugar desde donde crear una reserva: el formulario del panel admin era el de alta de visitante, que nunca creó reservas.
- `ReservasContent.jsx` se movió a `components/` 🆕, porque ahora lo usan los dos dashboards, y se agregó al panel admin.
- De paso: la cuadrícula de ocupación se recarga al crear una reserva. Antes cargaba una sola vez al montarse, así que la cochera seguía viéndose libre hasta refrescar la página — el mismo síntoma por otra causa.
- No hizo falta tocar el backend: `/api/v1/reservas/**` ya pedía solo estar autenticado, y `DashboardAccessSecurityTests` ya cubría que un ADMIN puede usarlo.

**Documentación**

- `AparcAR-Manual-Completo.pdf` 🆕: manual de 46 páginas que explica el proyecto de punta a punta para alguien que nunca lo vio.

---

## Raíz del repo

| Archivo | Qué hace |
|---|---|
| `.gitignore` | Ignora `*.class`, `*.jar`, logs, etc. a nivel de todo el repo |
| `README.md` | Guía de instalación y ejecución del proyecto completo (front + back) |
| `ARCHIVOS.md` | Este archivo: mapa general de la estructura del proyecto |
| `TESTS.md` ✏️ | Índice de qué prueba cada archivo de test, front y back, caso por caso (35 archivos, 248 tests) |
| `AparcAR-Manual-Completo.pdf` 🆕 | Manual de 46 páginas: el dominio, las tecnologías usadas una por una, el árbol de carpetas comentado, los endpoints, la seguridad, la base de datos, Docker y el testing. Pensado para alguien que nunca vio el código |

---

## `.github/workflows/` 🆕

| Archivo | Qué hace |
|---|---|
| `ci.yml` 🆕 | Pipeline de GitHub Actions. Corre en cada pull request contra `main` o `dev` |

El workflow usa `dorny/paths-filter` para detectar qué mitad del repo cambió y correr solo esa:

- **backend** (si cambió `aparcar-api-back/**`): JDK 21 Temurin con caché de Maven, y `mvn -B clean install` — que compila y corre los 160 tests.
- **frontend** (si cambió `aparcar-front/**`): Node 24 con caché de npm, y después `npm ci`, `npm run lint`, `npm test` y `npm run build`.

Los dos filtros incluyen además `.github/workflows/**` ✏️: si se toca el propio pipeline, corren las dos suites completas. Sin eso, un PR que solo modifica `ci.yml` no ejecutaba ningún check y el cambio al pipeline se mergeaba sin validarse nunca.

Es decir: si un PR rompe un test, el lint o el build, se ve antes del merge.

### Por qué la condición está en los pasos y no en el job ✏️

Los dos jobs **corren siempre**; lo que se saltea son los pasos de adentro. Es a propósito.

Antes la condición estaba a nivel de job (`if:` al lado de `needs:`). Cuando la mitad correspondiente no cambiaba, GitHub marcaba el job como *skipped*, y **un job salteado nunca reporta conclusión**. Si ese check figura como *required* en la protección de rama, el PR se queda con "Expected — waiting for status to be reported" y no se puede mergear nunca. Es justo lo que pasaba con `CI / backend` en un PR que solo tocaba el frontend.

Con la condición en los pasos, el job arranca igual, reporta verde en segundos cuando no hay nada que hacer, y no gasta el build completo. El nombre del check no cambia, así que no hay que tocar la configuración de la rama protegida.

También se agregó un bloque `permissions` explícito: en eventos `pull_request`, `dorny/paths-filter` le pide a la API de GitHub la lista de archivos del PR, y sin `pull-requests: read` el job `changes` falla — y si ese falla, se saltean los dos que dependen de él.

---

# `aparcar-api-back/` — Backend (Spring Boot)

## Raíz del backend

| Archivo | Qué hace |
|---|---|
| `.env` / `.env.example` | Variables de entorno (credenciales de DB, mail, etc.). `.env` no se sube a git; `.env.example` es la plantilla |
| `.gitignore` | Ignora `target/` y otros archivos generados |
| `.pre-commit-config.yaml` | Hooks que corren antes de cada commit: valida YAML, detecta secretos, limpia espacios en blanco |
| `Makefile` | Comandos `make run`, `make test`, `make build`, `make migrate`, etc. |
| `Taskfile.yml` | Alternativa al Makefile usando Task |
| `README.md` | Instrucciones específicas del backend |
| `docker-compose.yaml` | Define los servicios `db` (PostgreSQL) y `server` (API Spring Boot) |
| `pom.xml` | Dependencias y configuración Maven del backend |

---

## `dockerfiles/`

| Archivo | Qué hace |
|---|---|
| `db.Dockerfile` | Imagen de PostgreSQL usada por Docker |
| `db-start.sh` | Script ejecutado al iniciar el contenedor de base de datos |
| `server.Dockerfile` | Compila el backend con Maven y genera la imagen que ejecuta el `.jar` |

---

## `scripts/`

| Archivo | Qué hace |
|---|---|
| `create-migration.go` | Programa usado por `task migrate` / `make migrate` para generar migraciones Liquibase |

---

# `src/main/java/com/aparcar/api/`

## Raíz

| Archivo | Qué hace |
|---|---|
| `AparcarApiApplication.java` | Punto de entrada (`main`) de la aplicación Spring Boot |

---

## `component/`

Piezas reutilizables e inyectables.

| Archivo | Qué hace |
|---|---|
| `IEmailSender.java` | Contrato para enviar emails |
| `IRevokedUserCache.java` | Contrato para el cache de JWT revocados |
| `OTPCleanup.java` | Tarea programada que elimina códigos OTP vencidos |
| `ReservasVencidas.java` 🆕 | Tarea programada que pasa a `FINALIZADA` las reservas cuya franja terminó. **No es lo que libera la cochera**: eso sale del solapamiento de rangos y funciona aunque esta tarea nunca corra. Solo mantiene el listado legible |
| `impl/RevokedUserCache.java` | Implementación del cache de usuarios revocados usando Caffeine |
| `impl/SpringEmailSender.java` | Implementación del envío de emails con `JavaMailSender` |

---

## `config/`

Configuración general de Spring.

| Archivo | Qué hace |
|---|---|
| `ApplicationConstants.java` | Constantes compartidas: perfiles y configuración JWT |
| `AsyncConfig.java` | Configura ejecución de tareas `@Async` |
| `ModelMapperConfig.java` | Configura ModelMapper |
| `SchedulingConfig.java` | Configura tareas `@Scheduled` |
| `WebClientConfig.java` | Configura el bean de `WebClient` |
| `WebConfig.java` | Maneja headers `X-Forwarded-*` |
| `ZonaHorariaConfig.java` 🆕 | Fija la zona horaria de la aplicación (`app.zona-horaria`, por defecto `America/Argentina/Buenos_Aires`). Sin esto el contenedor arranca en UTC y las franjas, que son reloj de pared, quedan desfasadas respecto del navegador |
| `middleware/DevExceptionHandler.java` ✏️ | Convierte excepciones a respuestas JSON detalladas en desarrollo. Agregado el handler de `AccessDeniedException` 🆕, que devuelve 403 con el mensaje del service (ej. "No podés modificar un vehículo que no es tuyo") |
| `middleware/ProdExceptionHandler.java` ✏️ | Manejo de errores para producción, con el mismo handler de `AccessDeniedException` |

---

## `controller/`

Endpoints REST de la aplicación.

| Archivo | Qué hace |
|---|---|
| `AuthController.java` | `/register`, `/login`, `/forgot-password`, `/reset-password` |
| `CocheraController.java` | Gestión de cocheras y consulta de cocheras disponibles |
| `ReservaController.java` | Alta y consulta de reservas |
| `UserController.java` ✏️ | Gestión ADMIN de usuarios: activar, listar inactivos, eliminar, listar todos y editar |
| `VehiculoController.java` ✏️ | Alta y consulta de vehículos, más `PUT /{id}` y `DELETE /{id}` 🆕. Ambos reciben el `Authentication` y le pasan al service el email del que pide y si es ADMIN, para que el service decida si tiene permiso |
| `VisitanteController.java` ✏️ | Alta y consulta de visitantes, más `GET /me`, `POST /me` y ahora `PUT /me` 🆕: el propio visitante (logueado) consulta, carga y edita su perfil sin pasar por un admin. `/me` está declarado **antes** que `/{id}` a propósito, para que `"me"` no se intente parsear como UUID |

### Tabla completa de endpoints

Todo lo que el backend sabe hacer hoy. La columna "Quién puede" sale de `DevSecurityConfig` / `ProdSecurityConfig`.

| Método y ruta | Qué hace | Quién puede |
|---|---|---|
| `POST /register` | Crea una cuenta (nace INACTIVA y con rol USER) | ADMIN |
| `POST /login` | Valida credenciales por HTTP Basic y devuelve el JWT en el header `Authorization` | Autenticable |
| `POST /forgot-password` | Manda un código OTP al mail | Público |
| `POST /reset-password` | Cambia la contraseña usando el OTP | Público |
| `POST /users/activate` | Activa una cuenta | ADMIN |
| `GET /users/inactive` | Lista los emails sin activar | ADMIN |
| `DELETE /users` | Elimina una cuenta (no podés borrarte a vos mismo) | ADMIN |
| `GET /api/v1/usuarios` | Lista todas las cuentas, sin exponer el password | ADMIN |
| `PUT /api/v1/usuarios/{id}` | Edita nombre, teléfono y authorities | ADMIN |
| `POST /api/v1/cocheras` | Crea una cochera (número único) | ADMIN |
| `GET /api/v1/cocheras` | Lista todas las cocheras | ADMIN |
| `GET /api/v1/cocheras/{id}` | Una cochera por id | ADMIN |
| `PUT /api/v1/cocheras/{id}` | Edita; al deshabilitarla, cancela sus reservas CONFIRMADAS | ADMIN |
| `DELETE /api/v1/cocheras/{id}` | Borra; falla con 400 si tiene reservas | ADMIN |
| `GET /api/v1/cocheras/disponibles` ✏️ | Cocheras libres **durante toda la franja** `desde`/`hasta`, filtradas por tipo de vehículo. Ojo: es "libre en todo el rango", no "libre en algún momento" | **Público** |
| `POST /api/v1/visitantes` | Alta de visitante (documento único) | Autenticado |
| `GET /api/v1/visitantes` | Lista de visitantes | Autenticado |
| `GET /api/v1/visitantes/{id}` | Un visitante por id | Autenticado |
| `GET /api/v1/visitantes/me` | Mi propio perfil de visitante | Autenticado |
| `POST /api/v1/visitantes/me` | Cargo mi propio perfil (una sola vez) | Autenticado |
| `PUT /api/v1/visitantes/me` 🆕 | Edito mi teléfono y mi email | Autenticado |
| `POST /api/v1/vehiculos` | Alta de vehículo (patente única, normalizada a mayúsculas) | Autenticado |
| `GET /api/v1/vehiculos` | Lista, con filtro opcional `?visitanteId=` | Autenticado |
| `GET /api/v1/vehiculos/{id}` | Un vehículo por id | Autenticado |
| `PUT /api/v1/vehiculos/{id}` 🆕 | Edita patente y tipo | Dueño o ADMIN |
| `DELETE /api/v1/vehiculos/{id}` 🆕 | Borra; falla con 400 si tiene reservas | Dueño o ADMIN |
| `POST /api/v1/reservas` ✏️ | Crea la reserva sobre la franja `desde`/`hasta`, aplicando las reglas de negocio y el control de superposición | Autenticado |
| `GET /api/v1/reservas` | Lista todas las reservas | Autenticado |
| `GET /api/v1/reservas/{id}` | Una reserva por id | Autenticado |
| `GET /docs` | Swagger UI: la API documentada e interactiva | Público |
| `GET /actuator/health` | Chequeo de salud del servicio | Público |

"Dueño o ADMIN" no lo resuelve Spring Security: lo resuelve `VehiculoService.verificarPropietario()`, comparando el email del JWT contra el `appUser` del visitante dueño del vehículo. Si no coincide y no es ADMIN, lanza `AccessDeniedException` → 403.

---

## `dto/`

Objetos usados para entrada y salida de información de la API.

### Generales

| Archivo | Qué hace |
|---|---|
| `ErrorResponseDto.java` | Formato estándar de errores (`code`, `message`, `details`) |

### `dto/auth/`

| Archivo | Qué hace |
|---|---|
| `RegisteredUserDto.java` | Respuesta devuelta al crear un usuario |
| `RegistrationDto.java` | Body de `POST /register`: nombre, email, password y teléfono |
| `ResetPasswordDto.java` | Body para cambiar contraseña mediante OTP |
| `UserEmailDto.java` | Body genérico `{ email }`, utilizado para activar/eliminar usuarios |
| `UpdateUserDto.java` 🆕 | Body para editar nombre, teléfono y authorities de un usuario |
| `UserResponseDto.java` 🆕 | Respuesta administrativa de usuario: id, nombre, email, teléfono, authorities y estado; no expone password |

### `dto/email/`

| Archivo | Qué hace |
|---|---|
| `PlainEmailData.java` | Datos internos utilizados para enviar un email |

### `dto/reserva/`

| Archivo | Qué hace |
|---|---|
| `VisitanteAltaDto.java` ✏️ | Alta operativa del admin: los datos del visitante, su vehículo y la franja de la reserva, todo junto, porque se crean en una sola transacción |
| `VisitanteResponseDto.java` | Datos devueltos de un visitante |
| `VehiculoRequestDto.java` | Datos recibidos para crear un vehículo |
| `VehiculoUpdateDto.java` 🆕 | Body de `PUT /api/v1/vehiculos/{id}`: patente y tipo. Valida el formato de patente con regex (`AAA000` o `AA000AA`) |
| `VisitanteUpdateDto.java` 🆕 | Body de `PUT /api/v1/visitantes/me`: solo teléfono y email. El nombre y el documento no se editan desde acá |
| `VehiculoResponseDto.java` | Datos devueltos de un vehículo |
| `CocheraRequestDto.java` | Datos recibidos para crear una cochera |
| `CocheraResponseDto.java` | Datos devueltos de una cochera |
| `ReservaRequestDto.java` | Datos necesarios para crear una reserva |
| `ReservaResponseDto.java` | Respuesta completa de una reserva |

---

## `entity/`

Entidades JPA que representan los datos persistidos.

### `entity/auth/`

| Archivo | Qué hace |
|---|---|
| `AppAuthority.java` | Enum de roles internos: `USER` y `ADMIN` |
| `AppUser.java` | Entidad de los usuarios internos que pueden iniciar sesión |
| `InactiveUsersDto.java` | Wrapper con emails de usuarios inactivos |
| `OneTimePassword.java` | Entidad de códigos OTP para recuperación de contraseña |

### `entity/reserva/`

| Archivo | Qué hace |
|---|---|
| `Visitante.java` ✏️ | Entidad de visitantes; agregado `appUser` (`@OneToOne` opcional hacia `AppUser`) para el login propio del visitante |
| `Vehiculo.java` | Entidad de vehículos asociados a visitantes |
| `VehiculoTipo.java` | Enum `AUTO`, `MOTO`, `CARGA` |
| `Cochera.java` | Entidad de cocheras |
| `CocheraTipo.java` | Enum `AUTO`, `MOTO`, `ACCESIBLE`, `CARGA` |
| `CocheraEstado.java` | Estado operativo de una cochera |
| `Reserva.java` | Entidad de reservas |
| `ReservaEstado.java` | Enum `CONFIRMADA`, `CANCELADA` |

---

## `events/`

Listeners utilizados principalmente para logging de Spring Security.

| Archivo | Qué hace |
|---|---|
| `AuthenticationEventsListener.java` | Registra logins exitosos y fallidos |
| `AuthorizationEventsListener.java` | Registra intentos de acceso rechazados |

---

## `exception/`

Excepciones propias del backend.

| Archivo | Qué hace |
|---|---|
| `NotFoundException.java` | Recurso solicitado inexistente → HTTP 404 |
| `OTPException.java` | Error relacionado con OTP |
| `OTPExceptionReason.java` | Motivos posibles de error de OTP |
| `ValidationException.java` | Error de validación o regla de negocio → HTTP 400 |

---

## `filters/`

Filtros HTTP ejecutados durante los requests.

| Archivo | Qué hace |
|---|---|
| `ApiVersionFilter.java` | Agrega el header `X-Api-Version` |
| `JWTGeneratorFilter.java` | Genera el JWT luego de un login exitoso |
| `JWTValidationFilter.java` ✏️ | Valida JWT y carga la autenticación; `/register` ahora también procesa JWT porque requiere ADMIN |
| `RateLimitFilter.java` | Limita intentos sobre endpoints sensibles |
| `StripPortFromXffFilter.java` | Normaliza la IP proveniente de headers proxy |

---

## `repository/`

Acceso a datos usando Spring Data JPA.

| Archivo | Qué hace |
|---|---|
| `AppUserRepository.java` ✏️ | Acceso a `AppUser`; utiliza `UUID` como tipo de ID y permite buscar usuarios por email |
| `CocheraRepository.java` | Acceso a cocheras |
| `OneTimePasswordRepository.java` | Acceso a códigos OTP |
| `ReservaRepository.java` ✏️ | Acceso a reservas y consultas relacionadas con disponibilidad. Agregado `existsByVehiculoId` 🆕, que usa `VehiculoService.eliminar` para no borrar un vehículo con reservas |
| `VehiculoRepository.java` | Acceso a vehículos |
| `VisitanteRepository.java` | Acceso a visitantes |

---

## `security/`

Autenticación y autorización.

| Archivo | Qué hace |
|---|---|
| `AppUserDetailsService.java` | Carga un `AppUser` por email para Spring Security |
| `CustomBasicAuthenticationEntryPoint.java` | Respuesta devuelta cuando una ruta requiere autenticación (401) |
| `CustomAccessDeniedHandler.java` 🆕 | Respuesta devuelta cuando el usuario está autenticado pero no tiene el rol (403). Devuelve JSON con el mismo formato que el 401 (`timestamp`, `status`, `error`, `message`, `path`, `client_ip`) y deja un `log.warn` con la IP. Se registra en las dos security configs con `.exceptionHandling(...)` |
| `authenticationProvider/DevAuthenticationProvider.java` ✏️ | Autenticación de desarrollo y test. **Cambio importante: ahora valida la contraseña con BCrypt**, igual que producción. Antes autenticaba con cualquier contraseña siempre que el email existiera. Además traduce `UsernameNotFoundException` a `BadCredentialsException`, para no filtrar qué emails están registrados |
| `authenticationProvider/ProdAuthenticationProvider.java` | Autenticación de producción con validación de password |
| `securityConfig/DevSecurityConfig.java` ✏️ | Configuración de seguridad de desarrollo: la tabla de permisos, CORS para `localhost:*`, sesión STATELESS, CSRF desactivado, y el registro de los filtros. Ahora también registra el `CustomAccessDeniedHandler` |
| `securityConfig/ProdSecurityConfig.java` ✏️ | Configuración equivalente para producción/test, con el mismo handler de 403 |

### Las reglas de acceso, tal como están escritas

El orden importa: Spring Security evalúa de arriba hacia abajo y se queda con **la primera regla que coincide**.

```java
// 1) PÚBLICO. Va primero a propósito: si no, /disponibles
//    caería en la regla de ADMIN de /api/v1/cocheras/** de abajo.
.requestMatchers("/api/v1/cocheras/disponibles",
                 "/forgot-password",
                 "/reset-password",
                 "/actuator/health").permitAll()

// 2) SOLO ADMIN
.requestMatchers("/users/**",
                 "/api/v1/usuarios/**",
                 "/register",
                 "/api/v1/cocheras/**").hasAuthority("ADMIN")

// 3) CUALQUIER USUARIO AUTENTICADO (los usan los dos dashboards)
.requestMatchers("/api/v1/reservas/**",
                 "/api/v1/vehiculos/**",
                 "/api/v1/visitantes/**",
                 "/login").authenticated()

// 4) TODO LO DEMÁS (Swagger, estáticos)
.requestMatchers("/**").permitAll()
```

El login sigue siendo HTTP Basic y devuelve un JWT válido 8 horas, con el email y un claim `authorities` que es **un string separado por comas** (`"USER,ADMIN"`), no un array — por eso el frontend hace `.split(",")`.

Sobre los códigos de error: **401** significa "no sé quién sos" (falta el token, venció, o la firma no da) y lo produce `CustomBasicAuthenticationEntryPoint`. **403** significa "sé quién sos y no te corresponde" y lo produce `CustomAccessDeniedHandler` 🆕. Los dos devuelven JSON con el mismo formato.

---

## `service/` + `service/impl/`

Lógica de negocio.

| Archivo | Qué hace |
|---|---|
| `IAuthService.java` / `impl/AuthService.java` | Registro, login y recuperación de contraseña |
| `ICocheraService.java` / `impl/CocheraService.java` | Gestión y disponibilidad de cocheras |
| `IReservaService.java` / `impl/ReservaService.java` | Lógica de reservas y validación de compatibilidad/disponibilidad |
| `IUserService.java` / `impl/UserService.java` ✏️ | Gestión administrativa de usuarios: listar, editar, activar y eliminar. Al eliminar, desvincula primero el visitante propio de la cuenta (si tiene uno) antes de borrarla |
| `IVehiculoService.java` / `impl/VehiculoService.java` ✏️ | Gestión de vehículos. Agregados `editar` y `eliminar` 🆕, los dos con `verificarPropietario`: un ADMIN pasa siempre; un USER solo si el `appUser` del visitante dueño coincide con el email del que pide, y si no, `AccessDeniedException`. `eliminar` además bloquea si el vehículo tiene reservas |
| `IVisitanteService.java` / `impl/VisitanteService.java` ✏️ | Gestión de visitantes, más `obtenerPropio(email)`, `crearPropio(email, dto)` y ahora `actualizarPropio(email, dto)` 🆕: el visitante carga y edita sus propios datos, vinculados a su cuenta |

### Comportamiento actual de alta de usuario

`AuthService.register()` crea los nuevos usuarios con:

```text
authority: USER
isActive: false
```

Un administrador puede posteriormente modificar sus authorities, activarlos o eliminarlos desde la gestión de usuarios.

> ⚠️ **Ojo si venías desarrollando de antes:** el perfil `dev` ya no autentica con cualquier contraseña. `DevAuthenticationProvider` ahora compara contra el hash BCrypt, igual que producción. Para entrar hace falta la contraseña real con la que se creó la cuenta.

---

# `src/main/resources/`

## Configuración

| Archivo | Qué hace |
|---|---|
| `application.yml` | Configuración base de Spring |
| `application-dev.yml` | Configuración específica de desarrollo; utiliza `ddl-auto: update` |
| `application-prod.yml` | Configuración para producción |
| `application-test.yml` | Configuración de tests con H2 |
| `banner.txt` | Arte ASCII mostrado al iniciar AparcAR |

## `db/changelog/`

| Archivo | Qué hace |
|---|---|
| `db.changelog-master.yaml` | Lista de migraciones Liquibase |
| `001-initial-schema.yaml` | Migración inicial actualmente vacía |
| `002-visitantes-vehiculos-cocheras-reservas.yaml` | Crea tablas de visitantes, vehículos, cocheras y reservas |
| `003-visitante-app-user.yaml` 🆕 | Agrega `visitantes.app_user_id` (único, sin FK física a propósito — ver nota abajo) para que un visitante pueda vincularse a su propia cuenta de login |

### Por qué `003` no tiene foreign key física hacia `app_users`

`app_users` no la crea Liquibase — la crea Hibernate con `ddl-auto: update`, que corre **después** de Liquibase. Una FK en `003` hacia esa tabla se rompe en cualquier base nueva (los tests con H2, o el primer `docker compose up` de otra persona) porque `app_users` todavía no existe cuando corre esta migración. Se detectó al escribir los tests: pasaba en la Postgres de desarrollo (porque esa tabla ya existía de arranques anteriores) pero fallaba siempre en H2. La relación la valida JPA (`@OneToOne` en `Visitante.java`), no la base.

---

# `src/test/java/com/aparcar/api/`

Convención de esta sección: 🆕 = clase de test agregada al sumar cobertura de login/roles/dashboards. El resto ya existía.

| Archivo | Qué hace |
|---|---|
| `AparcarApiApplicationTests.java` | Comprueba que el contexto Spring pueda iniciar |
| `component/OTPCleanupTests.java` | Tests de limpieza de OTP |
| `component/RevokedUserCacheTests.java` | Tests del cache de JWT revocados |
| `component/SpringEmailSenderTests.java` | Tests del envío de emails |
| `config/IntegrationTests.java` | Configuración reusable para tests de integración (MockMvc + Spring completo) |
| `config/SynchronousAsyncConfig.java` | Ejecuta tareas async de forma síncrona durante tests |
| `config/UnitTests.java` | Configuración reusable de Mockito |
| `config/WebClientTestConfig.java` | Configuración de WebClient para tests |
| `filters/JWTGeneratorFilterTests.java` 🆕 | **Caja blanca.** Prueba el filtro que arma el JWT directamente (mocks, sin Spring): genera `Authorization: Bearer ...` con email/authorities correctos solo si hay autenticación, y solo en `/login` |
| `filters/RateLimitFilterTests.java` 🆕 | **Caja blanca.** Prueba el limitador de intentos directamente: deja pasar las primeras 5 requests por IP y bloquea (429) la 6ta; IPs distintas tienen buckets independientes |
| `integration/AuthControllerTests.java` ✏️ | Tests de `/register`, `/login`, `/forgot-password`, `/reset-password`. Actualicé `registerValidatesInput` y `registerCreatesInactiveUser` porque `/register` pasó a requerir rol ADMIN (antes eran públicos y quedaron rotos por ese cambio); agregué los casos 401 (anónimo) y 403 (rol USER) |
| `integration/CocheraControllerTests.java` | **Caja negra.** CRUD completo de `/api/v1/cocheras`: seguridad (401/403), validaciones, alta/edición/borrado y `/disponibles` de punta a punta |
| `integration/DashboardAccessSecurityTests.java` 🆕 | **Caja negra.** Matriz de qué rol puede pegarle a qué endpoint: `/api/v1/visitantes`, `/vehiculos` y `/reservas` exigen solo estar autenticado (los usan ambos dashboards, sin importar el rol), `/api/v1/usuarios` exige ADMIN, `/api/v1/cocheras/disponibles` es público |
| `integration/LoginFlowTests.java` ✏️ | **Caja negra**, contra un servidor real embebido (no MockMvc — ver el porqué en el comentario de la clase). Login real con HTTP Basic: verifica el JWT devuelto (email, authorities) y los 401. Actualizado: ahora comprueba que **una contraseña incorrecta devuelve 401 también en dev/test**, porque el `DevAuthenticationProvider` pasó a validarla |
| `integration/ReservaControllerTests.java` 🆕 | **Caja negra.** Reglas de negocio de `/api/v1/reservas` contra DB real (no mocks): vehículo que no pertenece al visitante, incompatibilidad de tipos, doble reserva del mismo día, cochera ACCESIBLE acepta cualquier vehículo |
| `integration/UserControllerTests.java` ✏️ | Tests de `/users/**` y `/api/v1/usuarios/**`. Agregué los casos de `PUT /api/v1/usuarios/{id}` (actualiza campos, 404 si no existe, 401 anónimo) |
| `integration/VehiculoControllerTests.java` ✏️ | **Caja negra.** `/api/v1/vehiculos`: formato de patente, normalización a mayúsculas, patente/visitante duplicado o inexistente, filtro por `visitanteId`, y los casos nuevos de `PUT`/`DELETE` con control de propietario (403 si no sos el dueño) |
| `integration/VisitanteControllerTests.java` ✏️ | **Caja negra.** `/api/v1/visitantes`, con foco en `/me` (el visitante carga y edita su propio perfil): 404 sin perfil, alta, documento duplicado, cuenta que ya tiene un perfil cargado, y la actualización por `PUT /me` |
| `security/AppUserDetailsServiceTests.java` 🆕 | **Caja blanca.** El puente AppUser → UserDetails: mapea authorities correctamente, lanza `UsernameNotFoundException` si el email no existe |
| `security/authenticationProvider/DevAuthenticationProviderTests.java` ✏️ | **Caja blanca.** Reescrito: ya no documenta el viejo comportamiento inseguro. Ahora verifica que dev/test **valida la contraseña contra el hash** y rechaza con `BadCredentialsException` tanto si no matchea como si el email no existe |
| `security/authenticationProvider/ProdAuthenticationProviderTests.java` 🆕 | **Caja blanca.** El que sí valida contraseña (perfil prod real): rechaza con `BadCredentialsException` tanto si la contraseña no matchea como si el usuario no existe (para no filtrar cuáles emails están registrados) |
| `service/AuthServiceTests.java` | Tests unitarios de `AuthService` |
| `service/CocheraServiceTests.java` | Tests de cocheras, incluye la cancelación automática de reservas al deshabilitar una cochera |
| `service/ReservaServiceTests.java` | Tests unitarios de reservas (con mocks): mismas reglas que `ReservaControllerTests` pero aisladas del repositorio |
| `service/UserServiceTests.java` ✏️ | Tests de gestión de usuarios. Agregué los casos de `deleteUser`: desvincula el visitante propio antes de borrar la cuenta (evita romper la FK `fk_visitante_app_user`), y no hace nada si no hay ninguno vinculado |
| `service/VehiculoServiceTests.java` ✏️ | Tests de vehículos. Agregados los casos de `editar` y `eliminar`: control de propietario, patente duplicada al editar, y el bloqueo al borrar un vehículo con reservas |
| `service/VisitanteServiceTests.java` ✏️ | Tests de visitantes, incluido todo el flujo `/me`: `obtenerPropio`, `crearPropio` y `actualizarPropio` — 404 sin perfil, cuenta que ya tiene uno, documento duplicado, alta correcta vinculada a la cuenta, y edición del teléfono/email propios |

## Sobre `LoginFlowTests` y el bug de `getServletPath()` en MockMvc

Escribiendo estos tests encontré algo importante para quien toque `RateLimitFilter`, `JWTValidationFilter` o `JWTGeneratorFilter`: los tres deciden si aplicarse mirando `request.getServletPath()`. En el dispatch simulado de MockMvc ese valor **no coincide** con el de un despliegue real (queda vacío), así que esos filtros nunca se activan bajo MockMvc — silencioso, sin error, simplemente no hacen nada. Por eso `LoginFlowTests` corre contra un servidor embebido real (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTemplate`) en vez de `MockMvc`: es la única forma de que estos tres filtros se ejecuten de verdad durante el test.

No es un bug de producción — contra la app real (Docker) ya confirmamos a mano que el JWT y el rate limiting funcionan bien — es una limitación del entorno de test que vale la pena tener en cuenta antes de confiar en un test de estos tres filtros hecho con MockMvc.

---

# `aparcar-front/` — Frontend (Next.js)

## Raíz del frontend

| Archivo | Qué hace |
|---|---|
| `.dockerignore` | Archivos que no se copian al construir la imagen |
| `.env` / `.env.example` | Configura `NEXT_PUBLIC_API_BASE_URL` |
| `.gitignore` | Ignora `node_modules/`, `.next/` y otros generados |
| `.pre-commit-config.yaml` | Ejecuta validaciones antes de commits |
| `Dockerfile` | Construcción y ejecución del frontend con Docker |
| `README.md` | Documentación del frontend |
| `Taskfile.yml` | Comandos de desarrollo/build |
| `docker-compose.yml` | Levanta el frontend en el puerto 3000 |
| `eslint.config.mjs` | Configuración ESLint |
| `jsconfig.json` | Define alias `@/` |
| `kickstart.md` | Guía de instalación |
| `next.config.mjs` | Configuración de Next.js |
| `package.json` / `package-lock.json` ✏️ | Dependencias. Agregados `npm test` (`vitest run`) y `npm run test:watch` (`vitest`) |
| `postcss.config.mjs` | Configuración Tailwind CSS 4 |
| `vitest.config.mjs` 🆕 | Configuración de Vitest: entorno `jsdom`, alias `@/`, y el archivo de setup de `test/` |

---

# `app/` — páginas y contenido

| Archivo / carpeta | Qué hace |
|---|---|
| `api.jsx` ✏️ | Instancia Axios compartida; configura base URL e inyecta JWT en requests autenticados. El interceptor de request **no pisa** un `Authorization` ya seteado a mano (ej. el `Basic` de `/login`) — antes lo pisaba con el `Bearer` de una cookie vieja, causando que el login pidiera el usuario y contraseña dos veces |
| `favicon.ico` | Ícono de la aplicación |
| `globals.css` | Estilos globales y Tailwind |
| `layout.js` | Layout global y `<Toaster />` de Sonner |
| `page.jsx` 🆕 | **Reemplaza a `page.js`** (que se eliminó). Landing pública de AparcAR: presenta el producto, con íconos SVG propios y menú hamburguesa en mobile. Si ya hay sesión activa, redirige sola al dashboard que corresponde al rol |
| `not-found.js` 🆕 | Página 404 propia de Next.js, para cuando alguien escribe una ruta que no existe |
| `login/page.jsx` ✏️ | Login del personal interno; genera sesión y redirige según rol a `dashboard-admin` o `dashboard-user`. Ahora pasa `validateStatus` a Axios para que un 401 **no** se trate como error: así el interceptor de respuesta no borra la cookie ni redirige, y la pantalla puede mostrar "Credenciales incorrectas" sin recargarse |
| `recover-password/page.jsx` | Solicitud de OTP |
| `reset-password/page.jsx` | Cambio de contraseña mediante OTP |
| `unauthorized/page.jsx` | Página mostrada cuando el usuario no tiene permisos |

---

## `app/dashboard-admin/`

Sección para usuarios con rol `ADMIN`.

| Archivo | Qué hace |
|---|---|
| `page.jsx` ✏️ | Entrada del dashboard ADMIN, protegida con `requireAuth(["ADMIN"])`. Server Component: solo valida el rol y arma la barra de navegación (`/cocheras`, `/usuarios`, `LogoutButton`). El contenido lo delega en `PanelOperativo` |
| `PanelOperativo.jsx` 🆕 | Agrupa las tres secciones operativas del panel: la cuadrícula de ocupación, el alta de visitantes y **el formulario de reservas**. Existe como componente de cliente aparte porque `page.jsx` es Server Component y no puede tener estado: acá vive el contador que le avisa a la cuadrícula que se creó una reserva y tiene que recargarse |
| `EstadoCocherasGrid.jsx` ✏️ | Cuadrícula visual de ocupación: agrupa las cocheras por tipo (motos, autos, remolques, accesibles) y marca cada una como libre/ocupada/deshabilitada comparando `/api/v1/cocheras` contra `/api/v1/cocheras/disponibles` del día. Acepta una prop `refreshKey` 🆕: cuando cambia, vuelve a pedir los datos, para no quedar mostrando una cochera como libre después de reservarla |
| `VisitantesContent.jsx` | Alta de visitante + vehículo hecha por el admin (formulario completo) |

### `app/dashboard-admin/cocheras/`

Módulo de gestión de cocheras.

| Archivo | Qué hace |
|---|---|
| `page.jsx` 🆕 | Ruta `/dashboard-admin/cocheras`; valida server-side rol `ADMIN` |
| `CocherasManagement.jsx` 🆕 | CRUD completo de cocheras: alta, edición (con confirmación al deshabilitar una cochera con reservas), baja (bloqueada si tiene reservas asociadas), y filtros por sector/tipo/estado |

### `app/dashboard-admin/usuarios/`

Módulo de gestión de usuarios internos.

| Archivo | Qué hace |
|---|---|
| `page.jsx` 🆕 | Ruta `/dashboard-admin/usuarios`; valida server-side que el usuario tenga authority `ADMIN` |
| `UserManagement.jsx` 🆕 | Interfaz interactiva para listar, crear, editar, asignar roles, activar y eliminar usuarios |

`UserManagement.jsx` reutiliza:

- `app/api.jsx` para todas las llamadas HTTP;
- `react-hook-form` para formularios;
- `zod` para validaciones;
- `sonner` para notificaciones;
- los endpoints ya existentes de activación y eliminación;
- los nuevos endpoints de listado y edición.

No implementa lógica propia de autenticación ni acceso directo a PostgreSQL.

---

## `app/dashboard-user/`

Sección destinada a usuarios internos con rol `USER`.

| Archivo | Qué hace |
|---|---|
| `page.jsx` ✏️ | Entrada del dashboard USER, protegida con `requireAuth(["USER"])`. Combina "Mis datos" y "Nueva reserva" en una sola página, más el `LogoutButton` 🆕 |
| `MiPerfilContent.jsx` ✏️ | El propio visitante carga sus datos (nombre, documento, teléfono, email) una sola vez, vinculados a su cuenta (`/api/v1/visitantes/me`), y gestiona sus vehículos. Ampliado 🆕: ahora también **edita su teléfono y email** (`PUT /me`) y **edita o elimina sus vehículos** (`PUT` / `DELETE /api/v1/vehiculos/{id}`, con confirmación antes de borrar) |
| `ReservasContent.jsx` | **Se mudó a `components/`** ✏️, porque ahora lo usan los dos dashboards. Ver esa sección |

---

## `public/`

| Archivo | Qué hace |
|---|---|
| `Logo.jpeg` 🆕 | Logo de AparcAR utilizado actualmente en la pantalla de login |

---

# `components/`

| Archivo | Qué hace |
|---|---|
| `ProtectedRoute.jsx` ✏️ | Wrapper client-side para proteger rutas según autenticación/rol. Refactorizado: se eliminó el estado `isReady` y la decisión de renderizar se deriva directo de `isHydrated + isAuthenticated + hasRequiredRole`, lo que evita mostrar contenido un instante antes de redirigir |
| `LogoutButton.jsx` 🆕 | Botón "Cerrar sesión": llama a `logout()` del store (que borra la cookie JWT y limpia el estado) y navega a `/login` con `router.replace`, para que el botón Atrás no vuelva al dashboard |
| `ReservasContent.jsx` 🆕 | **Movido desde `app/dashboard-user/`.** Alta de reserva por patente: se escribe/elige la patente (autocompletado nativo) y se resuelven solos el visitante y el tipo de vehículo. Vuelve a pedir la lista de vehículos al hacer foco en el campo, por si se cargó uno recién más arriba en la misma página. Sirve igual para los dos roles porque busca sobre el catálogo completo, sin filtrar por la cuenta que mira. La prop opcional `onReservaCreada` la usa el panel admin para refrescar la cuadrícula. Sus `id` de formulario van prefijados con `reserva-` para no chocar con los del alta de visitante, que se renderiza en la misma página |

Las pantallas nuevas basadas en Server Components utilizan preferentemente `requireAuth()` desde `utils/serverAuth.js`.

---

# `scripts/`

| Archivo | Qué hace |
|---|---|
| `entrypoint.sh` | Inyecta variables `NEXT_PUBLIC_*` cuando el frontend corre en Docker |
| `entrypoint_local.sh` | Variante para desarrollo local |

---

# `store/`

| Archivo | Qué hace |
|---|---|
| `authStore.js` | Estado global de autenticación con Zustand; guarda JWT y expone funciones de sesión |

---

# `utils/`

| Archivo | Qué hace |
|---|---|
| `env.js` | Resuelve variables públicas tanto en desarrollo como en Docker |
| `serverAuth.js` | Protección server-side mediante `requireAuth(allowedRoles)` |

Ejemplo:

```javascript
await requireAuth(["ADMIN"]);
```

Si el JWT no existe, redirige al login. Si existe pero no contiene alguno de los roles requeridos, redirige a `/unauthorized`.

---

# `test/` — tests automatizados (frontend)

🆕 Toda la carpeta es nueva: no existía testing en el frontend antes de esta sesión. Usa **Vitest + React Testing Library + jsdom**, más `axios-mock-adapter` para probar los interceptores de `api.jsx` sin red real. Corre con `npm test` (una vez) o `npm run test:watch`.

Convención: `test/` refleja la estructura de `app/`, `store/` y `utils/` (misma idea que `src/test/java/...` reflejando `src/main/java/...` en el backend).

| Archivo | Qué prueba |
|---|---|
| `setup.js` | Carga los matchers de `jest-dom`, limpia el DOM y las cookies después de cada test |
| `page.test.jsx` 🆕 | `app/page.jsx`: muestra la landing si no hay sesión, redirige según el rol si la hay, y el menú hamburguesa abre y cierra en mobile |
| `components/LogoutButton.test.jsx` 🆕 | `components/LogoutButton.jsx`: cierra la sesión y navega a `/login` |
| `api.test.jsx` | Interceptores de `app/api.jsx`: baseURL, inyección del Bearer desde la cookie, **que no pise un Authorization ya seteado a mano** (regresión del bug del login doble), y el manejo de 401 (borra cookie + redirige) |
| `login/page.test.jsx` | `app/login/page.jsx`: validaciones, Basic Auth armado correctamente, redirección según rol (ADMIN vs USER), errores del backend |
| `store/authStore.test.js` | `store/authStore.js`: decodificación de authorities del JWT, cookie, expiración, `logout`/`checkAuth` |
| `utils/env.test.js` | `utils/env.js`: prioridad de `window.__ENV` sobre el valor de build |
| `dashboard-admin/EstadoCocherasGrid.test.jsx` | Agrupación por tipo, cálculo de ocupadas/libres/deshabilitadas, estado de carga y error |
| `dashboard-admin/VisitantesContent.test.jsx` | Alta de visitante + vehículo (dos POST encadenados), validaciones, errores de duplicados |
| `dashboard-admin/cocheras/CocherasManagement.test.jsx` ✏️ | CRUD completo: filtros, alta, edición (con `window.confirm` al deshabilitar), baja (con confirmación), y la navegación de regreso al panel |
| `dashboard-admin/usuarios/UserManagement.test.jsx` ✏️ | Alta de usuario, activar, editar roles, eliminar (con confirmación), y la navegación de regreso al panel |
| `components/ReservasContent.test.jsx` ✏️ | Resolución de visitante/vehículo por patente, cochera deshabilitada hasta tener match, **regresión del bug de caché de vehículos al hacer foco**, envío de la reserva. Se movió junto con el componente |
| `dashboard-admin/PanelOperativo.test.jsx` 🆕 | **Regresión del bug de la reserva que no se agregaba**: que el panel admin incluya el formulario de reservas, que un admin pueda crear una resolviendo el visitante por patente, y que la cuadrícula pase de "0/1 ocupadas" a "1/1 ocupadas" sin recargar |
| `dashboard-user/MiPerfilContent.test.jsx` ✏️ | Autoregistro del visitante (`/me`), alta de vehículo propio, validaciones y errores del backend, más los casos nuevos 🆕 de editar el perfil (`PUT /me`), editar un vehículo y eliminarlo con confirmación |

El detalle de qué casos prueba cada archivo (front y back) está en `TESTS.md`, en la raíz del repo: **36 archivos y 251 tests** en total (160 del backend, 91 del frontend). Los dos suites corren solas en cada pull request, vía `.github/workflows/ci.yml`.

---

# Flujo de autenticación actual

## Login

```text
/login
   ↓
HTTP Basic Auth
   ↓
Backend Spring Security
   ↓
JWT con email + authorities
   ↓
Frontend guarda JWT
   ↓
   ├── ADMIN → /dashboard-admin
   └── USER  → /dashboard-user
```

---

## Gestión de usuarios ADMIN

```text
/dashboard-admin/usuarios
          ↓
requireAuth(["ADMIN"])
          ↓
UserManagement.jsx
          ↓
app/api.jsx
          ↓
Spring Boot API
          ↓
PostgreSQL
```

Funciones disponibles:

```text
Listar usuarios
Crear usuario
Editar nombre
Editar teléfono
Editar authorities
Activar usuario
Eliminar usuario
```

Los usuarios nuevos se crean inicialmente como:

```text
USER
INACTIVO
```

y luego pueden ser gestionados por un administrador.

---

# Tablas que existen actualmente en PostgreSQL

| Tabla | Origen | Columnas principales | Entidad |
|---|---|---|---|
| `visitantes` | Liquibase | `id`, `nombre`, `documento`, `telefono`, `email`, `app_user_id` (único, sin FK física) | `Visitante.java` |
| `vehiculos` | Liquibase | `id`, `patente`, `tipo`, `visitante_id` | `Vehiculo.java` |
| `cocheras` | Liquibase | `id`, `numero`, `sector`, `tipo`, `estado` | `Cochera.java` |
| `reservas` | Liquibase | `id`, `fecha`, `visitante_id`, `vehiculo_id`, `cochera_id`, `estado`, `fecha_creacion` | `Reserva.java` |
| `app_users` | Hibernate (`ddl-auto: update`) | `id`, `nombre`, `email`, `password`, `telefono`, `is_active` | `AppUser.java` |
| `app_user_authorities` | Hibernate (`ddl-auto: update`) | `user_id`, `authority` | `AppUser.authorities` |
| `otp_codes` | Hibernate (`ddl-auto: update`) | `id`, `token`, `user_id`, `expires_at`, `used` | `OneTimePassword.java` |
| `databasechangelog` / `databasechangeloglock` | Liquibase | Internas de Liquibase | — |

---

# Sobre `db/changelog/001-initial-schema.yaml`

La migración `001-initial-schema.yaml` continúa vacía.

Actualmente las tablas relacionadas con autenticación:

```text
app_users
app_user_authorities
otp_codes
```

se crean/actualizan automáticamente en desarrollo mediante:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

configurado en `application-dev.yml`.

Las entidades de autenticación todavía no cuentan con una migración Liquibase propia.

En el futuro, si el equipo decide unificar todo el esquema bajo Liquibase, se deberá generar una migración correspondiente antes de retirar `ddl-auto: update`.
