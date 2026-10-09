# Mapa de archivos

## Trazabilidad de reservas

- `entity/reserva/ReservaMovimiento`, `ReservaAccion` y `ReservaMotivoCancelacion`:
  historial de altas, cancelaciones y deshabilitaciones. Cada movimiento conserva
  fecha e identidad del autor (ID, nombre, email y rol al actuar), incluso si su
  cuenta cambia o se elimina. `Reserva` mantiene la relación y una versión para
  impedir que bajas concurrentes sobrescriban el registro.
- `ReservaService`: registra al usuario autenticado en el alta y la cancelación.
  Los DTO incluyen `motivoCancelacion`; `historial` se envía únicamente a ADMIN,
  tanto al consultar como al crear o cancelar. Se conserva el filtro de reservas
  propias para USER. `ReservaRepository` carga el historial junto con el listado.
- `VisitanteController` / `IVisitanteService` / `VisitanteService`: el alta con
  vehículo y reserva propaga el email del administrador autenticado, conservando
  al visitante nuevo como ocupante.
- `CocheraController` / `ICocheraService` / `CocheraService`: al deshabilitar una
  cochera, cada reserva confirmada recibe un movimiento de deshabilitación con
  el admin responsable. La cochera, las reservas y sus movimientos se guardan en
  una misma transacción. Las reservas ya canceladas conservan su motivo original.
- `010-trazabilidad-reservas.yaml`: crea `reserva_movimientos` y agrega `version`
  a `reservas`. Liquibase la aplica al arrancar el backend actualizado. No completa
  autores ni motivos de reservas anteriores con suposiciones.
- Frontend `components/ReservasContent.jsx`: diferencia «Cancelaste esta reserva»,
  «Cancelada por administración» y «Deshabilitada por administración». Conserva
  `CANCELADA` si una reserva histórica no tiene motivo registrado.
- Frontend `components/HistorialReserva.jsx`: historial desplegable exclusivo del
  listado admin, junto al ocupante y su vehículo. Muestra acción, autor y fecha
  en horario argentino; identifica altas anteriores sin autor registrado.

Las rutas Java de esta sección son relativas a `aparcar-api-back/src/main/java/com/aparcar/api/`.

## Mi cuenta y nombre del estacionamiento

- `components/AccountMenu.jsx` y `DashboardHeader.jsx`: acceso compartido a
  **Mis datos** y **Cerrar sesión** en todas las pantallas de ambos roles.
  Reutiliza `LogoutButton`; admite teclado, Escape, clic fuera y pantallas móviles.
- `app/dashboard-admin/perfil/page.jsx` y `app/dashboard-user/perfil/page.jsx`:
  rutas protegidas con `requireAuth`, con acceso de regreso al panel.
- `components/ProfileSettings.jsx`: perfil responsive con identidad, contacto
  y cambio de contraseña. Reutiliza Axios, React Hook Form, Zod, Sonner y los
  estilos de ambos temas. Permite descartar cambios, reintentar una carga fallida
  y conserva la edición si falla el guardado; bloquea controles mientras guarda.
  USER conserva email, teléfono y declaración de discapacidad; ADMIN puede editar
  además su documento y el nombre de su estacionamiento.
- `components/ParkingHeading.jsx`: únicamente el nombre del estacionamiento,
  centrado sobre el panel operativo, con tipografía más pequeña y de peso medio.
  Sin icono, subtítulo ni enlace adicional. Usa **Mi estacionamiento** si aún no
  se configuró el nombre; los nombres largos se ajustan al ancho disponible.
- `app/dashboard-user/MiPerfilContent.jsx`: muestra únicamente la gestión de
  vehículos, sin resumen personal ni consulta a `/api/v1/visitantes/me`.
  Conserva el aviso al formulario de reservas cuando cambian los vehículos.
  Los datos personales se consultan y editan desde **Mi cuenta → Mis datos**.
  Al volver del perfil, el panel vuelve a consultar vehículos y disponibilidad.
- Backend `Visitante`, `VisitanteUpdateDto`, `VisitanteResponseDto` y
  `VisitanteService`: reutilizan `GET/PUT /api/v1/visitantes/me`. El servidor valida
  campos exclusivos de ADMIN, normaliza email/documento, evita duplicados y
  conserva documento, nombre del estacionamiento y discapacidad si no se envían.
  El teléfono se puede vaciar explícitamente. No se cambian roles ni contraseñas
  al modificar el documento.
- `009-nombre-estacionamiento.yaml`: columna opcional de hasta 100 caracteres
  **por cuenta administradora**, sin modificar las cuentas existentes. Se aplica
  con Liquibase al arrancar el backend actualizado; no requiere carga inicial.
- `IRevokedUserCache`, `RevokedUserCache` y filtros JWT: al cambiar de email se
  invalidan las sesiones anteriores y el frontend pide iniciar sesión nuevamente.
  La fecha de emisión permite volver a usar un email previo sin reactivar tokens
  antiguos; la revocación administrativa existente conserva su comportamiento.

Las rutas de frontend de esta sección son relativas a `aparcar-front/`.

## Gestión de tarifas

- `aparcar-front/app/dashboard-admin/tarifas/page.jsx`: nueva pantalla protegida
  para ADMIN, con el encabezado y la navegación existentes.
- `aparcar-front/app/dashboard-admin/tarifas/TarifasManagement.jsx`: edición de
  los 16 precios en tarjetas responsive, usando `ui-card`, `ui-input`, React
  Hook Form y Zod. Acepta coma o punto decimal, valida los campos, bloquea el
  formulario durante el guardado y conserva los cambios ante errores.
- `aparcar-front/app/dashboard-admin/page.jsx` / `components/DashboardIcon.jsx`:
  acceso “Gestionar tarifas”; la navegación permite envolver sus enlaces.
- `aparcar-front/hooks/useCotizacion.js`: consulta el importe al backend al
  cambiar categoría o franja; invalida inmediatamente la cotización anterior,
  ignora respuestas tardías y permite reintentar.
- `aparcar-front/components/CotizacionReserva.jsx` / `utils/tarifas.js`:
  resumen compartido con total en ARS, categoría, desglose y estados de carga/error.
- `ReservasContent.jsx` / `VisitantesContent.jsx`: muestran el precio antes de
  confirmar en USER, ADMIN y alta operativa. Envían `precioEsperado` para detectar
  cambios de tarifa mientras el formulario estaba abierto. El historial muestra
  el importe guardado, o “Sin importe registrado” para reservas anteriores.
- Backend `entity/reserva/Tarifa.java` / `repository/TarifaRepository.java`:
  cuatro precios decimales por categoría y versión para evitar sobrescribir
  ediciones concurrentes. No se asignan precios predeterminados de producción.
- `dto/reserva/TarifaDto.java`, `TarifasRequestDto.java` y
  `CotizacionResponseDto.java`: contrato de edición de la matriz y cotización.
- `service/ITarifaService.java` / `service/impl/TarifaService.java`: guardado
  transaccional de las cuatro categorías y cálculo con `BigDecimal`. Suma
  jornadas completas de 24 h, medias jornadas de 12 h, horas y fracciones de
  15 min restantes, en ese orden; no busca la combinación más económica.
- `controller/TarifaController.java` / ambas configuraciones de seguridad:
  `GET /api/v1/tarifas` y `GET /api/v1/tarifas/cotizacion?tipo=...&desde=...&hasta=...`
  para ADMIN/USER; `PUT /api/v1/tarifas` exclusivamente ADMIN. El PUT recibe
  `{ tarifas: [{ tipo, hora, fraccion, mediaJornada, jornadaCompleta, version }] }`,
  incluyendo las cuatro categorías una sola vez y las versiones recibidas por GET.
- `ReservaService` / `Reserva` / `ReservaResponseDto`: el servidor determina
  la categoría según la cochera validada y guarda `precioTotal` y `tipoTarifa`
  al crear. Listar, cancelar o finalizar no recalcula importes. `VisitanteService`
  propaga `precioEsperado` del alta operativa; si cambió el precio, revierte la
  cuenta, el vehículo y la reserva mediante la transacción existente.
- `008-tarifas.yaml` / `db.changelog-master.yaml`: tabla `tarifas` y columnas
  históricas de reserva, inicialmente nulas para las reservas anteriores.
- Nuevos tests: `TarifaServiceTests`, `TarifaControllerTests`,
  `TarifaMigracionTests`, `TarifasManagement.test.jsx`, `useCotizacion.test.jsx`
  y `ReservasTarifas.test.jsx`; `src/test/resources/tarifas-test.sql` provee
  precios solo para las pruebas de integración que crean reservas.

**Categorías y puesta en marcha:** Auto, Moto, Accesible y Carga. Se conserva
el modelo existente: Accesible es un tipo de cochera, por lo que se aplica su
tarifa al elegir esa cochera, respetando la declaración de discapacidad del
visitante. El administrador debe configurar los 16 precios antes de crear
nuevas reservas. Un cero explícito indica un período sin cargo; un precio sin
configurar impide cotizar y reservar. Las reservas anteriores permanecen intactas.

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

- = archivo agregado desde la versión anterior de este documento.
- = archivo existente que fue modificado desde entonces.

---

## Qué cambió en esta tanda

Resumen de lo nuevo, para no tener que leer todo el archivo buscando las marcas. Las marcas 🆕/✏️ de más abajo corresponden **solo** a esta tanda.

**Cocheras accesibles** 🆕

- Cada visitante declara si tiene una discapacidad: nueva columna `visitantes.tiene_discapacidad` (`007-visitante-tiene-discapacidad.yaml` 🆕), `false` por defecto, así que las cuentas existentes quedan "sin declarar".
- Se puede declarar al registrarse (`POST /register`), en el alta que hace el admin, o después desde el propio perfil (`PUT /api/v1/visitantes/me`). Es una declaración de la persona: la puede marcar y desmarcar cuando quiera. Si el `PUT` no manda el campo, no se toca.
- **La regla vive en el backend**: `ReservaService` rechaza con 400 la reserva de una cochera ACCESIBLE si el **dueño de la reserva** no tiene la declaración. Cuenta la del dueño, no la de quien la carga: un ADMIN que reserva para otra persona queda sujeto a lo que declaró esa persona.
- `GET /api/v1/cocheras/disponibles` sigue siendo público, pero ya no le ofrece las ACCESIBLE a un visitante logueado sin la declaración: se las rechazaría al reservar. Anónimos y ADMIN las siguen viendo todas.
- En el front, el visitante tiene el checkbox en "Editar mis datos" (y una insignia en sus datos), el admin lo tiene en el alta de visitante, y el formulario de reservas del admin oculta las ACCESIBLE cuando el dueño del vehículo no las puede usar. En todos los casos, si el backend rechaza, su mensaje llega tal cual.

**Alta de cocheras por planta** 🆕

- Reemplaza, en la pantalla, al alta en lote fila por fila (número, sector, tipo y estado tipeados a mano en cada fila).
- Ahora se elige **un sector** (de la lista real de `/sectores` o uno nuevo) y **cuántas cocheras de cada tipo**. El número lo genera el backend: `POST /api/v1/cocheras/alta-por-planta` 🆕.
- Esquema de numeración (`NumeracionCocheras.java` 🆕): prefijo fijo por tipo (AUTO `A`, MOTO `M`, CARGA `C`, ACCESIBLE `AC`) + guion + secuencial con al menos 2 dígitos (`A-05`, `AC-12`, `A-100`). Cada tipo sigue desde el **máximo** existente de su prefijo. Los números con otro formato (datos viejos) se ignoran para el cálculo.
- Todo-o-nada: si algo falla no se crea ninguna. Si dos altas simultáneas generan el mismo número, la restricción UNIQUE de `cocheras.numero` hace fallar a la segunda, que responde 400 pidiendo reintentar. Para un único predio no se justificó nada más complejo (bloqueos, secuencias).
- El admin ve exactamente qué números quedaron asignados, agrupados por tipo, con los datos que devolvió el backend.
- El endpoint viejo `POST /api/v1/cocheras/bulk` **sigue existiendo** en el backend, pero el front ya no lo usa. Se puede borrar cuando se confirme que nada más lo llama.

**Errores**

- `ProdExceptionHandler` ahora responde 400 (y no 500) ante un JSON mal formado o con un valor imposible de convertir, igual que ya hacía el de desarrollo. Por ejemplo, un tipo de cochera que no existe en el alta por planta.

---

## Tanda anterior: reservas por franja horaria

**Reservas por franja horaria**

- Una reserva dejó de ser "por día" y pasó a ser un rango `[desde, hasta)`. Se puede reservar el tiempo que se quiera, incluidos varios días.
- **Las reservas se toman en bloques de 15 minutos.** La regla se valida en el backend y no solo en la pantalla: si viviera únicamente en la UI, cualquier cliente que pegue a la API podría reservar de 14:07 a 15:23. En el formulario, los campos `Desde` y `Hasta` declaran `step` de 15 min, así que las flechas del campo se mueven de a un bloque y el navegador marca como inválido cualquier horario intermedio. Lo que se escribe se lleva al bloque **más cercano** (`redondearAlBloqueLocal` 🆕) **al salir del campo**, no en cada tecla ✏️: escribir los minutos pasa por estados intermedios —tipear el `3` de `:30` deja `:03`— y acomodarlos en el momento los borraba, así que no se podía escribir ningún minuto. Se redondea y no se trunca porque quien escribe 18:23 está más cerca de querer 18:30 que 18:15; el **arranque por defecto** sí sigue truncando hacia abajo, por el motivo contrario: cubrir los minutos que ya pasaron. La misma regla se aplica al consultar disponibilidad y al enviar, para que hacia el backend nunca salga un horario fuera de la grilla.
- El arranque por defecto es el **bloque en curso**, redondeando hacia abajo: si son las 14:07 arranca 14:00, no 14:15, porque si no los ocho minutos en los que el auto ya está estacionado quedarían sin cubrir.

**El sistema detecta la modalidad**

- `ModalidadReserva.java` clasifica cada reserva en **por franja horaria**, **media jornada** (12 h o más) o **jornada completa** (24 h o más), deducido de la duración y no del horario de arranque: media jornada es medio día de estadía, empiece a las 8 o a las 15.
- **No se guarda en la base**: es una lectura de la franja. Si el predio decide que media jornada son 6 h y no 12, las reservas viejas se reinterpretan solas en vez de quedar etiquetadas con un criterio viejo.
- Viaja en `ReservaResponseDto`, así que el listado la muestra sin volver a deducirla — si la calculara por su cuenta, backend y pantalla podrían discrepar al cambiar un umbral.
- `AtajosJornada.jsx` ofrece las dos jornadas como botones: en vez de contar horas a mano, se elige **media jornada** o **jornada completa** y el campo `Hasta` salta a 12 h o 24 h después del inicio. Caen **justo** en el umbral que usa el backend, así que la reserva queda clasificada con la modalidad que anuncia el botón; si un atajo dejara 11 h 45, el listado la mostraría como franja horaria y el botón estaría mintiendo.
- El atajo mueve solo el `Hasta`: el inicio lo sigue eligiendo quien reserva, porque media jornada es medio día de estadía, empiece a las 8 o a las 15.
- **Toda la regla de "no se pisan" es una sola condición**, en `ReservaRepository`: `r.desde < :hasta AND r.hasta > :desde`. Es el test estándar de intersección de intervalos semiabiertos: cubre los cuatro casos de superposición y deja pasar el borde que hay que permitir — si una reserva termina justo cuando arranca la siguiente, no se pisan y la cochera se puede volver a entregar en ese instante.
- **Las cocheras se liberan solas.** Sale de lo mismo: una reserva vencida ya no intersecta ningún rango futuro, así que deja de ocupar en el instante exacto en que termina, sin depender de que corra ninguna tarea.
- `ReservasVencidas.java` marca las vencidas como `FINALIZADA`, pero es **solo informativo**: mantiene el listado legible. Si el proceso se cae un fin de semana, nadie se queda sin poder reservar.
- Un mismo vehículo no puede ocupar dos cocheras a la vez, aunque las dos estén libres.
- El formulario arranca el "desde" en el momento actual y deja elegir día y hora del "hasta", en los dos dashboards.

**⚠️ Zona horaria**

- `ZonaHorariaConfig.java` fija la zona de la aplicación (`app.zona-horaria`, por defecto `America/Argentina/Buenos_Aires`).
- Hace falta porque las franjas se guardan como `LocalDateTime`, un reloj de pared sin zona. Es el modelo correcto para un predio —"de 10 a 12" son las 10 y las 12 *del lugar*— pero solo funciona si backend y navegador coinciden en cuál es ese reloj. Sin esto el contenedor arrancaba en UTC mientras el navegador mandaba hora local: una reserva de las 16 a las 17 llegaba a un servidor que creía que eran las 19 y la rechazaba por "terminada en el pasado".

**Integración continua**

- `.github/workflows/ci.yml`: GitHub Actions corre los checks en cada pull request a `main` o `dev`.

**Seguridad**

- ⚠️ `DevAuthenticationProvider` **ahora valida la contraseña con BCrypt**, igual que producción. Antes autenticaba a cualquiera con solo existir el email. Si venías probando con contraseñas inventadas, ya no funciona.
- `CustomAccessDeniedHandler`: los 403 ahora devuelven JSON con el mismo formato que los 401, en vez de una página de error de Spring.

**Gestión de datos propios (dashboard USER)**

- `PUT /api/v1/visitantes/me`: el visitante edita su teléfono y su email.
- `PUT /api/v1/vehiculos/{id}` y `DELETE /api/v1/vehiculos/{id}`: editar y borrar vehículos, con control de propietario (un USER solo toca los suyos; un ADMIN, todos). No se puede borrar un vehículo con reservas asociadas.

**Frontend**

- `app/page.jsx` reemplaza a `app/page.js`: landing pública, con redirección automática al dashboard si ya hay sesión.
- `app/not-found.js`: página 404 propia.
- `components/LogoutButton.jsx`: botón de cerrar sesión, presente en ambos dashboards.
- `MiPerfilContent.jsx`: gestiona únicamente los vehículos en el dashboard. El perfil personal se consulta y edita desde **Mi cuenta → Mis datos**.

**Bugfix: no se podía reservar desde el panel admin**

- El formulario de reservas vivía solo en `app/dashboard-user/`, una ruta protegida con `requireAuth(["USER"])`. Una cuenta ADMIN quedaba sin ningún lugar desde donde crear una reserva: el formulario del panel admin era el de alta de visitante, que nunca creó reservas.
- `ReservasContent.jsx` se movió a `components/`, porque ahora lo usan los dos dashboards, y se agregó al panel admin.
- De paso: la cuadrícula de ocupación se recarga al crear una reserva. Antes cargaba una sola vez al montarse, así que la cochera seguía viéndose libre hasta refrescar la página — el mismo síntoma por otra causa.
- No hizo falta tocar el backend: `/api/v1/reservas/**` ya pedía solo estar autenticado, y `DashboardAccessSecurityTests` ya cubría que un ADMIN puede usarlo.

**Documentación**

- `AparcAR-Manual-Completo.pdf`: manual de 46 páginas que explica el proyecto de punta a punta para alguien que nunca lo vio.

---

## Raíz del repo

| Archivo | Qué hace |
|---|---|
| `.gitignore` | Ignora `*.class`, `*.jar`, logs, etc. a nivel de todo el repo |
| `README.md` | Guía de instalación y ejecución del proyecto completo (front + back) |
| `ARCHIVOS.md` | Este archivo: mapa general de la estructura del proyecto |
| `TESTS.md` ✏️ | Índice de qué prueba cada archivo de test, front y back, caso por caso (56 archivos, 662 tests) |
| `AparcAR-Manual-Completo.pdf` | Manual de 46 páginas: el dominio, las tecnologías usadas una por una, el árbol de carpetas comentado, los endpoints, la seguridad, la base de datos, Docker y el testing. Pensado para alguien que nunca vio el código |

---

## `.github/workflows/`

| Archivo | Qué hace |
|---|---|
| `ci.yml` | Pipeline de GitHub Actions. Corre en cada pull request contra `main` o `dev` |

El workflow usa `dorny/paths-filter` para detectar qué mitad del repo cambió y correr solo esa:

- **backend** (si cambió `aparcar-api-back/**`): JDK 21 Temurin con caché de Maven, y `mvn -B clean install` — que compila y corre todos los tests del backend.
- **frontend** (si cambió `aparcar-front/**`): Node 24 con caché de npm, y después `npm ci`, `npm run lint`, `npm test` y `npm run build`.

Los dos filtros incluyen además `.github/workflows/**`: si se toca el propio pipeline, corren las dos suites completas. Sin eso, un PR que solo modifica `ci.yml` no ejecutaba ningún check y el cambio al pipeline se mergeaba sin validarse nunca.

Es decir: si un PR rompe un test, el lint o el build, se ve antes del merge.

### Por qué la condición está en los pasos y no en el job

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
| `ReservasVencidas.java` | Tarea programada que pasa a `FINALIZADA` las reservas cuya franja terminó. **No es lo que libera la cochera**: eso sale del solapamiento de rangos y funciona aunque esta tarea nunca corra. Solo mantiene el listado legible |
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
| `ZonaHorariaConfig.java` | Fija la zona horaria de la aplicación (`app.zona-horaria`, por defecto `America/Argentina/Buenos_Aires`). Sin esto el contenedor arranca en UTC y las franjas, que son reloj de pared, quedan desfasadas respecto del navegador |
| `middleware/DevExceptionHandler.java` | Convierte excepciones a respuestas JSON detalladas en desarrollo. Agregado el handler de `AccessDeniedException`, que devuelve 403 con el mensaje del service (ej. "No podés modificar un vehículo que no es tuyo") |
| `middleware/ProdExceptionHandler.java` ✏️ | Manejo de errores para producción, con el mismo handler de `AccessDeniedException`. Suma el de `HttpMessageNotReadableException` 🆕: un JSON mal formado o con un valor imposible de convertir (ej. un tipo de cochera inexistente) es un 400, no un 500 |

---

## `controller/`

Endpoints REST de la aplicación.

| Archivo | Qué hace |
|---|---|
| `AuthController.java` | `/register`, `/login`, `/forgot-password`, `/reset-password` |
| `CocheraController.java` ✏️ | Gestión de cocheras y consulta de cocheras disponibles. Suma `POST /alta-por-planta` 🆕. `/disponibles` recibe el `Authentication` (null si es anónimo) para no ofrecerle cocheras ACCESIBLE a un visitante sin discapacidad declarada. Incluye además `GET /sectores` y el alta en lote vieja `POST /bulk` |
| `ReservaController.java` | Alta y consulta de reservas |
| `UserController.java` | Gestión ADMIN de usuarios: activar, listar inactivos, eliminar, listar todos y editar |
| `VehiculoController.java` | Alta y consulta de vehículos, más `PUT /{id}` y `DELETE /{id}`. Ambos reciben el `Authentication` y le pasan al service el email del que pide y si es ADMIN, para que el service decida si tiene permiso |
| `VisitanteController.java` | Alta y consulta de visitantes, más `GET /me`, `POST /me` y ahora `PUT /me`: el propio visitante (logueado) consulta, carga y edita su perfil sin pasar por un admin. `/me` está declarado **antes** que `/{id}` a propósito, para que `"me"` no se intente parsear como UUID |

### Tabla completa de endpoints

Todo lo que el backend sabe hacer hoy. La columna "Quién puede" sale de `DevSecurityConfig` / `ProdSecurityConfig`.

| Método y ruta | Qué hace | Quién puede |
|---|---|---|
| `POST /register` ✏️ | Crea una cuenta USER activa. Acepta `tieneDiscapacidad` opcional (si no viene, `false`) | Público |
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
| `GET /api/v1/cocheras/sectores` | Los sectores distintos ya usados, ordenados. Alimenta los desplegables de sector del front | ADMIN |
| `POST /api/v1/cocheras/alta-por-planta` 🆕 | Alta por planta: `{ sector, cantidades: { AUTO: n, ... }, estado? }`. Genera los números (`A-05`, `M-12`...) continuando cada prefijo desde el máximo existente y devuelve las cocheras creadas. Todo-o-nada | ADMIN |
| `POST /api/v1/cocheras/bulk` | Alta en lote vieja: una lista de cocheras completas, con el número tipeado. Todo-o-nada. El front ya no la usa | ADMIN |
| `GET /api/v1/cocheras/{id}` | Una cochera por id | ADMIN |
| `PUT /api/v1/cocheras/{id}` | Edita; al deshabilitarla, cancela sus reservas CONFIRMADAS | ADMIN |
| `DELETE /api/v1/cocheras/{id}` | Borra; falla con 400 si tiene reservas | ADMIN |
| `GET /api/v1/cocheras/disponibles` ✏️ | Cocheras libres **durante toda la franja** `desde`/`hasta`, filtradas por tipo de vehículo. Ojo: es "libre en todo el rango", no "libre en algún momento". A un visitante logueado sin discapacidad declarada no le devuelve las ACCESIBLE; anónimos y ADMIN las ven todas | **Público** |
| `POST /api/v1/visitantes` | Alta de visitante (documento único) | Autenticado |
| `GET /api/v1/visitantes` | Lista de visitantes | Autenticado |
| `GET /api/v1/visitantes/{id}` | Un visitante por id | Autenticado |
| `GET /api/v1/visitantes/me` | Mi propio perfil de visitante | Autenticado |
| `POST /api/v1/visitantes/me` | Cargo mi propio perfil (una sola vez) | Autenticado |
| `PUT /api/v1/visitantes/me` ✏️ | Edito mi teléfono, mi email y mi declaración de discapacidad (`tieneDiscapacidad`; si no viene, no se toca) | Autenticado |
| `POST /api/v1/vehiculos` | Alta de vehículo (patente única, normalizada a mayúsculas) | Autenticado |
| `GET /api/v1/vehiculos` | Lista, con filtro opcional `?visitanteId=` | Autenticado |
| `GET /api/v1/vehiculos/{id}` | Un vehículo por id | Autenticado |
| `PUT /api/v1/vehiculos/{id}` | Edita patente y tipo | Dueño o ADMIN |
| `DELETE /api/v1/vehiculos/{id}` | Borra; falla con 400 si tiene reservas | Dueño o ADMIN |
| `POST /api/v1/reservas` ✏️ | Crea la reserva sobre la franja `desde`/`hasta`, aplicando las reglas de negocio y el control de superposición. Una cochera ACCESIBLE exige que el dueño de la reserva tenga declarada la discapacidad | Autenticado |
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
| `RegistrationDto.java` ✏️ | Body de `POST /register`: nombre, email, password y teléfono, más `tieneDiscapacidad` opcional |
| `ResetPasswordDto.java` | Body para cambiar contraseña mediante OTP |
| `UserEmailDto.java` | Body genérico `{ email }`, utilizado para activar/eliminar usuarios |
| `UpdateUserDto.java` | Body para editar nombre, teléfono y authorities de un usuario |
| `UserResponseDto.java` | Respuesta administrativa de usuario: id, nombre, email, teléfono, authorities y estado; no expone password |

### `dto/email/`

| Archivo | Qué hace |
|---|---|
| `PlainEmailData.java` | Datos internos utilizados para enviar un email |

### `dto/reserva/`

| Archivo | Qué hace |
|---|---|
| `VisitanteAltaDto.java` ✏️ | Alta operativa del admin: los datos del visitante, su vehículo y la franja de la reserva, todo junto, porque se crean en una sola transacción. Suma `tieneDiscapacidad` opcional |
| `VisitanteResponseDto.java` ✏️ | Datos devueltos de un visitante. Suma `tieneDiscapacidad` (nunca null) |
| `VehiculoRequestDto.java` | Datos recibidos para crear un vehículo |
| `VehiculoUpdateDto.java` | Body de `PUT /api/v1/vehiculos/{id}`: patente y tipo. Valida el formato de patente con regex (`AAA000` o `AA000AA`) |
| `VisitanteUpdateDto.java` ✏️ | Body de `PUT /api/v1/visitantes/me`: teléfono, email y `tieneDiscapacidad` (null = no cambiar). El nombre y el documento no se editan desde acá |
| `VehiculoResponseDto.java` | Datos devueltos de un vehículo |
| `CocheraRequestDto.java` | Datos recibidos para crear una cochera (y cada elemento de `/bulk`) |
| `CocheraAltaPorPlantaDto.java` 🆕 | Body del alta por planta: `sector`, `cantidades` (mapa tipo → cantidad, sin negativos) y `estado` opcional (HABILITADA por defecto) |
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
| `Visitante.java` ✏️ | Entidad única del sistema: es a la vez la cuenta con la que se inicia sesión y la persona que reserva. Antes eran dos (`AppUser` y `Visitante`), unidas por un vínculo opcional que producía "visitantes fantasma". Suma `tieneDiscapacidad` y `puedeUsarCocheraAccesible()`, que es la única pregunta que hace el resto del código |
| `InactiveUsersDto.java` | Wrapper con emails de usuarios inactivos |
| `OneTimePassword.java` | Entidad de códigos OTP para recuperación de contraseña |

### `entity/reserva/`

| Archivo | Qué hace |
|---|---|
| `Visitante.java` | Entidad de visitantes; agregado `appUser` (`@OneToOne` opcional hacia `AppUser`) para el login propio del visitante |
| `Vehiculo.java` | Entidad de vehículos asociados a visitantes |
| `VehiculoTipo.java` | Enum `AUTO`, `MOTO`, `CARGA` |
| `Cochera.java` | Entidad de cocheras |
| `CocheraTipo.java` | Enum `AUTO`, `MOTO`, `ACCESIBLE`, `CARGA` |
| `CocheraEstado.java` | Estado operativo de una cochera |
| `Reserva.java` | Entidad de reservas. La `fecha` (un día) se reemplazó por la franja `desde`/`hasta`. Expone `seSolapaCon(desde, hasta)` y `estaVigenteEn(momento)`, que es donde está escrita la regla de superposición |
| `ModalidadReserva.java` | Enum `FRANJA`, `MEDIA_JORNADA`, `JORNADA_COMPLETA`, deducido de la duración de la franja. No se persiste |
| `ReservaEstado.java` | Enum `CONFIRMADA`, `CANCELADA`, `FINALIZADA`. `FINALIZADA` la pone una tarea programada cuando pasa el `hasta`; es informativa, la disponibilidad no depende de ella |

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
| `JWTValidationFilter.java` | Valida JWT y carga la autenticación; `/register` ahora también procesa JWT porque requiere ADMIN |
| `RateLimitFilter.java` | Limita intentos sobre endpoints sensibles |
| `StripPortFromXffFilter.java` | Normaliza la IP proveniente de headers proxy |

---

## `repository/`

Acceso a datos usando Spring Data JPA.

| Archivo | Qué hace |
|---|---|
| `VisitanteRepository.java` | Acceso a `Visitante`, que es a la vez cuenta y visitante. Usa `UUID` como id y busca por email o documento |
| `CocheraRepository.java` ✏️ | Acceso a cocheras. `findDistinctSectores` alimenta `/sectores`; `findAllNumeros` 🆕 trae solo los números existentes para la numeración automática |
| `OneTimePasswordRepository.java` | Acceso a códigos OTP |
| `ReservaRepository.java` | Acceso a reservas y consultas relacionadas con disponibilidad. Agregado `existsByVehiculoId`, que usa `VehiculoService.eliminar` para no borrar un vehículo con reservas |
| `VehiculoRepository.java` | Acceso a vehículos |
| `VisitanteRepository.java` | Acceso a visitantes |

---

## `security/`

Autenticación y autorización.

| Archivo | Qué hace |
|---|---|
| `VisitanteDetailsService.java` | Carga un `Visitante` por email para Spring Security |
| `CustomBasicAuthenticationEntryPoint.java` | Respuesta devuelta cuando una ruta requiere autenticación (401) |
| `CustomAccessDeniedHandler.java` | Respuesta devuelta cuando el usuario está autenticado pero no tiene el rol (403). Devuelve JSON con el mismo formato que el 401 (`timestamp`, `status`, `error`, `message`, `path`, `client_ip`) y deja un `log.warn` con la IP. Se registra en las dos security configs con `.exceptionHandling(...)` |
| `authenticationProvider/DevAuthenticationProvider.java` | Autenticación de desarrollo y test. **Cambio importante: ahora valida la contraseña con BCrypt**, igual que producción. Antes autenticaba con cualquier contraseña siempre que el email existiera. Además traduce `UsernameNotFoundException` a `BadCredentialsException`, para no filtrar qué emails están registrados |
| `authenticationProvider/ProdAuthenticationProvider.java` | Autenticación de producción con validación de password |
| `securityConfig/DevSecurityConfig.java` | Configuración de seguridad de desarrollo: la tabla de permisos, CORS para `localhost:*`, sesión STATELESS, CSRF desactivado, y el registro de los filtros. Ahora también registra el `CustomAccessDeniedHandler` |
| `securityConfig/ProdSecurityConfig.java` | Configuración equivalente para producción/test, con el mismo handler de 403 |

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

Sobre los códigos de error: **401** significa "no sé quién sos" (falta el token, venció, o la firma no da) y lo produce `CustomBasicAuthenticationEntryPoint`. **403** significa "sé quién sos y no te corresponde" y lo produce `CustomAccessDeniedHandler`. Los dos devuelven JSON con el mismo formato.

---

## `service/` + `service/impl/`

Lógica de negocio.

| Archivo | Qué hace |
|---|---|
| `IAuthService.java` / `impl/AuthService.java` ✏️ | Registro, login y recuperación de contraseña. El registro guarda la declaración de discapacidad |
| `ICocheraService.java` / `impl/CocheraService.java` ✏️ | Gestión de cocheras. La disponibilidad se calcula por rango: una cochera figura libre solo si no tiene ninguna reserva confirmada que pise la franja pedida. Suma `crearPorPlanta` 🆕 (valida, numera, guarda todo con `saveAllAndFlush` y traduce un choque de número duplicado a 400) y el filtro de ACCESIBLE en `listarDisponibles` según quién pregunta. `crearEnLote` (el `/bulk` viejo) sigue |
| `impl/NumeracionCocheras.java` 🆕 | La numeración automática `{PREFIJO}-{secuencial}`, como puro cálculo sin base (por eso se testea aislada). El `switch` de prefijos es exhaustivo: si se agrega un tipo al enum, no compila hasta asignarle el suyo |
| `IReservaService.java` / `impl/ReservaService.java` ✏️ | Lógica de reservas. Valida compatibilidad de tipos y la franja: que el fin sea posterior al inicio, que no esté enteramente vencida, que la cochera esté libre en todo el rango y que el vehículo no esté comprometido en otra. Suma `validarAccesibilidad` 🆕: una ACCESIBLE solo para un dueño con la discapacidad declarada |
| `IUserService.java` / `impl/UserService.java` | Gestión administrativa de usuarios: listar, editar, activar y eliminar. Al eliminar, desvincula primero el visitante propio de la cuenta (si tiene uno) antes de borrarla |
| `IVehiculoService.java` / `impl/VehiculoService.java` | Gestión de vehículos. Agregados `editar` y `eliminar`, los dos con `verificarPropietario`: un ADMIN pasa siempre; un USER solo si el `appUser` del visitante dueño coincide con el email del que pide, y si no, `AccessDeniedException`. `eliminar` además bloquea si el vehículo tiene reservas |
| `IVisitanteService.java` / `impl/VisitanteService.java` ✏️ | Gestión de visitantes, más `obtenerPropio(email)`, `crearPropio(email, dto)` y `actualizarPropio(email, dto)`: el visitante carga y edita sus propios datos, vinculados a su cuenta. El alta del admin y `actualizarPropio` guardan la declaración de discapacidad |

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
| `db.changelog-master.yaml` ✏️ | Lista de migraciones Liquibase |
| `001-initial-schema.yaml` | Migración inicial actualmente vacía |
| `002-visitantes-vehiculos-cocheras-reservas.yaml` | Crea tablas de visitantes, vehículos, cocheras y reservas |
| `003-visitante-app-user.yaml` | Agregaba `visitantes.app_user_id` para vincular un visitante a su cuenta de login. Quedó sin efecto: `004` unificó las dos entidades |
| `004-unificar-visitante-cuenta.yaml` | Fusiona visitante y cuenta en una sola tabla `visitantes`, y elimina `app_users`. Un documento es una persona es una cuenta |
| `005-identidad-visitante-unica.yaml` | Índices de PostgreSQL para que el email sea único sin distinguir mayúsculas |
| `006-reservas-por-franja-horaria.yaml` | Convierte la reserva por día en una franja `desde`/`hasta`. Las reservas existentes se conservan como el día completo que ocupaban, que es lo que significaban antes. Crea los índices `(cochera_id, estado, desde, hasta)` y su equivalente por vehículo, que son los que sostienen la consulta de solapamiento. El backfill va en dos versiones porque la aritmética de fechas no es portable: prod usa Postgres y los tests H2 |
| `007-visitante-tiene-discapacidad.yaml` 🆕 | Agrega `visitantes.tiene_discapacidad` (boolean, no nulo, `false` por defecto): las cuentas que ya existían quedan como "sin declarar" |

### Por qué `003` no tenía foreign key física hacia `app_users` (histórico)

`app_users` no la crea Liquibase — la crea Hibernate con `ddl-auto: update`, que corre **después** de Liquibase. Una FK en `003` hacia esa tabla se rompe en cualquier base nueva (los tests con H2, o el primer `docker compose up` de otra persona) porque `app_users` todavía no existe cuando corre esta migración. Se detectó al escribir los tests: pasaba en la Postgres de desarrollo (porque esa tabla ya existía de arranques anteriores) pero fallaba siempre en H2. La relación la validaba JPA, no la base. Hoy la nota es solo histórica —`004` eliminó `app_users`— pero la lección sigue valiendo: una migración no puede depender de una tabla que crea Hibernate después.

---

# `src/test/java/com/aparcar/api/`

Mismas marcas que el resto del documento: 🆕/✏️ = agregado/modificado en esta tanda.

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
| `filters/JWTGeneratorFilterTests.java` | **Caja blanca.** Prueba el filtro que arma el JWT directamente (mocks, sin Spring): genera `Authorization: Bearer ...` con email/authorities correctos solo si hay autenticación, y solo en `/login` |
| `filters/RateLimitFilterTests.java` | **Caja blanca.** Prueba el limitador de intentos directamente: deja pasar las primeras 5 requests por IP y bloquea (429) la 6ta; IPs distintas tienen buckets independientes |
| `integration/AuthControllerTests.java` | Tests de `/register`, `/login`, `/forgot-password`, `/reset-password`. Actualicé `registerValidatesInput` y `registerCreatesInactiveUser` porque `/register` pasó a requerir rol ADMIN (antes eran públicos y quedaron rotos por ese cambio); agregué los casos 401 (anónimo) y 403 (rol USER) |
| `integration/AccesibilidadTests.java` 🆕 | **Caja negra.** Cocheras ACCESIBLE de punta a punta: la declaración en `/register`, en el alta del admin y en `PUT /me`; quién puede reservar una (USER con y sin declaración, ADMIN a nombre de otro) y qué le muestra `/disponibles` a cada uno |
| `integration/CocheraAltaPorPlantaControllerTests.java` 🆕 | **Caja negra.** `POST /alta-por-planta`: seguridad, números que continúan desde lo que hay en la base, dos altas seguidas sin repetir, y las validaciones que devuelven 400 sin crear nada |
| `integration/CocheraControllerTests.java` | **Caja negra.** CRUD completo de `/api/v1/cocheras`: seguridad (401/403), validaciones, alta/edición/borrado, `/sectores`, `/bulk` y `/disponibles` de punta a punta |
| `integration/DashboardAccessSecurityTests.java` | **Caja negra.** Matriz de qué rol puede pegarle a qué endpoint: `/api/v1/visitantes`, `/vehiculos` y `/reservas` exigen solo estar autenticado (los usan ambos dashboards, sin importar el rol), `/api/v1/usuarios` exige ADMIN, `/api/v1/cocheras/disponibles` es público |
| `integration/LoginFlowTests.java` | **Caja negra**, contra un servidor real embebido (no MockMvc — ver el porqué en el comentario de la clase). Login real con HTTP Basic: verifica el JWT devuelto (email, authorities) y los 401. Actualizado: ahora comprueba que **una contraseña incorrecta devuelve 401 también en dev/test**, porque el `DevAuthenticationProvider` pasó a validarla |
| `integration/ReservaControllerTests.java` ✏️ | **Caja negra.** Reglas de negocio de `/api/v1/reservas` contra DB real (no mocks): vehículo que no pertenece al visitante, incompatibilidad de tipos, cochera ACCESIBLE acepta cualquier vehículo, y toda la **franja horaria** — dos visitantes que se pisan, dos que usan la misma cochera en franjas consecutivas, reserva de varios días, y una reserva vencida que deja la cochera libre sin cancelarla. El caso de ACCESIBLE ahora declara la discapacidad del visitante |
| `integration/UserControllerTests.java` | Tests de `/users/**` y `/api/v1/usuarios/**`. Agregué los casos de `PUT /api/v1/usuarios/{id}` (actualiza campos, 404 si no existe, 401 anónimo) |
| `integration/VehiculoControllerTests.java` | **Caja negra.** `/api/v1/vehiculos`: formato de patente, normalización a mayúsculas, patente/visitante duplicado o inexistente, filtro por `visitanteId`, y los casos nuevos de `PUT`/`DELETE` con control de propietario (403 si no sos el dueño) |
| `integration/VisitanteControllerTests.java` | **Caja negra.** `/api/v1/visitantes`, con foco en `/me` (el visitante carga y edita su propio perfil): 404 sin perfil, alta, documento duplicado, cuenta que ya tiene un perfil cargado, y la actualización por `PUT /me` |
| `security/VisitanteDetailsServiceTests.java` | **Caja blanca.** El puente `Visitante` → `UserDetails`: mapea authorities correctamente y lanza `UsernameNotFoundException` si el email no existe |
| `security/authenticationProvider/DevAuthenticationProviderTests.java` | **Caja blanca.** Reescrito: ya no documenta el viejo comportamiento inseguro. Ahora verifica que dev/test **valida la contraseña contra el hash** y rechaza con `BadCredentialsException` tanto si no matchea como si el email no existe |
| `security/authenticationProvider/ProdAuthenticationProviderTests.java` | **Caja blanca.** El que sí valida contraseña (perfil prod real): rechaza con `BadCredentialsException` tanto si la contraseña no matchea como si el usuario no existe (para no filtrar cuáles emails están registrados) |
| `service/AuthServiceTests.java` | Tests unitarios de `AuthService` |
| `service/CocheraAltaPorPlantaTests.java` 🆕 | **Caja blanca** de `crearPorPlanta` con mocks: orden por tipo, continuación desde los existentes, estado por defecto, validaciones, y el choque concurrente por número duplicado traducido a 400 |
| `service/CocheraDisponiblesAccesiblesTests.java` 🆕 | **Caja blanca** de qué ACCESIBLE ofrece `/disponibles` según quién pregunta (anónimo, ADMIN, visitante con y sin declaración, cuenta inexistente) |
| `service/CocheraServiceTests.java` | Tests de cocheras, incluye la cancelación automática de reservas al deshabilitar una cochera y el alta en lote vieja |
| `service/NumeracionCocherasTests.java` 🆕 | **Caja blanca** de la numeración: continuar desde el máximo, que los prefijos no se crucen (`AC-..` no cuenta para `A` ni para `C`), ignorar formatos viejos, ancho mínimo de 2 dígitos sin tope |
| `service/ReservaAccesibilidadTests.java` 🆕 | **Caja blanca** de la regla de ACCESIBLE al reservar: cuenta la declaración del dueño, no la de quien carga la reserva |
| `service/ReservaServiceTests.java` ✏️ | Tests unitarios de reservas (con mocks): mismas reglas que `ReservaControllerTests` pero aisladas del repositorio. Sumadas las validaciones de franja: rango invertido, duración cero, franja ya vencida, inicio en el pasado con fin futuro (que sí es válido) y vehículo comprometido en otra cochera |
| `entity/ReservaSolapamientoTests.java` | **Caja blanca** del predicado de solapamiento, que es donde vive toda la regla. Ataca los bordes sin pasar por el servicio ni la base: franja contigua, contenida, idéntica, y las que se pisan por seis minutos. Un `<=` de más y dos reservas consecutivas dejarían de poder existir; uno de menos y se permitiría pisar |
| `component/ReservasVencidasTests.java` | **Caja blanca** de la tarea que marca las vencidas como `FINALIZADA` |
| `config/ZonaHorariaConfigTests.java` | **Caja blanca** de la zona horaria. Incluye el caso que rompía: arrancar la JVM en UTC y comprobar que la corrección la deja en hora local |
| `service/UserServiceTests.java` | Tests de gestión de usuarios. Agregué los casos de `deleteUser`: desvincula el visitante propio antes de borrar la cuenta (evita romper la FK `fk_visitante_app_user`), y no hace nada si no hay ninguno vinculado |
| `service/VehiculoServiceTests.java` | Tests de vehículos. Agregados los casos de `editar` y `eliminar`: control de propietario, patente duplicada al editar, y el bloqueo al borrar un vehículo con reservas |
| `service/VisitanteDiscapacidadTests.java` 🆕 | **Caja blanca** de cómo se guarda y edita la declaración: alta del admin (false si no viene), marcar y desmarcar desde el perfil, y que sin el campo no se toque |
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
| `package.json` / `package-lock.json` | Dependencias. Agregados `npm test` (`vitest run`) y `npm run test:watch` (`vitest`) |
| `postcss.config.mjs` | Configuración Tailwind CSS 4 |
| `vitest.config.mjs` | Configuración de Vitest: entorno `jsdom`, alias `@/`, y el archivo de setup de `test/` |

---

# `app/` — páginas y contenido

| Archivo / carpeta | Qué hace |
|---|---|
| `api.jsx` | Instancia Axios compartida; configura base URL e inyecta JWT en requests autenticados. El interceptor de request **no pisa** un `Authorization` ya seteado a mano (ej. el `Basic` de `/login`) — antes lo pisaba con el `Bearer` de una cookie vieja, causando que el login pidiera el usuario y contraseña dos veces |
| `favicon.ico` | Ícono de la aplicación |
| `globals.css` | Estilos globales y Tailwind |
| `layout.js` | Layout global y `<Toaster />` de Sonner |
| `page.jsx` | **Reemplaza a `page.js`** (que se eliminó). Landing pública de AparcAR: presenta el producto, con íconos SVG propios y menú hamburguesa en mobile. Si ya hay sesión activa, redirige sola al dashboard que corresponde al rol |
| `not-found.js` | Página 404 propia de Next.js, para cuando alguien escribe una ruta que no existe |
| `login/page.jsx` | Login del personal interno; genera sesión y redirige según rol a `dashboard-admin` o `dashboard-user`. Ahora pasa `validateStatus` a Axios para que un 401 **no** se trate como error: así el interceptor de respuesta no borra la cookie ni redirige, y la pantalla puede mostrar "Credenciales incorrectas" sin recargarse |
| `recover-password/page.jsx` | Solicitud de OTP |
| `reset-password/page.jsx` | Cambio de contraseña mediante OTP |
| `unauthorized/page.jsx` | Página mostrada cuando el usuario no tiene permisos |

---

## `app/dashboard-admin/`

Sección para usuarios con rol `ADMIN`.

| Archivo | Qué hace |
|---|---|
| `page.jsx` | Entrada del dashboard ADMIN, protegida con `requireAuth(["ADMIN"])`. Server Component: solo valida el rol y arma la barra de navegación (`/cocheras`, `/usuarios`, `LogoutButton`). El contenido lo delega en `PanelOperativo` |
| `PanelOperativo.jsx` | Agrupa las tres secciones operativas del panel: la cuadrícula de ocupación, el alta de visitantes y **el formulario de reservas**. Existe como componente de cliente aparte porque `page.jsx` es Server Component y no puede tener estado: acá vive el contador que le avisa a la cuadrícula que se creó una reserva y tiene que recargarse |
| `VisitantesContent.jsx` ✏️ | Alta de visitante + vehículo hecha por el admin (formulario completo). Suma el checkbox opcional "Persona con discapacidad": viaja en el alta y, mientras no está marcado, el desplegable no ofrece cocheras ACCESIBLE (con una nota que explica cómo verlas) |

### `app/dashboard-admin/cocheras/`

Módulo de gestión de cocheras.

| Archivo | Qué hace |
|---|---|
| `page.jsx` | Ruta `/dashboard-admin/cocheras`; valida server-side rol `ADMIN` |
| `CocherasManagement.jsx` ✏️ | CRUD completo de cocheras: alta, edición (con confirmación al deshabilitar una cochera con reservas), baja (bloqueada si tiene reservas asociadas), y filtros por sector/tipo/estado/fecha. La sección de alta en lote fila por fila se reemplazó por **"Alta por planta"** 🆕: un sector (mismo selector que el alta, con "+ Otro (sector nuevo)") y una cantidad por tipo que arranca en 0; el botón queda deshabilitado con todo en 0, el pedido va a `/alta-por-planta`, y el resultado muestra los números que asignó el backend, agrupados por tipo |

### `app/dashboard-admin/usuarios/`

Módulo de gestión de usuarios internos.

| Archivo | Qué hace |
|---|---|
| `page.jsx` | Ruta `/dashboard-admin/usuarios`; valida server-side que el usuario tenga authority `ADMIN` |
| `UserManagement.jsx` | Interfaz interactiva para listar, crear, editar, asignar roles, activar y eliminar usuarios |

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
| `page.jsx` | Entrada del dashboard USER, protegida con `requireAuth(["USER"])`. Combina vehículos y reservas; el encabezado incluye el menú **Mi cuenta** |
| `MiPerfilContent.jsx` | Lista, agrega, edita y elimina vehículos propios, conservando validaciones, confirmación de baja y avisos al formulario de reservas. No muestra información personal ni consulta `/api/v1/visitantes/me` |
| `PanelVisitante.jsx` | Agrupa vehículos y reservas y mantiene el contador que refresca las patentes cuando cambia un vehículo |
| `ReservasContent.jsx` | **Se mudó a `components/`**, porque ahora lo usan los dos dashboards. Ver esa sección |

---

## `public/`

| Archivo | Qué hace |
|---|---|
| `Logo.jpeg` | Logo de AparcAR utilizado actualmente en la pantalla de login |

---

# `components/`

| Archivo | Qué hace |
|---|---|
| `ProtectedRoute.jsx` | Wrapper client-side para proteger rutas según autenticación/rol. Refactorizado: se eliminó el estado `isReady` y la decisión de renderizar se deriva directo de `isHydrated + isAuthenticated + hasRequiredRole`, lo que evita mostrar contenido un instante antes de redirigir |
| `LogoutButton.jsx` | Botón "Cerrar sesión": llama a `logout()` del store (que borra la cookie JWT y limpia el estado) y navega a `/login` con `router.replace`, para que el botón Atrás no vuelva al dashboard |
| `ReservasContent.jsx` ✏️ | **Movido desde `app/dashboard-user/`.** Alta de reserva por patente: se escribe/elige la patente (autocompletado nativo) y se resuelven solos el visitante y el tipo de vehículo. Vuelve a pedir la lista de vehículos al hacer foco en el campo, por si se cargó uno recién más arriba en la misma página. Sirve igual para los dos roles porque busca sobre el catálogo completo, sin filtrar por la cuenta que mira. La prop opcional `onReservaCreada` la usa el panel admin para refrescar la cuadrícula. Sus `id` de formulario van prefijados con `reserva-` para no chocar con los del alta de visitante, que se renderiza en la misma página. En modo admin oculta las cocheras ACCESIBLE cuando el dueño del vehículo tiene `tieneDiscapacidad === false`, y lo explica con su nombre (si el dato no viene, no oculta nada). El rechazo del backend se muestra tal cual |
| `AtajosJornada.jsx` | Los dos atajos de jornada (**media jornada** / **jornada completa**), compartidos por el formulario de reserva y el alta de visitante. Solo mueven el campo `Hasta`, sumando al inicio los minutos exactos del umbral con el que el backend clasifica la modalidad. El que coincide con la franja cargada queda marcado (`aria-pressed`), así que además de ser un atajo sirve de lectura de lo que se está reservando. Quedan deshabilitados sin inicio, porque no habría a qué sumarle. El `idPrefijo` evita que choquen los `id` cuando los dos formularios conviven en la misma página |

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
| `franjaHoraria.js` | Todo lo que necesita la franja horaria en el front: el tamaño del bloque (`PASO_MINUTOS`), bajar un horario al bloque en el que cae, el arranque por defecto, los umbrales de jornada y la traducción de la modalidad que manda el backend. El formato legible (`formatearRango`) se arma a mano y no con `toLocaleString`, porque `es-AR` devuelve reloj de 12 horas (`10:00 a. m.`), peor de leer para horarios de cochera, y el resultado varía según el ICU del entorno. Distingue dos reglas que conviene no confundir ✏️: `redondearAlBloqueLocal` lleva al bloque más cercano lo que escribe una persona, y `alBloque` trunca hacia abajo el arranque por defecto. El redondeo se resuelve sobre un `Date` y no sobre el texto porque puede empujar a la hora siguiente, y hasta al día siguiente |

Ejemplo:

```javascript
await requireAuth(["ADMIN"]);
```

Si el JWT no existe, redirige al login. Si existe pero no contiene alguno de los roles requeridos, redirige a `/unauthorized`.

---

# `test/` — tests automatizados (frontend)

Toda la carpeta es nueva: no existía testing en el frontend antes de esta sesión. Usa **Vitest + React Testing Library + jsdom**, más `axios-mock-adapter` para probar los interceptores de `api.jsx` sin red real. Corre con `npm test` (una vez) o `npm run test:watch`.

Convención: `test/` refleja la estructura de `app/`, `store/` y `utils/` (misma idea que `src/test/java/...` reflejando `src/main/java/...` en el backend).

| Archivo | Qué prueba |
|---|---|
| `setup.js` | Carga los matchers de `jest-dom`, limpia el DOM y las cookies después de cada test |
| `page.test.jsx` | `app/page.jsx`: muestra la landing si no hay sesión, redirige según el rol si la hay, y el menú hamburguesa abre y cierra en mobile |
| `components/LogoutButton.test.jsx` | `components/LogoutButton.jsx`: cierra la sesión y navega a `/login` |
| `api.test.jsx` | Interceptores de `app/api.jsx`: baseURL, inyección del Bearer desde la cookie, **que no pise un Authorization ya seteado a mano** (regresión del bug del login doble), y el manejo de 401 (borra cookie + redirige) |
| `login/page.test.jsx` | `app/login/page.jsx`: validaciones, Basic Auth armado correctamente, redirección según rol (ADMIN vs USER), errores del backend |
| `store/authStore.test.js` | `store/authStore.js`: decodificación de authorities del JWT, cookie, expiración, `logout`/`checkAuth` |
| `utils/env.test.js` | `utils/env.js`: prioridad de `window.__ENV` sobre el valor de build |
| `utils/franjaHoraria.test.js` 🆕 | `utils/franjaHoraria.js`: que el redondeo vaya al bloque más cercano en las dos direcciones, que cruce la hora, el día y el año, y que no se confunda con el truncado del arranque por defecto, que nunca adelanta el horario |
| `dashboard-admin/VisitantesContent.test.jsx` ✏️ | Alta operativa del admin (una sola llamada), validaciones, errores de duplicados, y el checkbox de discapacidad: desmarcado por defecto, viaja en el alta, y oculta las ACCESIBLE hasta marcarlo |
| `dashboard-admin/cocheras/CocherasManagement.test.jsx` ✏️ | CRUD completo: filtros, alta, edición (con `window.confirm` al deshabilitar), baja (con confirmación), la navegación de regreso al panel, y el **alta por planta** (cantidades en 0, botón deshabilitado, payload, números asignados por el backend y su error) |
| `dashboard-admin/usuarios/UserManagement.test.jsx` | Alta de usuario, activar, editar roles, eliminar (con confirmación), y la navegación de regreso al panel |
| `components/ReservasContent.test.jsx` ✏️ | Resolución de visitante/vehículo por patente, cochera deshabilitada hasta tener match, **regresión del bug de caché de vehículos al hacer foco**, envío de la reserva, y toda la franja horaria: arranque en el bloque de 15 en curso, `step` declarado, horarios fuera de bloque bajados al bloque, rango invertido, atajos de jornada llevados hasta el campo y la modalidad que muestra el listado. Se movió junto con el componente. Suma las cocheras accesibles: ocultas para el admin si el dueño no declaró discapacidad, sin filtrar de nuevo en modo visitante, y el mensaje de rechazo del backend |
| `components/AtajosJornada.test.jsx` | `components/AtajosJornada.jsx`: que cada atajo caiga **justo** en el umbral con el que el backend clasifica la modalidad, que muevan solo el `Hasta`, que marquen el que coincide con la franja cargada y que queden deshabilitados sin inicio |
| `dashboard-admin/PanelOperativo.test.jsx` | **Regresión del bug de la reserva que no se agregaba**: que el panel admin incluya el formulario de reservas, que un admin pueda crear una resolviendo el visitante por patente, y que la cuadrícula pase de "0/1 ocupadas" a "1/1 ocupadas" sin recargar |
| `dashboard-user/MiPerfilContent.test.jsx` | Carga y gestión de vehículos propios, validaciones, errores, edición y eliminación con confirmación. Verifica que no se consulte el perfil ni se muestren datos personales o un acceso duplicado a Mis datos |

El detalle de qué casos prueba cada archivo (front y back) está en `TESTS.md`, en la raíz del repo: **69 archivos y 779 tests** en total (455 del backend en 42 archivos, 324 del frontend en 27). Los dos suites corren solas en cada pull request, vía `.github/workflows/ci.yml`.

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
| `visitantes` ✏️ | Liquibase | `id`, `nombre`, `documento`, `email`, `password`, `telefono`, `is_active`, `tiene_discapacidad`. Es a la vez la cuenta de login y la persona que reserva | `Visitante.java` |
| `vehiculos` | Liquibase | `id`, `patente`, `tipo`, `visitante_id` | `Vehiculo.java` |
| `cocheras` | Liquibase | `id`, `numero` (único: es lo que frena un número duplicado en altas simultáneas), `sector`, `tipo`, `estado` | `Cochera.java` |
| `reservas` | Liquibase | `id`, `desde`, `hasta`, `visitante_id`, `vehiculo_id`, `cochera_id`, `estado`, `fecha_creacion` | `Reserva.java` |
| `visitante_authorities` | Liquibase | `visitante_id`, `authority` | `Visitante.authorities` |
| `otp_codes` | Liquibase | `id`, `token`, `user_id`, `expires_at`, `used` | `OneTimePassword.java` |
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
