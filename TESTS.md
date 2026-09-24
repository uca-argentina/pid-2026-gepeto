# Indice de tests

Que prueba cada archivo de test del proyecto, caso por caso. Ver `ARCHIVOS.md` para que hace cada archivo de codigo en general, y `AparcAR-Manual-Completo.pdf` para la explicacion conceptual de por que el proyecto testea asi (caja blanca vs caja negra, la trampa de MockMvc, los tests de regresion).

> Este archivo se mantiene a mano. Si agregas o borras un test, actualizalo: los totales del final tienen que seguir cerrando.

---

# Backend (`aparcar-api-back/src/test/java/com/aparcar/api/`)

## `AparcarApiApplicationTests.java`

```
contextLoads   (sin @DisplayName)
```

## `component/OTPCleanupTests.java`

```
otpCleanup deletes all expired OTPs
```

## `component/RevokedUserCacheTests.java`

```
RevokedUserCache revokes and checks revoked users
RevokedUserCache rejects nulls
```

## `component/SpringEmailSenderTests.java`

```
sendPlainTextEmail should send email successfully
sendPlainTextEmail should throw RuntimeException on error
```

## `filters/JWTGeneratorFilterTests.java`

Caja blanca. El filtro que arma el JWT, probado en aislamiento con mocks.

```
shouldNotFilter devuelve false solo para /login
doFilterInternal no agrega header Authorization si no hay autenticación
doFilterInternal genera un JWT con email y authorities cuando hay autenticación
El JWT expira 8 horas después de emitido
```

## `filters/RateLimitFilterTests.java`

Caja blanca. El limitador de intentos: 5 por IP por minuto, buckets independientes.

```
shouldNotFilter deja pasar rutas que no son login/register/forgot-password
shouldNotFilter aplica rate limit a login, register y forgot-password
permite las primeras 5 requests de una misma IP
la 6ta request de la misma IP en la ventana recibe 429 y no llega al resto de la cadena
dos IPs distintas tienen buckets independientes
```

## `integration/AuthControllerTests.java`

```
/register validates input
/register creates inactive user
/login returns username
/forgot-password sends OTP
/reset-password resets password with valid OTP
```

## `integration/CocheraControllerTests.java`

Caja negra. CRUD completo de cocheras de punta a punta, contra base H2 real.

```
[Caja negra] endpoints de gestión devuelven 401 para usuarios anónimos
[Caja negra] /disponibles es publico incluso para anonimos
[Caja negra] endpoints de gestión devuelven 403 para USER sin rol ADMIN
[Caja negra] POST /api/v1/cocheras devuelve 400 si falta el numero
[Caja negra] POST /api/v1/cocheras devuelve 201 y crea la cochera con datos validos
[Caja negra] POST /api/v1/cocheras devuelve 400 si el numero ya existe
[Caja negra] GET /api/v1/cocheras devuelve todas las cocheras
[Caja negra] GET /api/v1/cocheras/{id} devuelve 404 si no existe
[Caja negra] GET /api/v1/cocheras/{id} devuelve 200 con la cochera cuando existe
[Caja negra] PUT /api/v1/cocheras/{id} devuelve 404 si no existe
[Caja negra] PUT /api/v1/cocheras/{id} devuelve 200 y actualiza los datos
[Caja negra] PUT /api/v1/cocheras/{id} devuelve 400 si el nuevo numero ya esta en uso
[Caja negra] DELETE /api/v1/cocheras/{id} devuelve 404 si no existe
[Caja negra] DELETE /api/v1/cocheras/{id} devuelve 204 cuando no tiene reservas
[Caja negra] DELETE /api/v1/cocheras/{id} devuelve 400 si tiene reservas asociadas
[Caja negra] /disponibles excluye cocheras deshabilitadas
[Caja negra] /disponibles filtra por tipoVehiculo compatible
```

## `integration/DashboardAccessSecurityTests.java`

Caja negra. La matriz de permisos: que rol puede pegarle a que endpoint.

```
GET /api/v1/visitantes, /vehiculos y /reservas devuelven 401 para anónimos
GET /api/v1/visitantes, /vehiculos y /reservas devuelven 200 para rol USER (los necesita dashboard-user)
GET /api/v1/visitantes, /vehiculos y /reservas devuelven 200 para rol ADMIN (los necesita dashboard-admin)
GET /api/v1/cocheras/disponibles es público
GET /api/v1/usuarios devuelve 401 para anónimos
GET /api/v1/usuarios devuelve 403 para rol USER (esta pantalla es solo de ADMIN)
GET /api/v1/usuarios devuelve 200 para rol ADMIN
El 403 devuelve el mismo formato JSON que el 401 (no un 404 ni un body vacío)
```

## `integration/LoginFlowTests.java`

Caja negra contra un servidor embebido real (no MockMvc). Ver la nota al pie sobre `getServletPath()`.

```
login con credenciales válidas devuelve 200 y un JWT con el email y las authorities del usuario
login con un usuario que solo tiene rol USER devuelve un JWT con una sola authority
login con email inexistente devuelve 401 sin JWT
en el perfil de test/dev, una contraseña incorrecta devuelve 401 sin JWT
```

## `integration/ReservaControllerTests.java`

Caja negra. Las reglas de negocio de reservas contra DB real.

```
[Caja negra] endpoints de reservas devuelven 401 para anonimos
[Caja negra] POST /api/v1/reservas devuelve 400 si falta la fecha
[Caja negra] POST /api/v1/reservas devuelve 400 si la fecha es anterior a hoy
[Caja negra] POST /api/v1/reservas devuelve 404 si el visitante no existe
[Caja negra] POST /api/v1/reservas devuelve 404 si la cochera no existe
[Caja negra] POST /api/v1/reservas devuelve 400 si el vehiculo no pertenece al visitante indicado
[Caja negra] POST /api/v1/reservas devuelve 400 si el tipo de cochera no es compatible
[Caja negra] POST /api/v1/reservas devuelve 400 si la cochera ya tiene una reserva confirmada ese dia
[Caja negra] POST /api/v1/reservas devuelve 201 y queda CONFIRMADA cuando todo es valido
[Caja negra] POST /api/v1/reservas permite reservar una cochera ACCESIBLE con cualquier tipo de vehiculo
[Caja negra] GET /api/v1/reservas/{id} devuelve 404 si no existe
[Caja negra] GET /api/v1/reservas devuelve todas las reservas cargadas
```

## `integration/UserControllerTests.java`

```
/users/** should return 401 Unauthorized for anonymous users
/users/** should return 403 Forbidden for regular users
/users/activate returns 400 Bad Request for invalid email
/users/activate returns 200 OK for valid email
/users/inactive returns 200 OK with a set of inactive users
DELETE /users returns 400 Bad Request for caller email equal to deleted email
DELETE /users returns 200 OK for valid email
PUT /api/v1/usuarios/{id} actualiza nombre, telefono y authorities
PUT /api/v1/usuarios/{id} devuelve 404 si el usuario no existe
PUT /api/v1/usuarios/{id} devuelve 401 para anonimos
```

## `integration/VehiculoControllerTests.java`

Caja negra. Alta, formato de patente, y la edicion/borrado con control de propietario.

```
[Caja negra] endpoints de vehiculos devuelven 401 para anonimos
[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente tiene formato invalido
[Caja negra] POST /api/v1/vehiculos devuelve 404 si el visitante no existe
[Caja negra] POST /api/v1/vehiculos devuelve 201 y normaliza la patente a mayusculas
[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente ya existe
[Caja negra] GET /api/v1/vehiculos sin filtro devuelve todos los vehiculos
[Caja negra] GET /api/v1/vehiculos?visitanteId filtra solo los de ese visitante
[Caja negra] GET /api/v1/vehiculos/{id} devuelve 404 si no existe
[Caja negra] GET /api/v1/vehiculos/{id} devuelve 200 con el vehiculo cuando existe
[Caja negra] PUT /api/v1/vehiculos/{id} devuelve 403 si no es el dueño
[Caja negra] PUT /api/v1/vehiculos/{id} permite al dueño editar su vehiculo
[Caja negra] DELETE /api/v1/vehiculos/{id} devuelve 204 cuando no tiene reservas
```

## `integration/VisitanteControllerTests.java`

Caja negra. Foco en los endpoints `/me` de autoservicio.

```
[Caja negra] endpoints de visitantes devuelven 401 para anonimos
[Caja negra] POST /api/v1/visitantes devuelve 400 si falta el nombre
[Caja negra] POST /api/v1/visitantes devuelve 201 con datos validos
[Caja negra] POST /api/v1/visitantes devuelve 400 si el documento ya existe
[Caja negra] GET /api/v1/visitantes/{id} devuelve 404 si no existe
[Caja negra] GET /api/v1/visitantes/me devuelve 404 si la cuenta todavia no cargo su perfil
[Caja negra] GET /api/v1/visitantes/me devuelve el perfil vinculado a la cuenta autenticada
[Caja negra] POST /api/v1/visitantes/me crea el perfil vinculado a la cuenta autenticada
[Caja negra] POST /api/v1/visitantes/me devuelve 400 si la cuenta ya tiene un perfil cargado
[Caja negra] POST /api/v1/visitantes/me devuelve 400 si el documento ya esta en uso por otro visitante
[Caja negra] PUT /api/v1/visitantes/me actualiza telefono y email
[Caja negra] PUT /api/v1/visitantes/me devuelve 401 para anonimos
```

## `security/AppUserDetailsServiceTests.java`

Caja blanca. El puente AppUser -> UserDetails.

```
loadUserByUsername mapea las authorities del AppUser a GrantedAuthority
loadUserByUsername lanza UsernameNotFoundException si el email no existe
```

## `security/authenticationProvider/DevAuthenticationProviderTests.java`

Caja blanca. Desde el cambio de seguridad, **dev/test tambien valida la contraseña**.

```
autentica exitosamente cuando la contraseña matchea el hash
rechaza con BadCredentialsException cuando la contraseña no matchea
rechaza con BadCredentialsException cuando el email no existe
supports() solo acepta UsernamePasswordAuthenticationToken
```

## `security/authenticationProvider/ProdAuthenticationProviderTests.java`

Caja blanca. Rechaza con `BadCredentialsException` tanto si la clave no matchea como si el email no existe, para no filtrar que emails estan registrados.

```
autentica exitosamente cuando la contraseña matchea el hash
rechaza con BadCredentialsException cuando la contraseña no matchea
rechaza con BadCredentialsException (no UsernameNotFoundException) cuando el email no existe
```

## `service/AuthServiceTests.java`

```
register throws ValidationException when email already registered
register successfully creates a new user
createAndSendOTP throws NotFoundException when user not found
createAndSendOTP successfully creates and sends OTP
resetPassword throws NotFoundException when user not found
resetPassword throws OTPException when OTP is invalid
resetPassword throws OTPException when OTP is expired
resetPassword successfully resets the user's password
```

## `service/CocheraServiceTests.java`

Incluye la cancelacion automatica de reservas al deshabilitar una cochera.

```
crear lanza ValidationException si ya existe una cochera con ese numero
crear guarda la cochera cuando el numero no esta repetido
obtenerPorId lanza NotFoundException si no existe
obtenerPorId devuelve la cochera cuando existe
editar lanza NotFoundException si no existe
editar lanza ValidationException si el nuevo numero ya esta en uso por otra cochera
editar permite guardar sin chequear duplicados si el numero no cambia
eliminar lanza NotFoundException si no existe
eliminar lanza ValidationException si la cochera tiene reservas asociadas
eliminar borra la cochera cuando no tiene reservas asociadas
listarDisponibles excluye cocheras con una reserva confirmada en esa fecha
listarDisponibles filtra por tipo exacto de vehiculo cuando se indica
listarDisponibles incluye cocheras ACCESIBLE sin importar el tipo de vehiculo
editar cancela las reservas CONFIRMADA de la cochera al pasarla a DESHABILITADA
```

## `service/ReservaServiceTests.java`

Las mismas reglas que `ReservaControllerTests`, pero con mocks y aisladas del repositorio.

```
crear lanza NotFoundException si el visitante no existe
crear lanza ValidationException si el vehiculo no pertenece al visitante
crear lanza ValidationException si el tipo de cochera no es compatible con el vehiculo
crear permite una cochera ACCESIBLE para cualquier tipo de vehiculo
crear lanza ValidationException si la cochera ya tiene una reserva confirmada ese dia
crear guarda la reserva como CONFIRMADA cuando todas las validaciones pasan
```

## `service/UserServiceTests.java`

Incluye el borrado seguro: desvincula el visitante propio antes de borrar la cuenta.

```
activateUser throws NotFoundException when user not found
activateUser activates user successfully
getInactiveUsers returns set of inactive user emails
deleteUser throws RuntimeException when caller email is null
deleteUser throws NotFoundException when user not found
deleteUser throws ValidationException when user tries to delete themselves
deleteUser desvincula el visitante propio antes de borrar la cuenta, para no violar la FK
deleteUser no toca visitantes cuando la cuenta no tiene ninguno vinculado
```

## `service/VehiculoServiceTests.java`

Incluye `verificarPropietario`: un USER solo toca sus propios vehiculos, un ADMIN todos.

```
crear lanza NotFoundException si el visitante no existe
crear lanza ValidationException si ya existe un vehiculo con esa patente
crear normaliza la patente a mayusculas antes de guardar
obtenerPorId lanza NotFoundException si el vehiculo no existe
listarPorVisitante devuelve solo los vehiculos de ese visitante
editar lanza AccessDeniedException si quien pide no es ADMIN ni el dueño
editar permite al ADMIN modificar un vehiculo que no es suyo
eliminar lanza ValidationException si el vehiculo tiene reservas asociadas
```

## `service/VisitanteServiceTests.java`

Incluye el flujo `/me`: obtener, crear y actualizar el perfil propio.

```
crear lanza ValidationException si ya existe un visitante con el mismo documento
crear guarda el visitante cuando el documento no esta repetido
obtenerPorId lanza NotFoundException si el visitante no existe
obtenerPorId devuelve el visitante cuando existe
listar devuelve todos los visitantes
obtenerPropio lanza NotFoundException si la cuenta no tiene un visitante vinculado
obtenerPropio devuelve el visitante vinculado a la cuenta autenticada
crearPropio lanza ValidationException si la cuenta ya tiene un visitante cargado
crearPropio lanza ValidationException si el documento ya esta en uso por otro visitante
crearPropio crea el visitante vinculado a la cuenta autenticada
actualizarPropio lanza NotFoundException si la cuenta no tiene un visitante vinculado
actualizarPropio actualiza telefono y email sin tocar nombre ni documento
```

---

# Frontend (`aparcar-front/test/`)

## `api.test.jsx`

Los dos interceptores de `app/api.jsx`, incluida la **regresion del bug del login doble**.

```
setea el baseURL usando getEnv
agrega el Bearer token de la cookie JWT cuando no hay Authorization explicito
no agrega Authorization si no hay cookie JWT
NO pisa un Authorization ya seteado a mano (ej. Basic Auth de /login), aunque haya una cookie JWT vieja
ante un 401, borra la cookie JWT y redirige a /login
ante un error que no es 401, no toca la cookie ni redirige
```

## `components/LogoutButton.test.jsx`

El boton de cerrar sesion: limpia el store y navega a `/login`.

```
cierra la sesión y navega a /login
```

## `components/ReservasContent.test.jsx`

_26 tests._

El formulario de reserva, que comparten los dos dashboards. Cubre la **franja horaria** (arranque por defecto en el momento actual, rango invertido, varios dias) y la **regresion del bug de cache de vehiculos al hacer foco**.

```
muestra el mensaje de vacio cuando no hay reservas cargadas
lista las reservas existentes con su estado
al escribir una patente registrada, muestra el nombre del visitante y el tipo de vehiculo
al escribir una patente que no existe, avisa que no se encontro ningun vehiculo
la cochera queda deshabilitada hasta encontrar un vehiculo por patente
al resolver un vehiculo (con la franja ya cargada por defecto), pide las cocheras disponibles de ese tipo
el foco en el campo de patente vuelve a pedir vehiculos y visitantes
confirmar la reserva envia el visitanteId/vehiculoId resueltos por patente, junto con cochera y franja
el boton de confirmar reserva esta deshabilitado hasta elegir una cochera
cancela con POST /api/v1/reservas/{id}/cancelar
pide confirmacion y no hace nada si se cancela el dialogo
avisa que la ocupacion cambio
no ofrece cancelar una reserva que ya esta cancelada
el visitante tambien puede cancelar desde su dashboard
si el backend rechaza la cancelacion, muestra su mensaje
no pide el catalogo de visitantes
ofrece sus propias patentes en un desplegable en vez de un campo libre
si todavia no cargo ningun vehiculo, explica que hace falta uno para reservar
no manda visitanteId al reservar
lista solo la patente, sin el nombre del visitante
arranca con el desde en el momento actual y el hasta una hora despues
pide las cocheras libres mandando desde y hasta
permite estirar la franja a varios dias y vuelve a consultar disponibilidad
avisa si la franja queda invertida y no consulta disponibilidad con ella
muestra la franja de cada reserva en el listado, no una fecha suelta
muestra los dos dias cuando la franja cruza la medianoche
```

## `components/ThemeToggle.test.jsx`

_2 tests._

El boton de cambio de tema.

```
se hidrata al montar y muestra el ícono de luna en modo claro
al hacer click, alterna a oscuro y aplica la clase al <html>
```

## `dashboard-admin/PanelOperativo.test.jsx`

_1 tests._

El panel operativo del ADMIN. **Regresion del bug de la reserva que no se agregaba**: que el panel incluya el formulario de reservas, que el admin pueda crear una, y que la cuadricula de ocupacion se refresque despues.

```
muestra unicamente el alta de visitante, no la grilla vieja ni el formulario de reservas
```

## `dashboard-admin/VisitantesContent.test.jsx`

_12 tests._

El alta operativa del admin: crea cuenta, vehiculo y reserva en una sola llamada, con su franja.

```
consulta disponibilidad y reserva para la franja elegida, limpiando la cochera anterior
ignora respuestas de disponibilidad de una franja anterior y deshabilita la cochera sin franja
avisa al instante si la franja esta invertida, sin llamar al backend
muestra errores si se envia el formulario vacio
exige el email porque es con lo que el visitante inicia sesion
rechaza una patente con formato invalido
rechaza una patente de moto cuando el tipo elegido sigue siendo AUTO
pide las cocheras disponibles de hoy para el tipo de vehiculo elegido
manda cuenta, vehiculo y cochera en una sola llamada
avisa que la contraseña inicial es el documento
avisa al padre para que refresque ocupacion y reservas
si el alta falla (ej. documento duplicado), muestra el error del backend
```

## `dashboard-admin/cocheras/CocherasManagement.test.jsx`

_24 tests._

ABM de cocheras con sus filtros. El filtro por fecha se traduce al dia completo como rango.

```
muestra la navegación de regreso al panel
carga y lista las cocheras existentes
combina tipo y estado sin sector y recupera todas las cocheras al limpiar filtros
una respuesta anterior no reemplaza los resultados del filtro actual
el dropdown de sector se arma con los sectores reales, sin repetidos
filtra por sector pidiendole al backend, no en el cliente
filtra por tipo pidiendole al backend
sin fecha seleccionada, la columna de disponibilidad muestra un guion
al elegir una fecha, pide al backend el dia completo como rango y muestra Libre/Ocupada
crea una cochera nueva y refresca la lista y los sectores
muestra errores de validacion si falta numero o sector
al editar, precarga el formulario con los datos de la fila elegida
al deshabilitar una cochera antes HABILITADA, pide confirmacion; si se cancela, no llama a PUT
al deshabilitar y confirmar, llama a PUT con el nuevo estado
eliminar pide confirmacion, y si se cancela no llama a DELETE
eliminar, si se confirma, llama a DELETE y refresca la lista
el alta usa un dropdown de sector con las opciones reales del backend
elegir '+ Otro' en el alta muestra un input de texto libre para el sector nuevo
sin ningun sector cargado todavia, el alta arranca directo en modo texto libre
el lote arranca con una sola fila y permite agregar mas
no permite quitar la ultima fila del lote
envia el lote completo a POST /api/v1/cocheras/bulk
si el backend rechaza el lote completo, muestra su mensaje y no limpia el formulario
muestra errores de validacion por fila si falta el numero
```

## `dashboard-admin/usuarios/UserManagement.test.jsx`

_13 tests._

```
muestra la navegación de regreso al panel
carga y lista los usuarios existentes
conserva el boton Activar para usuarios inactivos sin mostrar la columna Estado
un usuario activo no muestra el boton Activar
activar un usuario llama a POST /users/activate con su email
muestra errores de validacion al crear un usuario con datos invalidos
crea un usuario con datos validos
no deja crear una cuenta sin documento
al editar, precarga nombre, telefono y los roles marcados
editar sin ningun rol marcado muestra el error de validacion
guarda los cambios de edicion con PUT /api/v1/usuarios/{id}
eliminar pide confirmacion y llama a DELETE /users con el email
eliminar cancelado no llama a DELETE
```

## `dashboard-user/MiPerfilContent.test.jsx`

_20 tests._

Los datos propios del visitante y la **gestion completa de sus vehiculos**, mas el cambio de contraseña.

```
muestra el estado de carga inicialmente
no ofrece ningun formulario de autoregistro: el perfil viene con la cuenta
muestra sus datos y sus vehiculos
pide sus vehiculos sin mandar visitanteId
si todavia no tiene ningun vehiculo, avisa que no cargo ninguno
un error al cargar el perfil muestra un toast de error
agregar un vehiculo con patente invalida muestra el error de formato
agregar un vehiculo con patente de auto para un tipo MOTO muestra el error especifico de moto
agrega un vehiculo propio sin mandar visitanteId y refresca la lista
el boton 'Editar mis datos' precarga telefono y email actuales
guarda los cambios de telefono/email con PUT /api/v1/visitantes/me
no deja vaciar el email, porque es con lo que se inicia sesion
edita un vehiculo existente con PUT /api/v1/vehiculos/{id}
elimina un vehiculo con confirmacion
el formulario esta oculto hasta tocar 'Cambiar contraseña'
manda la actual y la nueva a PUT /api/v1/visitantes/me/password
exige la contraseña actual
rechaza una contraseña nueva de menos de 8 caracteres
avisa si la repeticion no coincide
si el backend rechaza el cambio, muestra su mensaje
```

## `login/page.test.jsx`

_10 tests._

```
muestra los campos de email y contraseña y el boton de ingresar
muestra errores de validacion y no llama a la API si el email esta vacio
muestra error de validacion si la contraseña tiene menos de 6 caracteres
envia el login con Basic Auth (email:password en base64) y no como body JSON
con rol ADMIN en el JWT, redirige a /dashboard-admin
codifica las contraseñas Unicode en UTF-8 para HTTP Basic
con solo rol USER en el JWT, redirige a /dashboard-user
si la respuesta no trae header Authorization, muestra error y no redirige
ante un 401 del backend, muestra 'Email o contraseña incorrectos.'
ante otro error del backend, muestra el mensaje que devuelve el servidor
```

## `page.test.jsx`

_4 tests._

La landing publica: redireccion por rol si ya hay sesion, y el menu hamburguesa mobile.

```
muestra la landing con el boton de iniciar sesion cuando no hay sesion
con rol ADMIN autenticado, redirige a /dashboard-admin
con rol USER autenticado, redirige a /dashboard-user
el menu hamburguesa se abre y cierra en mobile
```

## `register/page.test.jsx`

_14 tests._

El registro publico de visitantes desde la pantalla de login.

```
crea solo la cuenta con datos normalizados y permite iniciar sesión
bloquea envíos repetidos mientras se crea la cuenta
permite mostrar y ocultar las contraseñas
```

## `store/authStore.test.js`

_8 tests._

```
setAuth decodifica las authorities del JWT (string separado por comas) a un array de roles
setAuth guarda el token en una cookie JWT
setAuth con un solo rol devuelve un array de un elemento (no un string suelto)
setAuth con un token invalido deja isAuthenticated en false y no revienta
logout borra la cookie JWT y limpia el estado
checkAuth restaura la sesion desde la cookie si el token todavia no expiro
checkAuth cierra la sesion si el token de la cookie ya expiro
checkAuth sin cookie deja la sesion como no autenticada pero hidratada
```

## `store/themeStore.test.js`

_4 tests._

El store de tema claro/oscuro.

```
hidratar arranca en light si no hay nada guardado
hidratar restaura el tema guardado en localStorage
alternar cambia de light a dark y persiste la preferencia
alternar dos veces vuelve a light
```

## `utils/env.test.js`

_3 tests._

```
devuelve el valor de window.__ENV cuando está presente (runtime)
ignora window.__ENV si la clave pedida no está definida ahí
no revienta si window.__ENV no existe
```

## `utils/patenteValidation.test.js`

_9 tests._

Los formatos de patente aceptados segun el tipo de vehiculo.

```
rechaza una patente con formato de auto para una MOTO
rechaza una patente con formato de moto para un AUTO
es case-insensitive
```


---

# Totales

| | Archivos | Tests |
|---|---|---|
| Backend | 28 | 295 |
| Frontend | 16 | 157 |
| **Total** | **44** | **452** |

Correr todo:

```bash
# Backend (usa H2 en memoria: no necesita Docker ni Postgres levantados)
cd aparcar-api-back && mvn test

# Si no tenes Maven instalado, con un contenedor temporal:
#   docker run --rm -v "${PWD}:/app" -w /app \
#     maven:3.9.9-eclipse-temurin-21-alpine mvn test

# Cobertura (JaCoCo) -> target/site/jacoco/index.html
cd aparcar-api-back && mvn verify

# Frontend
cd aparcar-front && npm test
cd aparcar-front && npm run test:watch
```

Los mismos comandos corren solos en cada pull request contra `main` o `dev`, via `.github/workflows/ci.yml`. El workflow detecta que mitad del repo cambio y ejecuta solo esa: el job de backend hace `mvn clean install` (compila y corre los tests) y el de frontend hace `npm ci`, `npm run lint`, `npm test` y `npm run build`.

---

# Nota: por que `LoginFlowTests` no usa MockMvc

`RateLimitFilter`, `JWTValidationFilter` y `JWTGeneratorFilter` deciden si aplicarse mirando `request.getServletPath()`. En el dispatch simulado de MockMvc ese valor queda vacio y no coincide con el de un despliegue real, asi que **esos tres filtros nunca se ejecutan bajo MockMvc** -- en silencio, sin error.

Por eso `LoginFlowTests` corre contra un servidor embebido real (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTemplate`): es la unica forma de que esos filtros se ejecuten de verdad durante un test. No es un bug de produccion, es una limitacion del entorno de test que conviene tener en cuenta antes de confiar en un test de esos tres filtros hecho con MockMvc.
