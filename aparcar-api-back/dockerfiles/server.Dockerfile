FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Las dependencias se bajan en una capa aparte, antes de copiar el codigo. Es
# el paso mas lento de todo el build y, mientras el pom.xml no cambie, tanto
# Docker como la cache de GitHub Actions lo reusan en vez de repetirlo.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-alpine

WORKDIR /app

# No correr como root: si alguna vez se escapa algo del proceso, que no tenga
# permisos sobre el contenedor.
RUN addgroup -S aparcar && adduser -S aparcar -G aparcar

COPY --from=build /app/target/*.jar server.jar

USER aparcar

EXPOSE 8080

# La JVM mira la memoria del contenedor, no la de la maquina. En una instancia
# chica el valor por defecto se pasa del limite y el kernel mata el proceso
# apenas entran unos pedidos. compose puede sobrescribir esta variable.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

# Forma shell a proposito: hace falta que $JAVA_OPTS se expanda antes de
# arrancar la JVM. Con la forma exec (["java", ...]) llegaria como texto.
CMD java $JAVA_OPTS -jar server.jar
