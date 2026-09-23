# AparcAR Front

Frontend de AparcAR — Sistema de Reserva de Estacionamientos — Proyecto Integral de Desarrollo.

Basado en un template interno de **Next.js 16** (App Router) con React 19, configurado con herramientas esenciales para acelerar el desarrollo y estandarizar la calidad del código.

## 🚀 Inicio Rápido

Para comenzar a trabajar con el proyecto, te recomendamos encarecidamente empezar por la **[Guía Kickstart](./kickstart.md)**. Allí encontrarás los prerrequisitos y pasos exactos para instalar el entorno local.

## 📁 Archivos y Herramientas Importantes

Para simplificar el desarrollo, hemos integrado múltiples herramientas que deberías conocer:

- **[`kickstart.md`](./kickstart.md)**: El manual de bienvenida e instrucciones iniciales del proyecto.
- **[`Taskfile.yml`](./Taskfile.yml)**: Define comandos de uso frecuente mediante [Task](https://taskfile.dev/). Te permite ejecutar cosas complejas con facilidad (ej: `task dev`, `task setup`).
- **[`.pre-commit-config.yaml`](./.pre-commit-config.yaml)**: Reglas de `pre-commit` para validar automáticamente y formatear tu código (eliminar espacios en blanco, verificar YAML y correr el linter propio de Next.js) antes de cada commit.
- **`.github/workflows/ci.yml`**: Flujo de trabajo de GitHub Actions para Integración Continua (CI) en tus Pull Requests.

## 💻 Desarrollo Base

Si no cuentas con `Task`, puedes utilizar los comandos nativos de React / Next.js de toda la vida:

```bash
npm install      # Instalar dependencias
npm run dev      # Iniciar servidor de desarrollo en http://localhost:3000
npm run lint     # Correr el linter (ESLint)
npm run build    # Crear la versión optimizada de producción
```

Para más detalles acerca del framework, puedes consultar la [Documentación oficial de Next.js](https://nextjs.org/docs).
