# Indice de tests

Que prueba cada archivo de test del proyecto, caso por caso. Ver `ARCHIVOS.md` para que hace cada archivo de codigo en general, y `AparcAR-Manual-Completo.pdf` para la explicacion conceptual de por que el proyecto testea asi (caja blanca vs caja negra, la trampa de MockMvc, los tests de regresion).

> Este archivo se mantiene a mano. Si agregas o borras un test, actualizalo: los totales del final tienen que seguir cerrando.
>
> Los conteos por archivo salen de lo que reportan los runners (Surefire y Vitest), no de contar metodos: un test parametrizado cuenta como un test por cada fila de datos.

---


# Backend (`aparcar-api-back/src/test/java/com/aparcar/api/`)

## `AparcarApiApplicationTests.java`

_1 tests._

```
contextLoads   (sin @DisplayName)
```

## `component/OTPCleanupTests.java`

_1 tests._

```
otpCleanup deletes all expired OTPs
```

## `component/ReservasVencidasTests.java`

_4 tests._

Caja blanca de la tarea que marca como FINALIZADA las reservas vencidas. Ojo con lo que **no** hace: no es lo que libera la cochera (eso sale del solapamiento de rangos, que funciona aunque la tarea nunca corra), solo mantiene el estado legible.

```
pasa a FINALIZADA las reservas confirmadas cuya franja ya termino
solo considera las reservas CONFIRMADAS
no escribe en la base cuando no hay nada vencido
marca todas las vencidas de una, no solo la primera
```

## `component/RevokedUserCacheTests.java`

_2 tests._

```
RevokedUserCache revokes and checks revoked users
RevokedUserCache rejects nulls
```

## `component/SpringEmailSenderTests.java`

_2 tests._

```
sendPlainTextEmail should send email successfully
sendPlainTextEmail should throw RuntimeException on error
```

## `config/ZonaHorariaConfigTests.java`

_3 tests._

Caja blanca de la zona horaria. Existen por un bug concreto: el contenedor arrancaba en UTC mientras el navegador mandaba hora local, y una reserva de las 16 a las 17 llegaba a un backend que creia que eran las 19, asi que la rechazaba por "terminada en el pasado".

```
fija la zona horaria configurada como default de la JVM
corrige un arranque en UTC para que la hora local sea la del predio
respeta una zona distinta si el despliegue la configura
```

## `entity/ModalidadReservaTests.java`

_10 tests._

```
la modalidad sale de la duracion, con los bordes en 12 h y 24 h   (parametrizado: 8 casos)
el horario de arranque no cambia la modalidad
una franja sin datos se considera franja horaria, sin romper
```

## `entity/ReservaSolapamientoTests.java`

_20 tests._

Caja blanca del predicado de solapamiento, que es donde vive toda la regla de "dos reservas no se pisan". Ataca los bordes directamente, sin servicio ni base: un `<=` de mas y dos reservas consecutivas dejarian de poder existir; uno de menos y se permitiria pisar un minuto.

```
seSolapaCon distingue superposicion real de franjas apenas contiguas   (parametrizado: 11 casos)
dos reservas consecutivas que se tocan en un extremo pueden coexistir
el solapamiento es simetrico: da igual cual se pregunta primero
una franja ya terminada no se pisa con nada posterior
estaVigenteEn incluye el inicio y excluye el fin   (parametrizado: 6 casos)
```

## `filters/JWTGeneratorFilterTests.java`

_4 tests._

Caja blanca. El filtro que arma el JWT, probado en aislamiento con mocks.

```
shouldNotFilter devuelve false solo para /login
doFilterInternal no agrega header Authorization si no hay autenticación
doFilterInternal genera un JWT con email y authorities cuando hay autenticación
El JWT expira 8 horas después de emitido
```

## `filters/RateLimitFilterTests.java`

_5 tests._

Caja blanca. El limitador de intentos: 5 por IP por minuto, buckets independientes.

```
shouldNotFilter deja pasar rutas que no son login/register/forgot-password
shouldNotFilter aplica rate limit a login, register y forgot-password
permite las primeras 5 requests de una misma IP
la 6ta request de la misma IP en la ventana recibe 429 y no llega al resto de la cadena
dos IPs distintas tienen buckets independientes
```

## `integration/AccesibilidadTests.java`

_13 tests._

🆕 Caja negra. Cocheras ACCESIBLE de punta a punta contra base H2 real. Cubre la declaracion de discapacidad al crear la cuenta (alta del admin y `/register`) y al editar el perfil (`PUT /me`, que sin el campo no la borra); quien puede reservar una ACCESIBLE (un USER con o sin la declaracion, y un ADMIN reservando a nombre de otro, donde cuenta la declaracion del dueño y no la del admin); y que ofrece `/disponibles` a cada uno: anonimo y ADMIN ven todas, un USER sin la declaracion no ve las ACCESIBLE.

```
[Caja negra] el alta con tieneDiscapacidad=true puede reservar una cochera ACCESIBLE
[Caja negra] el alta sin discapacidad a una ACCESIBLE devuelve 400 y no deja nada creado
[Caja negra] POST /register guarda la discapacidad declarada al crear la cuenta
[Caja negra] PUT /api/v1/visitantes/me permite declarar la discapacidad y GET /me la devuelve
[Caja negra] PUT /api/v1/visitantes/me sin el campo no borra la declaracion
[Caja negra] un USER sin discapacidad declarada no puede reservar una ACCESIBLE
[Caja negra] un USER con discapacidad declarada puede reservar una ACCESIBLE
[Caja negra] un ADMIN no puede reservar una ACCESIBLE a nombre de alguien sin discapacidad declarada
[Caja negra] un ADMIN puede reservar una ACCESIBLE a nombre de alguien con discapacidad declarada
[Caja negra] /disponibles no le ofrece ACCESIBLE a un USER sin discapacidad declarada
[Caja negra] /disponibles si le ofrece ACCESIBLE a un USER con discapacidad declarada
[Caja negra] /disponibles le muestra ACCESIBLE a un ADMIN (reserva para otros)
[Caja negra] /disponibles sigue siendo publico y le muestra ACCESIBLE a un anonimo
```

## `integration/AuthControllerTests.java`

_5 tests._

```
/register validates input
/register creates an active user with rol USER
/login returns username
/forgot-password sends OTP
/reset-password resets password with valid OTP
```

## `integration/CocheraAltaPorPlantaControllerTests.java`

_12 tests._

🆕 Caja negra. `POST /api/v1/cocheras/alta-por-planta`: seguridad (401/403), numeracion automatica que continua desde lo que ya hay en la base (ignorando numeros con formato viejo), dos altas seguidas que no repiten numeros, tipos en 0 que no se crean y estado inicial. Las validaciones (todo en 0, sin cantidades, cantidad negativa, sector vacio, tipo inexistente) devuelven 400 **sin crear nada**.

```
[Caja negra] POST alta-por-planta devuelve 401 para anonimos
[Caja negra] POST alta-por-planta devuelve 403 para USER sin rol ADMIN
[Caja negra] crea las cocheras pedidas por tipo y devuelve los numeros asignados
[Caja negra] continua la numeracion de cada tipo desde lo que ya hay en la base
[Caja negra] dos altas seguidas no repiten numeros: la segunda sigue donde termino la primera
[Caja negra] los tipos en 0 no se crean
[Caja negra] respeta el estado inicial indicado
[Caja negra] devuelve 400 si todas las cantidades son 0
[Caja negra] devuelve 400 si no se manda ninguna cantidad
[Caja negra] devuelve 400 y no crea nada si alguna cantidad es negativa
[Caja negra] devuelve 400 si el sector viene vacio
[Caja negra] devuelve 400 (no 500) si se manda un tipo de cochera que no existe
```

## `integration/CocheraControllerTests.java`

_39 tests._

Caja negra. CRUD completo de cocheras de punta a punta, contra base H2 real. Incluye `/sectores` y el alta en lote vieja `POST /bulk`, que sigue existiendo aunque el front ya usa `/alta-por-planta` (esa tiene su propio archivo de tests).

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
[Regresion] lista todas las cocheras y combina filtros sin exigir sector   (parametrizado: 10 casos)
[Caja negra] GET /api/v1/cocheras?tipo filtra por tipo exacto
[Caja negra] GET /api/v1/cocheras?sector filtra de forma parcial e insensible a mayusculas
[Caja negra] GET /api/v1/cocheras sin fecha devuelve disponibleEnFecha en null
[Caja negra] GET /api/v1/cocheras?fecha marca disponibleEnFecha segun reservas confirmadas
[Caja negra] POST /api/v1/cocheras/bulk devuelve 401 para anonimos
[Caja negra] POST /api/v1/cocheras/bulk devuelve 403 para USER sin rol ADMIN
[Caja negra] POST /api/v1/cocheras/bulk crea todas las cocheras del lote
[Caja negra] POST /api/v1/cocheras/bulk no crea ninguna si una cochera del lote es invalida (todo-o-nada)
[Caja negra] POST /api/v1/cocheras/bulk devuelve 400 y no crea nada si un numero ya existe en la base
[Caja negra] GET /api/v1/cocheras/sectores devuelve 401 para anonimos
[Caja negra] GET /api/v1/cocheras/sectores devuelve 403 para USER sin rol ADMIN
[Caja negra] GET /api/v1/cocheras/sectores devuelve los sectores distintos, sin repetidos
```

## `integration/DashboardAccessSecurityTests.java`

_9 tests._

Caja negra. La matriz de permisos: que rol puede pegarle a que endpoint.

```
GET /api/v1/visitantes, /vehiculos y /reservas devuelven 401 para anónimos
GET /api/v1/vehiculos y /reservas devuelven 200 para rol USER (los necesita dashboard-user)
GET /api/v1/visitantes devuelve 403 para rol USER: el catalogo es del ADMIN
GET /api/v1/visitantes, /vehiculos y /reservas devuelven 200 para rol ADMIN (los necesita dashboard-admin)
GET /api/v1/cocheras/disponibles es público
GET /api/v1/usuarios devuelve 401 para anónimos
GET /api/v1/usuarios devuelve 403 para rol USER (esta pantalla es solo de ADMIN)
GET /api/v1/usuarios devuelve 200 para rol ADMIN
El 403 devuelve el mismo formato JSON que el 401 (no un 404 ni un body vacío)
```

## `integration/LoginFlowTests.java`

_4 tests._

Caja negra contra un servidor embebido real (no MockMvc). Ver la nota al pie sobre `getServletPath()`.

```
login con credenciales válidas devuelve 200 y un JWT con el email y las authorities del usuario
login con un usuario que solo tiene rol USER devuelve un JWT con una sola authority
login con email inexistente devuelve 401 sin JWT
en el perfil de test/dev, una contraseña incorrecta devuelve 401 sin JWT
```

## `integration/RegistrationFlowTests.java`

_1 tests._

Caja negra del registro contra un servidor embebido real.

```
registerLoginAndAccessOwnProfileWithRealJwt   (sin @DisplayName)
```

## `integration/RegistrationTests.java`

_9 tests._

Caja negra del alta publica de visitantes.

```
anonymousRegistrationCreatesOnlyActiveUserVisibleToAdmin   (sin @DisplayName)
rejectsExistingEmailRegardlessOfCaseAndSpaces   (sin @DisplayName)
rejectsExistingDocumentEvenForInactiveAccounts   (sin @DisplayName)
rejectsInvalidInput   (sin @DisplayName)   (parametrizado: 5 casos)
rejectsPasswordsOverBcryptByteLimit   (sin @DisplayName)
```

## `integration/ReservaControllerTests.java`

_27 tests._

Caja negra. Las reglas de negocio de reservas contra DB real, incluida la **superposicion de franjas entre usuarios distintos** y la liberacion automatica de la cochera al vencer. ✏️ Reservar una cochera ACCESIBLE ahora exige que el dueño tenga declarada la discapacidad; el caso que ya existia la declara (el detalle esta en `AccesibilidadTests`).

```
[Caja negra] endpoints de reservas devuelven 401 para anonimos
[Caja negra] POST /api/v1/reservas devuelve 400 si falta la franja
[Caja negra] POST /api/v1/reservas devuelve 400 si la franja ya termino
[Caja negra] POST /api/v1/reservas devuelve 404 si el visitante no existe
[Caja negra] POST /api/v1/reservas devuelve 404 si la cochera no existe
[Caja negra] POST /api/v1/reservas devuelve 400 si el vehiculo no pertenece al visitante indicado
[Caja negra] POST /api/v1/reservas devuelve 400 si el tipo de cochera no es compatible
[Caja negra] POST /api/v1/reservas devuelve 400 si la cochera ya tiene una reserva confirmada ese dia
[Caja negra] POST /api/v1/reservas devuelve 201 y queda CONFIRMADA cuando todo es valido
[Caja negra] POST /api/v1/reservas permite reservar una cochera ACCESIBLE con cualquier tipo de vehiculo
[Caja negra] GET /api/v1/reservas/{id} devuelve 404 si no existe
[Caja negra] GET /api/v1/reservas devuelve todas las reservas cargadas
[Caja negra] POST /api/v1/reservas ignora el visitanteId ajeno cuando quien reserva es un USER
[Caja negra] POST /api/v1/reservas devuelve 400 si un USER intenta reservar con el vehiculo de otro
[Caja negra] GET /api/v1/reservas devuelve solo las reservas propias a un visitante
[Caja negra] GET /api/v1/reservas/{id} devuelve 403 si la reserva es de otro visitante
[Caja negra] POST /api/v1/reservas/{id}/cancelar libera la cochera sin borrar la reserva
[Caja negra] cancelar dos veces la misma reserva devuelve 400
[Caja negra] un visitante puede cancelar su propia reserva
[Caja negra] un visitante no puede cancelar la reserva de otro
[Caja negra] dos visitantes no pueden pisarse la misma cochera en franjas que se solapan
[Caja negra] dos visitantes pueden usar la misma cochera en franjas consecutivas
[Caja negra] se puede reservar una franja de varios dias
[Caja negra] una reserva ya terminada deja la cochera libre para una franja nueva
[Caja negra] /disponibles excluye una cochera ocupada durante parte de la franja
[Caja negra] el mismo vehiculo no puede reservar dos cocheras en franjas que se solapan
[Caja negra] POST /api/v1/reservas devuelve 400 si el fin es anterior al inicio
```

## `integration/UserControllerTests.java`

_10 tests._

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

_18 tests._

Caja negra. Alta, formato de patente, y la edicion/borrado con control de propietario.

```
[Caja negra] endpoints de vehiculos devuelven 401 para anonimos
[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente tiene formato invalido
[Caja negra] POST /api/v1/vehiculos devuelve 404 si el visitante no existe
[Caja negra] POST /api/v1/vehiculos devuelve 201 y normaliza la patente a mayusculas
[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente ya existe
[Caja negra] POST /api/v1/vehiculos ignora el visitanteId cuando quien carga es un USER
[Caja negra] GET /api/v1/vehiculos sin filtro devuelve todos los vehiculos al ADMIN
[Caja negra] GET /api/v1/vehiculos?visitanteId filtra solo los de ese visitante
[Caja negra] GET /api/v1/vehiculos devuelve solo los propios a un visitante
[Caja negra] GET /api/v1/vehiculos/{id} devuelve 404 si no existe
[Caja negra] GET /api/v1/vehiculos/{id} devuelve 200 con el vehiculo cuando existe
[Caja negra] PUT /api/v1/vehiculos/{id} devuelve 403 si no es el dueño
[Caja negra] PUT /api/v1/vehiculos/{id} permite al dueño editar su vehiculo
[Caja negra] DELETE /api/v1/vehiculos/{id} devuelve 204 cuando no tiene reservas
[Caja negra] POST /api/v1/vehiculos acepta formato anterior de MOTO (123ABC)
[Caja negra] POST /api/v1/vehiculos acepta formato Mercosur de MOTO (A123BCD)
[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente tiene formato de auto para una MOTO
[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente tiene formato de moto para un AUTO
```

## `integration/VisitanteControllerTests.java`

_19 tests._

Caja negra. Foco en el alta operativa del admin y en los endpoints `/me` de autoservicio.

```
[Regresion] el alta reserva la franja elegida y rechaza una ya vencida sin crear datos
[Caja negra] endpoints de visitantes devuelven 401 para anonimos
[Caja negra] un USER no puede listar visitantes ni dar de alta
[Caja negra] POST /api/v1/visitantes/alta devuelve 400 si falta el nombre
[Caja negra] POST /api/v1/visitantes/alta devuelve 400 si falta el email
[Caja negra] POST /api/v1/visitantes/alta crea cuenta, vehiculo y reserva de hoy
[Caja negra] el alta deja la cuenta activa, con rol USER y el documento como contraseña
[Caja negra] POST /api/v1/visitantes/alta devuelve 400 si el documento ya existe
[Caja negra] POST /api/v1/visitantes/alta devuelve 400 si el email ya esta registrado
[Caja negra] si la cochera ya esta ocupada, el alta no deja ninguna cuenta a medio crear
[Caja negra] GET /api/v1/visitantes/{id} devuelve 404 si no existe
[Caja negra] GET /api/v1/visitantes/me devuelve los datos de la cuenta autenticada
[Caja negra] PUT /api/v1/visitantes/me actualiza telefono y email
[Caja negra] PUT /api/v1/visitantes/me devuelve 400 si el email ya lo usa otra cuenta
[Caja negra] PUT /api/v1/visitantes/me/password cambia la contraseña y deja entrar con la nueva
[Caja negra] PUT /api/v1/visitantes/me/password devuelve 400 si la contraseña actual no coincide
[Caja negra] PUT /api/v1/visitantes/me/password devuelve 400 si la contraseña nueva es muy corta
[Caja negra] PUT /api/v1/visitantes/me/password devuelve 401 para anonimos
[Caja negra] PUT /api/v1/visitantes/me devuelve 401 para anonimos
```

## `security/VisitanteDetailsServiceTests.java`

_2 tests._

Caja blanca. El puente Visitante -> UserDetails.

```
loadUserByUsername mapea las authorities del Visitante a GrantedAuthority
loadUserByUsername lanza UsernameNotFoundException si el email no existe
```

## `security/authenticationProvider/DevAuthenticationProviderTests.java`

_4 tests._

Caja blanca. Desde el cambio de seguridad, **dev/test tambien valida la contraseña**.

```
autentica exitosamente cuando la contraseña matchea el hash
rechaza con BadCredentialsException cuando la contraseña no matchea
rechaza con BadCredentialsException cuando el email no existe
supports() solo acepta UsernamePasswordAuthenticationToken
```

## `security/authenticationProvider/ProdAuthenticationProviderTests.java`

_3 tests._

Caja blanca. Rechaza con `BadCredentialsException` tanto si la clave no matchea como si el email no existe, para no filtrar que emails estan registrados.

```
autentica exitosamente cuando la contraseña matchea el hash
rechaza con BadCredentialsException cuando la contraseña no matchea
rechaza con BadCredentialsException (no UsernameNotFoundException) cuando el email no existe
```

## `service/AuthServiceTests.java`

_11 tests._

```
concurrentEmailConflictReturnsValidationError   (sin @DisplayName)
concurrentDocumentConflictReturnsValidationError   (sin @DisplayName)
register throws ValidationException when email already registered
register throws ValidationException when documento already registered
register successfully creates a new user
createAndSendOTP throws NotFoundException when user not found
createAndSendOTP successfully creates and sends OTP
resetPassword throws NotFoundException when user not found
resetPassword throws OTPException when OTP is invalid
resetPassword throws OTPException when OTP is expired
resetPassword successfully resets the user's password
```

## `service/CocheraAltaPorPlantaTests.java`

_12 tests._

🆕 Caja blanca de `CocheraService.crearPorPlanta`, con mocks: armado de las cocheras por tipo en orden fijo, continuacion desde los numeros existentes, estado por defecto HABILITADA, sector sin espacios de mas y validaciones. Incluye el choque de dos altas concurrentes: la restriccion UNIQUE de `numero` hace fallar a la segunda, y eso tiene que llegar como un 400 que pide reintentar, no como un 500.

```
crea la cantidad pedida de cada tipo, numerada desde 01 si no hay cocheras
devuelve las cocheras en orden fijo de tipo, sin importar el orden del mapa
cada tipo continua desde el maximo existente de su propio prefijo
los tipos con cantidad 0 o null no se crean
sin estado, las crea HABILITADA
respeta el estado inicial si se lo indica
guarda el sector sin espacios de mas
rechaza un sector vacio o en blanco sin guardar nada
rechaza si ningun tipo tiene cantidad mayor a 0
rechaza un mapa de cantidades vacio o ausente
rechaza una cantidad negativa aunque otro tipo tenga cantidad valida
si otra alta concurrente se quedo con el numero, avisa para reintentar
```

## `service/CocheraDisponiblesAccesiblesTests.java`

_6 tests._

🆕 Caja blanca de que cocheras ACCESIBLE ofrece `listarDisponibles` segun quien pregunta: anonimo y ADMIN ven todas; un visitante, solo si declaro discapacidad; si la cuenta del token ya no existe, se ocultan (lado conservador).

```
un anonimo ve tambien las ACCESIBLE: no hay persona contra la cual comparar
un ADMIN ve tambien las ACCESIBLE, sin consultar su propia cuenta
un visitante con discapacidad declarada ve las ACCESIBLE
a un visitante sin discapacidad declarada no se le ofrecen las ACCESIBLE
si la cuenta del token no existe, se ocultan las ACCESIBLE (lado conservador)
la version sin datos de quien pide sigue devolviendo las ACCESIBLE
```

## `service/CocheraServiceTests.java`

_22 tests._

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
listar sin filtros devuelve todas las cocheras con disponibleEnFecha en null
listar trata un sector en blanco como si no se hubiera pasado filtro
listar con fecha marca disponibleEnFecha=false para una cochera con reserva confirmada ese dia
crearEnLote lanza ValidationException si la lista esta vacia
crearEnLote lanza ValidationException si hay un numero repetido dentro del propio lote
crearEnLote lanza ValidationException si un numero ya existe en la base, sin guardar nada
crearEnLote guarda todas las cocheras del lote cuando son validas
listarSectores delega en el repository
```

## `service/NumeracionCocherasTests.java`

_30 tests._

🆕 Caja blanca de la numeracion automatica `{PREFIJO}-{secuencial}`. Es puro calculo: sin mocks ni base. Prefijos fijos por tipo (A, M, C, AC); continuar desde el **maximo** existente (no desde la cantidad ni desde el ultimo de la lista); que los prefijos no se crucen (`AC-05` no cuenta para AUTO, ni `AC-09` para CARGA); que los numeros con formato viejo o desconocido se ignoren sin romper; y el ancho minimo de 2 digitos sin limite superior (A-99 -> A-100).

```
cada tipo tiene su prefijo fijo   (parametrizado: 4 casos)
sin cocheras existentes, cada tipo arranca en 01
continua desde el maximo existente, no desde el ultimo ni desde la cantidad
el maximo de ACCESIBLE (AC-..) no se cuela en AUTO (A-..)
el maximo de AUTO (A-..) no se cuela en ACCESIBLE (AC-..)
el maximo de ACCESIBLE (AC-..) no se cuela en CARGA (C-..)
con todos los tipos mezclados, cada uno sigue su propia serie
ignora numeros con formato viejo o desconocido   (parametrizado: 14 casos)
los formatos desconocidos conviven con los validos sin romper el calculo
un secuencial absurdamente largo se ignora en vez de romper
tolera un null en la lista de existentes
rellena con cero hasta 2 digitos y despues crece sin limite
un numero con ceros de mas cuenta por su valor
cantidad 0 no genera ningun numero
```

## `service/ReservaAccesibilidadTests.java`

_6 tests._

🆕 Caja blanca de la regla que protege las cocheras ACCESIBLE al crear una reserva. Lo que cuenta es la declaracion del **dueño de la reserva**, no la de quien la carga: un ADMIN reservando para otra persona queda sujeto a lo que declaro esa persona. Las cocheras que no son ACCESIBLE no se ven afectadas.

```
un visitante sin discapacidad declarada no puede reservar una cochera ACCESIBLE
un visitante con discapacidad declarada puede reservar una cochera ACCESIBLE
si un ADMIN reserva para otro, cuenta la declaracion del dueño: sin ella se rechaza
si un ADMIN reserva para otro con discapacidad declarada, se permite sin mirar la cuenta del admin
la regla no afecta a las cocheras que no son ACCESIBLE
un flag en null se trata como 'sin discapacidad declarada'
```

## `service/ReservaServiceTests.java`

_32 tests._

Las mismas reglas que `ReservaControllerTests`, pero con mocks y aisladas del repositorio. Incluye la validacion de la franja horaria: rango invertido, duracion cero, franja ya vencida, y el vehiculo comprometido en otra cochera. ✏️ El caso de cochera ACCESIBLE ahora parte de un visitante con la discapacidad declarada (la regla completa esta en `ReservaAccesibilidadTests`).

```
crear lanza NotFoundException si el visitante no existe
crear lanza ValidationException si el vehiculo no pertenece al visitante
crear lanza ValidationException si el tipo de cochera no es compatible con el vehiculo
crear permite una cochera ACCESIBLE para cualquier tipo de vehiculo
crear lanza ValidationException si la cochera ya esta reservada en esa franja
crear guarda la reserva como CONFIRMADA cuando todas las validaciones pasan
crear ignora el visitanteId del dto cuando quien reserva no es ADMIN
crear lanza ValidationException si un ADMIN no indica a nombre de quien va la reserva
listar devuelve todas las reservas cuando quien pide es ADMIN
listar devuelve solo las reservas propias cuando quien pide no es ADMIN
obtenerPorId niega el acceso a una reserva de otro visitante
obtenerPorId deja al ADMIN ver cualquier reserva
cancelar pasa la reserva a CANCELADA sin borrarla
cancelar niega el acceso si la reserva es de otro visitante
cancelar deja al ADMIN dar de baja cualquier reserva
cancelar lanza ValidationException si la reserva ya estaba cancelada
cancelar lanza NotFoundException si la reserva no existe
crear rechaza una franja con fin anterior al inicio
crear rechaza una franja de duracion cero
crear rechaza una franja que termina en el pasado
crear acepta un inicio en el pasado mientras el fin siga siendo futuro
crear rechaza si el vehiculo ya tiene otra reserva en esa franja
crear consulta el solapamiento solo contra reservas CONFIRMADAS
cancelar rechaza una reserva cuya franja ya termino
crear rechaza un inicio que no cae en un bloque de 15 minutos
crear rechaza un fin que no cae en un bloque de 15 minutos
crear rechaza un horario con segundos, aunque los minutos sean multiplo de 15
crear acepta los cuatro bloques de la hora   (parametrizado: 4 casos)
crear acepta una reserva de exactamente 15 minutos
```

## `service/UserServiceTests.java`

_6 tests._

Incluye el borrado seguro: se bloquea si el visitante tiene reservas registradas, y se llevan sus vehiculos junto con la cuenta.

```
activateUser throws NotFoundException when user not found
getInactiveUsers returns set of inactive user emails
deleteUser lanza ValidationException si el visitante tiene reservas registradas
deleteUser borra los vehiculos del visitante junto con su cuenta
updateUser rechaza un documento que ya usa otro visitante
updateUser actualiza los datos y los roles del visitante
```

## `service/VehiculoServiceTests.java`

_16 tests._

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
crear acepta ambos formatos vigentes de patente para AUTO   (parametrizado: 2 casos)
crear acepta ambos formatos vigentes de patente para MOTO   (parametrizado: 2 casos)
crear acepta ambos formatos vigentes de patente para CARGA (mismo esquema que AUTO)   (parametrizado: 2 casos)
crear rechaza una patente con formato de auto para un vehiculo MOTO
crear rechaza una patente con formato de moto para un vehiculo AUTO
```

## `service/VisitanteDiscapacidadTests.java`

_6 tests._

🆕 Caja blanca de como se guarda y se edita la declaracion de discapacidad: en el alta del admin (queda en false si no viene), al editar el propio perfil (marcar, desmarcar, y sin el campo no se toca) y al leerlo para precargar el formulario.

```
altaConReserva guarda la discapacidad cuando el admin la marca
altaConReserva deja la discapacidad en false si el admin no la manda
actualizarPropio permite al visitante declarar su discapacidad
actualizarPropio permite al visitante quitar la declaracion
actualizarPropio sin el campo no le borra la declaracion a nadie
obtenerPropio devuelve la declaracion actual para precargar el formulario
```

## `service/VisitanteServiceTests.java`

_20 tests._

Incluye el alta operativa atomica (cuenta + vehiculo + reserva) y el cambio de contraseña propio. ✏️ La respuesta del visitante suma el campo `tieneDiscapacidad`.

```
altaConReserva lanza ValidationException si ya existe un visitante con el mismo documento
altaConReserva lanza ValidationException si ya existe una cuenta con ese email
altaConReserva usa el documento como contraseña inicial, hasheado
altaConReserva crea la cuenta activa y con rol USER
altaConReserva carga el vehiculo a nombre del visitante recien creado
altaConReserva reserva la cochera indicada para hoy
altaConReserva usa la franja elegida al crear la reserva
altaConReserva propaga el error de la reserva en vez de dejar la cuenta creada
obtenerPorId lanza NotFoundException si el visitante no existe
obtenerPorId devuelve el visitante cuando existe
listar devuelve todos los visitantes
obtenerPropio lanza NotFoundException si no existe una cuenta con ese email
obtenerPropio devuelve los datos de la cuenta autenticada
actualizarPropio lanza NotFoundException si no existe una cuenta con ese email
actualizarPropio actualiza telefono y email sin tocar nombre ni documento
actualizarPropio rechaza un email que ya usa otra cuenta
cambiarPasswordPropia rechaza el cambio si la contraseña actual no coincide
cambiarPasswordPropia rechaza una contraseña nueva igual a la actual
cambiarPasswordPropia guarda la contraseña nueva hasheada
cambiarPasswordPropia lanza NotFoundException si no existe una cuenta con ese email
```

---

# Frontend (`aparcar-front/test/`)

## `api.test.jsx`

_6 tests._

Los dos interceptores de `app/api.jsx`, incluida la **regresion del bug del login doble**.

```
setea el baseURL usando getEnv
agrega el Bearer token de la cookie JWT cuando no hay Authorization explicito
no agrega Authorization si no hay cookie JWT
NO pisa un Authorization ya seteado a mano (ej. Basic Auth de /login), aunque haya una cookie JWT vieja
ante un 401, borra la cookie JWT y redirige a /login
ante un error que no es 401, no toca la cookie ni redirige
```

## `components/AtajosJornada.test.jsx`

_8 tests._

Los atajos de media jornada y jornada completa. Lo que se verifica no es que sumen 12 o 24 horas cualesquiera, sino que caigan **justo** en los umbrales con los que el backend clasifica la modalidad: un atajo que dejara la reserva en 11 h 45 la etiquetaria como franja horaria y el boton estaria mintiendo.

```
ofrece media jornada y jornada completa con su duracion
media jornada deja el fin doce horas despues del inicio
jornada completa deja el fin al dia siguiente a la misma hora
los atajos caen justo en los umbrales que usa el backend
marca como activo el atajo que coincide con la franja actual
ninguno queda activo con una franja que no es una jornada
quedan deshabilitados si todavia no hay inicio
el resultado sigue cayendo en un bloque de 15 minutos
```

## `components/LogoutButton.test.jsx`

_1 tests._

El boton de cerrar sesion: limpia el store y navega a `/login`.

```
cierra la sesión y navega a /login
```

## `components/OcupacionCocheras.test.jsx`

_29 tests._

La vista de ocupacion del panel admin: dia elegido con navegacion y boton Hoy, agrupacion por tipo o por piso, contador de ocupadas sobre el total, el detalle de cada ocupacion, y las acciones de deshabilitar/habilitar una cochera y cancelar una reserva, siempre con confirmacion.

```
arranca en el día de hoy
arma un desplegable por cada tipo que exista, sin lista fija
la pestaña por piso agrupa los mismos datos por sector
el contador dice cuántas están ocupadas sobre el total
no vuelve a pedir datos al cambiar de pestaña o de día
muestra patente, horario, modalidad y mail de cada ocupación
las cocheras libres también figuran
el filtro deja solo las reservadas y se puede volver atrás
hasta que no se elige un grupo, invita a elegir uno
un grupo sin nada ocupado muestra sus cocheras como libres
con el filtro puesto y nada reservado, lo dice
volver a tocar el mismo grupo lo cierra
el día siguiente muestra sus propias reservas
el día anterior sigue mostrando lo que ya terminó
una reserva que cruza la medianoche aparece en los dos días
el botón Hoy vuelve al día actual y desaparece cuando ya estás ahí
una reserva cancelada no ocupa
sin cocheras cargadas lo dice
si falla la carga avisa y no rompe
vuelve a pedir los datos cuando cambia refreshKey
deshabilita una cochera mandando la cochera entera, no solo el estado
una cochera fuera de servicio se vuelve a habilitar desde el mismo lugar
al deshabilitar avisa cuántas reservas se van a cancelar
sin reservas no amenaza con cancelar nada
si el backend rechaza el cambio lo dice y no miente
cancela y vuelve a pedir los datos
si se dice que no, no cancela
una reserva finalizada no ofrece cancelar
el confirm nombra la patente y la cochera, para no cancelar la que no era
```

## `components/ReservasContent.test.jsx`

_45 tests._

El formulario de reserva, que comparten los dos dashboards. Cubre la **franja horaria** (arranque por defecto en el bloque de 15 en curso, rango invertido, varios dias), los atajos de jornada llevados hasta el campo, la modalidad que muestra el listado y la **regresion del bug de cache de vehiculos al hacer foco**. ✏️ Suma las **cocheras accesibles**: en modo admin se ocultan si el dueño del vehiculo no declaro discapacidad, explicando por que (y si el dato no viene, no se oculta nada); en modo visitante se muestra lo que ya filtro el backend; y el rechazo del backend llega tal cual al usuario.

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
arranca en el bloque de 15 en curso y propone una hora de duracion
los campos declaran el paso de 15 minutos
un horario fuera de bloque se baja al bloque en curso
pide las cocheras libres mandando desde y hasta
cambiar la franja vuelve a consultar disponibilidad
avisa al instante si la franja queda invertida
muestra la franja de cada reserva en el listado, no una fecha suelta
muestra los dos dias cuando la franja cruza la medianoche
muestra la modalidad que manda el backend
traduce las tres modalidades
una reserva sin modalidad no rompe el listado
media jornada deja el campo Hasta doce horas despues del inicio
jornada completa lleva el fin al dia siguiente
usar un atajo vuelve a consultar disponibilidad
por defecto los apila, como venía
en columnas agrupa el alta y el listado en dos bloques
el alta va antes que el listado en el DOM
en columnas se reserva igual que apilado
el listado sigue mostrándose en columnas
admin: oculta las ACCESIBLE si el dueño no declaró discapacidad y lo explica
admin: ofrece las ACCESIBLE si el dueño declaró discapacidad
admin: si el visitante no trae el campo, no oculta nada
admin: si solo quedaban ACCESIBLE, avisa que no hay cocheras para esa persona
visitante: muestra lo que devuelve el backend sin filtrar de nuevo
si el backend rechaza la reserva de una ACCESIBLE, muestra su mensaje tal cual
```

## `components/ThemeToggle.test.jsx`

_2 tests._

El boton de cambio de tema.

```
se hidrata al montar y muestra el ícono de luna en modo claro
al hacer click, alterna a oscuro y aplica la clase al <html>
```

## `dashboard-admin/PanelOperativo.test.jsx`

_2 tests._

El panel operativo del ADMIN. **Regresion del bug de la reserva que no se agregaba**: que el panel incluya el formulario de reservas, que el admin pueda crear una, y que la cuadricula de ocupacion se refresque despues.

```
abre con la ocupación de cocheras
no trae el alta de visitante ni el formulario de reservas
```

## `dashboard-admin/VisitantesContent.test.jsx`

_17 tests._

El alta operativa del admin: crea cuenta, vehiculo y reserva en una sola llamada, con su franja. ✏️ Suma el checkbox opcional de **discapacidad**: desmarcado por defecto, viaja en el alta, y las cocheras ACCESIBLE no se ofrecen hasta marcarlo (si se desmarca con una elegida, se deselecciona).

```
consulta disponibilidad y reserva para la franja elegida, limpiando la cochera anterior
ignora la respuesta de disponibilidad de una franja que ya cambio
el horario elegido siempre cae en un bloque de 15 minutos
muestra errores si se envia el formulario vacio
exige el email porque es con lo que el visitante inicia sesion
rechaza una patente con formato invalido
rechaza una patente de moto cuando el tipo elegido sigue siendo AUTO
pide las cocheras disponibles de hoy para el tipo de vehiculo elegido
manda cuenta, vehiculo y cochera en una sola llamada
avisa que la contraseña inicial es el documento
avisa al padre para que refresque ocupacion y reservas
si el alta falla (ej. documento duplicado), muestra el error del backend
muestra el checkbox de discapacidad desmarcado por defecto
sin marcarlo, manda tieneDiscapacidad: false
marcado, manda tieneDiscapacidad: true y permite reservar una ACCESIBLE
oculta las cocheras ACCESIBLE hasta que se marca el checkbox
si se desmarca con una ACCESIBLE elegida, la deselecciona
```

## `dashboard-admin/cocheras/CocherasManagement.test.jsx`

_31 tests._

ABM de cocheras con sus filtros. El filtro por fecha se traduce al dia completo como rango. ✏️ El **alta por planta** reemplazo al alta fila por fila: sector de la lista o nuevo, una cantidad por tipo arrancando en 0, boton deshabilitado con todo en 0, el payload que va a `/alta-por-planta` (solo los tipos con cantidad), los numeros que asigno el backend mostrados tal cual y agrupados por tipo, y el error del backend en el toast sin borrar lo cargado.

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
sin ningun sector cargado todavia, el alta (y el alta por planta) arrancan directo en modo texto libre
las cuatro cantidades arrancan en 0
con todas las cantidades en 0, el boton de crear queda deshabilitado
al cargar alguna cantidad se habilita y muestra el total a crear
el sector del alta por planta usa el mismo dropdown de sectores reales, con opcion de uno nuevo
envia sector y cantidades por tipo (sin los que quedaron en 0) a POST alta-por-planta
permite crear la planta en un sector nuevo con '+ Otro'
muestra los numeros que asigno el backend, agrupados por tipo
despues de crear, vuelve las cantidades a 0 y refresca la lista
si el backend rechaza el pedido, muestra su mensaje y no borra lo cargado
exige elegir un sector antes de enviar
no envia una cantidad negativa
ya no ofrece la carga fila por fila ni llama al endpoint /bulk
```

## `dashboard-admin/paginas.test.jsx`

_6 tests._

Las paginas `/dashboard-admin/reservas` y `/dashboard-admin/visitantes`: que exijan el rol ADMIN, que secciones arma cada una y la navegacion de regreso.

```
/dashboard-admin/reservas > exige el rol ADMIN
/dashboard-admin/reservas > trae el alta y el listado, sin el panel de ocupación
/dashboard-admin/reservas > los muestra en dos columnas
/dashboard-admin/visitantes > exige el rol ADMIN
/dashboard-admin/visitantes > trae el alta de visitante
/dashboard-admin/visitantes > deja volver al panel
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

_29 tests._

Los datos propios del visitante y la **gestion completa de sus vehiculos**, mas el cambio de contraseña. ✏️ Suma la **declaracion de discapacidad**: el checkbox precargado con el valor actual (incluso si el backend no manda el campo), que viaja en el `PUT /me`, la insignia en sus datos, y el aviso al panel solo cuando el valor cambio, para que el formulario de reserva vuelva a pedir las cocheras.

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
el formulario de edicion muestra el checkbox de discapacidad
precarga el checkbox marcado si el perfil lo tiene declarado
precarga el checkbox desmarcado si el perfil no lo tiene declarado
si el perfil no trae el campo, el checkbox arranca desmarcado
al marcarlo manda tieneDiscapacidad: true en el PUT y avisa el cambio
al desmarcarlo manda tieneDiscapacidad: false en el PUT
si guarda sin tocar el checkbox, no avisa ningun cambio de discapacidad
muestra la insignia en sus datos cuando lo tiene declarado
no muestra la insignia cuando no lo tiene declarado
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
rechaza datos inválidos: {"nombre":"   "}
rechaza datos inválidos: {"documento":"   "}
rechaza datos inválidos: {"email":"invalido"}
rechaza datos inválidos: {"password":"123"}
rechaza datos inválidos: {"password":"        ","confirmPassword":"        "}
rechaza datos inválidos: {"password":"ááááááááááááááááááááááááááááááááááááá"}
rechaza datos inválidos: {"confirmPassword":"otra-clave"}
muestra el error 400 sin redirigir ni perder datos
muestra el error 400 sin redirigir ni perder datos
muestra el error 429 sin redirigir ni perder datos
muestra el error undefined sin redirigir ni perder datos
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

## `utils/ocupacion.test.js`

_27 tests._

La logica pura detras de la vista de ocupacion: que reservas ocupan cada dia (medianoche, varios dias, canceladas y finalizadas), la agrupacion por tipo y por piso, el orden natural de los numeros, y el formato de dias y horarios en hora local.

```
no se corre de dia por la zona horaria
cruza fin de mes y fin de año
respeta los años bisiestos
una reserva dentro del día lo ocupa
una reserva de otro día no lo ocupa
una reserva que termina a medianoche no ocupa el día siguiente
una reserva que arranca a medianoche ocupa ese día y no el anterior
una reserva a caballo de la medianoche ocupa los dos días
una reserva de varios días ocupa también los del medio
una reserva sin franja no rompe
un grupo sin reservas aparece igual, con su cochera libre
ordena las cocheras por numero de forma natural
cuenta aparte las cocheras fuera de servicio
agrupa por tipo y por piso sobre los mismos datos
un tipo o piso nuevo aparece sin tocar nada
una reserva cancelada no ocupa
una reserva finalizada sigue contando en su día
dos turnos en la misma cochera son una cochera ocupada y dos reservas
cada ocupación trae los cuatro datos que el admin necesita
una reserva finalizada no es cancelable
una reserva de una cochera que ya no está en el catálogo se muestra igual
aguanta listas vacías o ausentes
marca con flechas lo que viene de antes o sigue después
una reserva contenida en el día muestra las dos horas
hoy sale del reloj local, no de UTC
nombra hoy, ayer y mañana, y nada más
escribe el día en castellano
```

## `utils/patenteValidation.test.js`

_9 tests._

Los formatos de patente aceptados segun el tipo de vehiculo.

```
acepta ABC123 para AUTO
acepta AB123CD para AUTO
acepta ABC123 para CARGA
acepta AB123CD para CARGA
acepta 123ABC para MOTO
acepta A123BCD para MOTO
rechaza una patente con formato de auto para una MOTO
rechaza una patente con formato de moto para un AUTO
es case-insensitive
```

---
# Totales

| | Archivos | Tests |
|---|---|---|
| Backend | 36 | 394 |
| Frontend | 20 | 268 |
| **Total** | **56** | **662** |

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