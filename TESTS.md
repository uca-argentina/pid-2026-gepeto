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
otpCleanupDeletesAllExpiredOTPs   (sin @DisplayName)
```

## `component/ReservasVencidasTests.java`

_4 tests._

Caja blanca de la tarea que marca como FINALIZADA las reservas vencidas. Ojo con lo que **no** hace: no es lo que libera la cochera (eso sale del solapamiento de rangos, que funciona aunque la tarea nunca corra), solo mantiene el estado legible.

```
marcaLasVencidasComoFinalizadas   (sin @DisplayName)
soloConsideraLasConfirmadas   (sin @DisplayName)
noEscribeSiNoHayVencidas   (sin @DisplayName)
marcaTodasLasVencidas   (sin @DisplayName)
```

## `component/RevokedUserCacheTests.java`

_2 tests._

```
testRevoke   (sin @DisplayName)
cacheRejectsNulls   (sin @DisplayName)
```

## `component/SpringEmailSenderTests.java`

_2 tests._

```
sendPlainTextEmailShouldSendEmailSuccessfully   (sin @DisplayName)
sendPlainTextEmailShouldThrowRuntimeExceptionOnError   (sin @DisplayName)
```

## `config/ZonaHorariaConfigTests.java`

_3 tests._

Caja blanca de la zona horaria. Existen por un bug concreto: el contenedor arrancaba en UTC mientras el navegador mandaba hora local, y una reserva de las 16 a las 17 llegaba a un backend que creia que eran las 19, asi que la rechazaba por "terminada en el pasado".

```
fijaLaZonaConfigurada   (sin @DisplayName)
corrigeUnArranqueEnUtc   (sin @DisplayName)
respetaOtraZonaConfigurada   (sin @DisplayName)
```

## `entity/ReservaSolapamientoTests.java`

_20 tests._

Caja blanca del predicado de solapamiento, que es donde vive toda la regla de "dos reservas no se pisan". Ataca los bordes directamente, sin servicio ni base: un `<=` de mas y dos reservas consecutivas dejarian de poder existir; uno de menos y se permitiria pisar un minuto.

```
seSolapaConCubreLosBordes   (sin @DisplayName)
reservasConsecutivasNoSePisan   (sin @DisplayName)
elSolapamientoEsSimetrico   (sin @DisplayName)
unaFranjaTerminadaNoOcupaMas   (sin @DisplayName)
estaVigenteEnCubreLosBordes   (sin @DisplayName)
```

## `filters/JWTGeneratorFilterTests.java`

_4 tests._

Caja blanca. El filtro que arma el JWT, probado en aislamiento con mocks.

```
shouldNotFilterOnlyAppliesToLogin   (sin @DisplayName)
doesNotSetHeaderWhenNoAuthentication   (sin @DisplayName)
generatesJwtWithEmailAndAuthoritiesWhenAuthenticated   (sin @DisplayName)
jwtExpiresEightHoursAfterIssued   (sin @DisplayName)
```

## `filters/RateLimitFilterTests.java`

_5 tests._

Caja blanca. El limitador de intentos: 5 por IP por minuto, buckets independientes.

```
shouldNotFilterSkipsUnrelatedPaths   (sin @DisplayName)
shouldFilterAppliesToSensitiveEndpoints   (sin @DisplayName)
allowsFirstFiveRequestsFromSameIp   (sin @DisplayName)
rejectsSixthRequestWithTooManyRequests   (sin @DisplayName)
differentIpsHaveIndependentBuckets   (sin @DisplayName)
```

## `integration/AuthControllerTests.java`

_5 tests._

```
tearDown   (sin @DisplayName)
registerValidatesInput   (sin @DisplayName)
registerCreatesActiveUser   (sin @DisplayName)
loginReturnsUsername   (sin @DisplayName)
forgotPasswordSendsOTP   (sin @DisplayName)
resetPasswordWithValidOTP   (sin @DisplayName)
```

## `integration/CocheraControllerTests.java`

_39 tests._

Caja negra. CRUD completo de cocheras de punta a punta, contra base H2 real.

```
gestionDevuelve401ParaAnonimos   (sin @DisplayName)
disponiblesEsPublicoParaAnonimos   (sin @DisplayName)
gestionDevuelve403ParaUsuarioSinAdmin   (sin @DisplayName)
crearDevuelve400SiFaltaNumero   (sin @DisplayName)
crearDevuelve201ConDatosValidos   (sin @DisplayName)
crearDevuelve400SiNumeroYaExiste   (sin @DisplayName)
listarDevuelveTodasLasCocheras   (sin @DisplayName)
obtenerPorIdDevuelve404SiNoExiste   (sin @DisplayName)
obtenerPorIdDevuelve200CuandoExiste   (sin @DisplayName)
editarDevuelve404SiNoExiste   (sin @DisplayName)
editarDevuelve200YActualiza   (sin @DisplayName)
editarDevuelve400SiNumeroYaEstaEnUso   (sin @DisplayName)
eliminarDevuelve404SiNoExiste   (sin @DisplayName)
eliminarDevuelve204CuandoNoTieneReservas   (sin @DisplayName)
eliminarDevuelve400SiTieneReservasAsociadas   (sin @DisplayName)
disponiblesExcluyeCocherasDeshabilitadas   (sin @DisplayName)
disponiblesFiltraPorTipoVehiculoCompatible   (sin @DisplayName)
listarConFiltrosIndependientes   (sin @DisplayName)
listarFiltraPorTipo   (sin @DisplayName)
listarFiltraPorSectorParcial   (sin @DisplayName)
listarSinFechaDevuelveDisponibleEnFechaNull   (sin @DisplayName)
listarConFechaMarcaDisponibilidad   (sin @DisplayName)
crearEnLoteDevuelve401ParaAnonimos   (sin @DisplayName)
crearEnLoteDevuelve403ParaUsuarioSinAdmin   (sin @DisplayName)
crearEnLoteCreaTodasLasCocherasDelLote   (sin @DisplayName)
[Caja negra] POST /api/v1/cocheras/bulk no crea ninguna si una cochera del lote es invalida (todo-o-nada)
crearEnLoteDevuelve400SiNumeroYaExisteEnLaBase   (sin @DisplayName)
listarSectoresDevuelve401ParaAnonimos   (sin @DisplayName)
listarSectoresDevuelve403ParaUsuarioSinAdmin   (sin @DisplayName)
listarSectoresDevuelveSectoresDistintos   (sin @DisplayName)
```

## `integration/DashboardAccessSecurityTests.java`

_9 tests._

Caja negra. La matriz de permisos: que rol puede pegarle a que endpoint.

```
sharedEndpointsRejectAnonymousUsers   (sin @DisplayName)
GET /api/v1/vehiculos y /reservas devuelven 200 para rol USER (los necesita dashboard-user)
visitantesCatalogIsAdminOnly   (sin @DisplayName)
GET /api/v1/visitantes, /vehiculos y /reservas devuelven 200 para rol ADMIN (los necesita dashboard-admin)
cocherasDisponiblesIsPublic   (sin @DisplayName)
usuariosRejectsAnonymousUsers   (sin @DisplayName)
GET /api/v1/usuarios devuelve 403 para rol USER (esta pantalla es solo de ADMIN)
usuariosAllowsAdminRole   (sin @DisplayName)
El 403 devuelve el mismo formato JSON que el 401 (no un 404 ni un body vacío)
```

## `integration/LoginFlowTests.java`

_4 tests._

Caja negra contra un servidor embebido real (no MockMvc). Ver la nota al pie sobre `getServletPath()`.

```
loginWithValidCredentialsReturnsJwtWithClaims   (sin @DisplayName)
loginWithUserOnlyRoleReturnsJwtWithSingleAuthority   (sin @DisplayName)
loginWithNonExistentEmailReturnsUnauthorized   (sin @DisplayName)
loginRejectsWrongPasswordInDevProfile   (sin @DisplayName)
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
rejectsInvalidInput   (sin @DisplayName)
rejectsPasswordsOverBcryptByteLimit   (sin @DisplayName)
```

## `integration/ReservaControllerTests.java`

_27 tests._

Caja negra. Las reglas de negocio de reservas contra DB real, incluida la **superposicion de franjas entre usuarios distintos** y la liberacion automatica de la cochera al vencer.

```
devuelve401ParaAnonimos   (sin @DisplayName)
crearDevuelve400SiFaltaFecha   (sin @DisplayName)
crearDevuelve400SiFechaEsPasada   (sin @DisplayName)
crearDevuelve404SiVisitanteNoExiste   (sin @DisplayName)
crearDevuelve404SiCocheraNoExiste   (sin @DisplayName)
crearDevuelve400SiVehiculoNoPerteneceAlVisitante   (sin @DisplayName)
crearDevuelve400SiTiposNoSonCompatibles   (sin @DisplayName)
crearDevuelve400SiCocheraYaEstaReservada   (sin @DisplayName)
crearDevuelve201YQuedaConfirmada   (sin @DisplayName)
crearPermiteCocheraAccesibleConCualquierVehiculo   (sin @DisplayName)
obtenerPorIdDevuelve404SiNoExiste   (sin @DisplayName)
listarDevuelveTodasLasReservas   (sin @DisplayName)
crearIgnoraElVisitanteIdAjenoCuandoEsUser   (sin @DisplayName)
crearDevuelve400SiUnUserUsaElVehiculoDeOtro   (sin @DisplayName)
listarDevuelveSoloLasPropiasAUnVisitante   (sin @DisplayName)
obtenerPorIdDevuelve403SiLaReservaEsDeOtro   (sin @DisplayName)
cancelarLiberaLaCocheraSinBorrarLaReserva   (sin @DisplayName)
cancelarDosVecesDevuelve400   (sin @DisplayName)
unVisitantePuedeCancelarSuPropiaReserva   (sin @DisplayName)
unVisitanteNoPuedeCancelarLaReservaDeOtro   (sin @DisplayName)
dosVisitantesNoPuedenPisarseLaMismaCochera   (sin @DisplayName)
dosVisitantesPuedenUsarLaMismaCocheraEnFranjasConsecutivas   (sin @DisplayName)
sePuedeReservarUnaFranjaDeVariosDias   (sin @DisplayName)
unaReservaTerminadaLiberaLaCochera   (sin @DisplayName)
disponiblesExcluyeCocheraOcupadaParcialmente   (sin @DisplayName)
elMismoVehiculoNoPuedeOcuparDosCocherasALaVez   (sin @DisplayName)
crearDevuelve400SiElFinEsAnteriorAlInicio   (sin @DisplayName)
```

## `integration/UserControllerTests.java`

_10 tests._

```
shouldReturnUnauthorizedForAnonymousUsers   (sin @DisplayName)
shouldReturnForbiddenForRegularUsers   (sin @DisplayName)
activateShouldReturnBadRequestForInvalidEmail   (sin @DisplayName)
activateShouldReturnOkForValidEmail   (sin @DisplayName)
inactiveShouldReturnOkWithASetOfInactiveUsers   (sin @DisplayName)
deleteShouldReturnBadRequestForInvalidEmail   (sin @DisplayName)
deleteShouldReturnOkForValidEmail   (sin @DisplayName)
updateUserUpdatesEditableFields   (sin @DisplayName)
updateUserReturnsNotFoundForUnknownId   (sin @DisplayName)
updateUserRejectsAnonymousUsers   (sin @DisplayName)
```

## `integration/VehiculoControllerTests.java`

_18 tests._

Caja negra. Alta, formato de patente, y la edicion/borrado con control de propietario.

```
devuelve401ParaAnonimos   (sin @DisplayName)
crearDevuelve400SiPatenteTieneFormatoInvalido   (sin @DisplayName)
crearDevuelve404SiVisitanteNoExiste   (sin @DisplayName)
crearDevuelve201YNormalizaPatente   (sin @DisplayName)
crearDevuelve400SiPatenteYaExiste   (sin @DisplayName)
crearIgnoraElVisitanteIdCuandoEsUser   (sin @DisplayName)
listarSinFiltroDevuelveTodosAlAdmin   (sin @DisplayName)
listarConFiltroDevuelveSoloLosDeEseVisitante   (sin @DisplayName)
listarDevuelveSoloLosPropiosAUnVisitante   (sin @DisplayName)
obtenerPorIdDevuelve404SiNoExiste   (sin @DisplayName)
obtenerPorIdDevuelve200CuandoExiste   (sin @DisplayName)
editarDevuelve403SiNoEsElDueño   (sin @DisplayName)
editarPermiteAlDueñoEditarSuVehiculo   (sin @DisplayName)
eliminarDevuelve204CuandoNoTieneReservas   (sin @DisplayName)
[Caja negra] POST /api/v1/vehiculos acepta formato anterior de MOTO (123ABC)
[Caja negra] POST /api/v1/vehiculos acepta formato Mercosur de MOTO (A123BCD)
crearDevuelve400SiPatenteEsDeAutoParaMoto   (sin @DisplayName)
crearDevuelve400SiPatenteEsDeMotoParaAuto   (sin @DisplayName)
```

## `integration/VisitanteControllerTests.java`

_19 tests._

Caja negra. Foco en el alta operativa del admin y en los endpoints `/me` de autoservicio.

```
altaRespetaYValidaLaFranjaElegida   (sin @DisplayName)
devuelve401ParaAnonimos   (sin @DisplayName)
unUserNoPuedeListarNiDarDeAlta   (sin @DisplayName)
altaDevuelve400SiFaltaNombre   (sin @DisplayName)
altaDevuelve400SiFaltaEmail   (sin @DisplayName)
altaCreaCuentaVehiculoYReserva   (sin @DisplayName)
altaDejaLaCuentaListaParaIniciarSesion   (sin @DisplayName)
altaDevuelve400SiDocumentoYaExiste   (sin @DisplayName)
altaDevuelve400SiEmailYaExiste   (sin @DisplayName)
altaNoDejaCuentaHuerfanaSiFallaLaReserva   (sin @DisplayName)
obtenerPorIdDevuelve404SiNoExiste   (sin @DisplayName)
obtenerPropioDevuelveLosDatosDeLaCuenta   (sin @DisplayName)
actualizarPropioActualizaTelefonoYEmail   (sin @DisplayName)
actualizarPropioDevuelve400SiElEmailYaEstaEnUso   (sin @DisplayName)
cambiarPasswordPropiaFuncionaDePuntaAPunta   (sin @DisplayName)
cambiarPasswordDevuelve400SiLaActualNoCoincide   (sin @DisplayName)
cambiarPasswordDevuelve400SiLaNuevaEsMuyCorta   (sin @DisplayName)
cambiarPasswordDevuelve401ParaAnonimos   (sin @DisplayName)
actualizarPropioDevuelve401ParaAnonimos   (sin @DisplayName)
```

## `security/VisitanteDetailsServiceTests.java`

_2 tests._

Caja blanca. El puente Visitante -> UserDetails.

```
loadUserByUsernameMapsAuthorities   (sin @DisplayName)
loadUserByUsernameThrowsWhenUserNotFound   (sin @DisplayName)
```

## `security/authenticationProvider/DevAuthenticationProviderTests.java`

_4 tests._

Caja blanca. Desde el cambio de seguridad, **dev/test tambien valida la contraseña**.

```
authenticatesWhenPasswordMatches   (sin @DisplayName)
rejectsWhenPasswordDoesNotMatch   (sin @DisplayName)
rejectsWithBadCredentialsWhenUserDoesNotExist   (sin @DisplayName)
supports() solo acepta UsernamePasswordAuthenticationToken
```

## `security/authenticationProvider/ProdAuthenticationProviderTests.java`

_3 tests._

Caja blanca. Rechaza con `BadCredentialsException` tanto si la clave no matchea como si el email no existe, para no filtrar que emails estan registrados.

```
authenticatesWhenPasswordMatches   (sin @DisplayName)
rejectsWhenPasswordDoesNotMatch   (sin @DisplayName)
rechaza con BadCredentialsException (no UsernameNotFoundException) cuando el email no existe
```

## `service/AuthServiceTests.java`

_11 tests._

```
concurrentEmailConflictReturnsValidationError   (sin @DisplayName)
concurrentDocumentConflictReturnsValidationError   (sin @DisplayName)
registerThrowsValidationExceptionWhenEmailAlreadyRegistered   (sin @DisplayName)
registerThrowsValidationExceptionWhenDocumentoAlreadyRegistered   (sin @DisplayName)
registerSuccessfullyCreatesNewUser   (sin @DisplayName)
createAndSendOTPThrowsNotFoundExceptionWhenUserNotFound   (sin @DisplayName)
createAndSendOTPSuccessfullyCreatesAndSendsOTP   (sin @DisplayName)
resetPasswordThrowsNotFoundExceptionWhenUserNotFound   (sin @DisplayName)
resetPasswordThrowsOTPExceptionWhenOTPIsInvalid   (sin @DisplayName)
resetPasswordThrowsOTPExceptionWhenOTPIsExpired   (sin @DisplayName)
resetPasswordSuccessfullyResetsUserPassword   (sin @DisplayName)
```

## `service/CocheraServiceTests.java`

_22 tests._

Incluye la cancelacion automatica de reservas al deshabilitar una cochera.

```
crearLanzaValidationExceptionSiNumeroYaExiste   (sin @DisplayName)
crearGuardaCocheraCuandoNumeroNoEstaRepetido   (sin @DisplayName)
obtenerPorIdLanzaNotFoundExceptionSiNoExiste   (sin @DisplayName)
obtenerPorIdDevuelveLaCocheraCuandoExiste   (sin @DisplayName)
editarLanzaNotFoundExceptionSiNoExiste   (sin @DisplayName)
editarLanzaValidationExceptionSiNumeroYaEstaEnUso   (sin @DisplayName)
editarPermiteGuardarSiNumeroNoCambia   (sin @DisplayName)
eliminarLanzaNotFoundExceptionSiNoExiste   (sin @DisplayName)
eliminarLanzaValidationExceptionSiTieneReservas   (sin @DisplayName)
eliminarBorraLaCocheraCuandoNoTieneReservas   (sin @DisplayName)
listarDisponiblesExcluyeCocherasReservadas   (sin @DisplayName)
listarDisponiblesFiltraPorTipoExacto   (sin @DisplayName)
listarDisponiblesIncluyeAccesibleParaCualquierTipo   (sin @DisplayName)
editarCancelaReservasConfirmadasAlDeshabilitar   (sin @DisplayName)
listarSinFiltrosDevuelveTodasConDisponibleEnFechaNull   (sin @DisplayName)
listarTrataSectorEnBlancoComoNull   (sin @DisplayName)
listarConFechaMarcaOcupadaCorrectamente   (sin @DisplayName)
crearEnLoteLanzaValidationExceptionSiListaVacia   (sin @DisplayName)
crearEnLoteLanzaValidationExceptionSiNumeroRepetidoEnElLote   (sin @DisplayName)
crearEnLoteLanzaValidationExceptionSiNumeroYaExisteEnLaBase   (sin @DisplayName)
crearEnLoteGuardaTodasLasCocherasCuandoSonValidas   (sin @DisplayName)
listarSectoresDelegaEnElRepository   (sin @DisplayName)
```

## `service/ReservaServiceTests.java`

_24 tests._

Las mismas reglas que `ReservaControllerTests`, pero con mocks y aisladas del repositorio. Incluye la validacion de la franja horaria: rango invertido, duracion cero, franja ya vencida, y el vehiculo comprometido en otra cochera.

```
crearLanzaNotFoundExceptionSiVisitanteNoExiste   (sin @DisplayName)
crearLanzaValidationExceptionSiVehiculoNoPerteneceAlVisitante   (sin @DisplayName)
crearLanzaValidationExceptionSiTiposNoSonCompatibles   (sin @DisplayName)
crearPermiteCocheraAccesibleParaCualquierVehiculo   (sin @DisplayName)
crearLanzaValidationExceptionSiCocheraYaEstaReservada   (sin @DisplayName)
crearGuardaReservaConfirmadaCuandoTodoEsValido   (sin @DisplayName)
crearIgnoraElVisitanteIdDelDtoSiNoEsAdmin   (sin @DisplayName)
crearLanzaValidationExceptionSiAdminNoIndicaVisitante   (sin @DisplayName)
listarDevuelveTodasParaAdmin   (sin @DisplayName)
listarDevuelveSoloLasPropiasParaVisitante   (sin @DisplayName)
obtenerPorIdNiegaElAccesoAUnaReservaAjena   (sin @DisplayName)
obtenerPorIdDejaAlAdminVerCualquierReserva   (sin @DisplayName)
cancelarPasaLaReservaACancelada   (sin @DisplayName)
cancelarNiegaElAccesoAUnaReservaAjena   (sin @DisplayName)
cancelarDejaAlAdminDarDeBajaCualquierReserva   (sin @DisplayName)
cancelarLanzaValidationExceptionSiYaEstabaCancelada   (sin @DisplayName)
cancelarLanzaNotFoundExceptionSiNoExiste   (sin @DisplayName)
crearRechazaFranjaInvertida   (sin @DisplayName)
crearRechazaFranjaDeDuracionCero   (sin @DisplayName)
crearRechazaFranjaEnteramenteVencida   (sin @DisplayName)
crearAceptaInicioPasadoConFinFuturo   (sin @DisplayName)
crearRechazaVehiculoComprometidoEnOtraCochera   (sin @DisplayName)
crearConsultaSolapamientoSoloContraConfirmadas   (sin @DisplayName)
cancelarRechazaUnaReservaYaTerminada   (sin @DisplayName)
```

## `service/UserServiceTests.java`

_10 tests._

Incluye el borrado seguro: se bloquea si el visitante tiene reservas registradas, y se llevan sus vehiculos junto con la cuenta.

```
activateUserThrowsNotFoundExceptionWhenUserNotFound   (sin @DisplayName)
activateUserSuccessfully   (sin @DisplayName)
getInactiveUsersReturnsSetOfInactiveUserEmails   (sin @DisplayName)
deleteUserThrowsRuntimeExceptionWhenCallerEmailIsNull   (sin @DisplayName)
deleteUserThrowsNotFoundExceptionWhenUserNotFound   (sin @DisplayName)
deleteUserThrowsValidationExceptionWhenUserTriesToDeleteThemselves   (sin @DisplayName)
deleteUserThrowsWhenVisitanteHasReservas   (sin @DisplayName)
deleteUserDeletesOwnVehiclesAlongWithTheAccount   (sin @DisplayName)
updateUserRejectsDocumentoAlreadyInUse   (sin @DisplayName)
updateUserUpdatesDataAndRoles   (sin @DisplayName)
```

## `service/VehiculoServiceTests.java`

_16 tests._

Incluye `verificarPropietario`: un USER solo toca sus propios vehiculos, un ADMIN todos.

```
crearLanzaNotFoundExceptionSiVisitanteNoExiste   (sin @DisplayName)
crearLanzaValidationExceptionSiPatenteYaExiste   (sin @DisplayName)
crearNormalizaPatenteAMayusculas   (sin @DisplayName)
obtenerPorIdLanzaNotFoundExceptionSiNoExiste   (sin @DisplayName)
listarPorVisitanteDevuelveVehiculosDelVisitante   (sin @DisplayName)
editarLanzaAccessDeniedExceptionSiNoEsElDueño   (sin @DisplayName)
editarPermiteAlAdminModificarCualquierVehiculo   (sin @DisplayName)
eliminarLanzaValidationExceptionSiTieneReservas   (sin @DisplayName)
crearAceptaAmbosFormatosParaAuto   (sin @DisplayName)
crearAceptaAmbosFormatosParaMoto   (sin @DisplayName)
crear acepta ambos formatos vigentes de patente para CARGA (mismo esquema que AUTO)
crearRechazaFormatoDeAutoParaMoto   (sin @DisplayName)
crearRechazaFormatoDeMotoParaAuto   (sin @DisplayName)
```

## `service/VisitanteServiceTests.java`

_20 tests._

Incluye el alta operativa atomica (cuenta + vehiculo + reserva) y el cambio de contraseña propio.

```
altaLanzaValidationExceptionSiDocumentoYaExiste   (sin @DisplayName)
altaLanzaValidationExceptionSiEmailYaExiste   (sin @DisplayName)
altaUsaElDocumentoComoContraseñaInicial   (sin @DisplayName)
altaCreaLaCuentaActivaYConRolUser   (sin @DisplayName)
altaCargaElVehiculoANombreDelVisitanteCreado   (sin @DisplayName)
altaReservaLaCocheraParaHoy   (sin @DisplayName)
altaReservaParaLaFranjaElegida   (sin @DisplayName)
altaPropagaElErrorDeLaReserva   (sin @DisplayName)
obtenerPorIdLanzaNotFoundExceptionSiNoExiste   (sin @DisplayName)
obtenerPorIdDevuelveVisitanteCuandoExiste   (sin @DisplayName)
listarDevuelveTodosLosVisitantes   (sin @DisplayName)
obtenerPropioLanzaNotFoundExceptionSiNoExisteLaCuenta   (sin @DisplayName)
obtenerPropioDevuelveLosDatosDeLaCuenta   (sin @DisplayName)
actualizarPropioLanzaNotFoundExceptionSiNoExisteLaCuenta   (sin @DisplayName)
actualizarPropioActualizaTelefonoYEmail   (sin @DisplayName)
actualizarPropioRechazaUnEmailYaEnUso   (sin @DisplayName)
cambiarPasswordRechazaSiLaActualNoCoincide   (sin @DisplayName)
cambiarPasswordRechazaSiLaNuevaEsIgualALaActual   (sin @DisplayName)
cambiarPasswordGuardaLaNuevaHasheada   (sin @DisplayName)
cambiarPasswordLanzaNotFoundExceptionSiNoExisteLaCuenta   (sin @DisplayName)
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

## `components/LogoutButton.test.jsx`

_1 tests._

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
