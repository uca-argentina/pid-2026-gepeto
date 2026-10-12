# Build stage
FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm install
COPY . .

ENV TURBOPACK=0

RUN npm run build

# Runtime stage
FROM node:20-alpine AS runtime

WORKDIR /app

ENV NODE_ENV=production

COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

COPY scripts/entrypoint.sh ./scripts/entrypoint.sh
# El `sed` saca los retornos de carro: clonado en Windows, el script queda con
# finales CRLF y el shebang pasa a ser "#!/bin/sh", un interprete que no
# existe. El contenedor arrancaba en bucle con "no such file or directory",
# nombrando al script y no al interprete, que es lo que despista.
RUN sed -i 's/$//' ./scripts/entrypoint.sh && chmod +x ./scripts/entrypoint.sh

EXPOSE 3000

ENTRYPOINT ["./scripts/entrypoint.sh"]

CMD ["node", "server.js"]