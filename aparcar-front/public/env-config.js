// Marcador de posicion. En produccion, scripts/entrypoint.sh reescribe este
// archivo al arrancar el contenedor con las variables NEXT_PUBLIC_* reales.
//
// Existe versionado para que en desarrollo el <script> que lo carga no de 404:
// con el objeto vacio, utils/env.js cae al valor que Next horneo en el build,
// que es justo lo que corresponde en local.
window.__ENV = {};
