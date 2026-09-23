FROM postgres:16.12-alpine

EXPOSE 5432

# Copio el script de inicialización de la base de datos
COPY dockerfiles/db-start.sh /docker-entrypoint-initdb.d/init-user-db.sh

# Cambio los permisos del script
RUN chmod +x /docker-entrypoint-initdb.d/init-user-db.sh

# Elimino el \r de los \r\n que pone Windows
# https://stackoverflow.com/questions/14219092/bash-script-bin-bashm-bad-interpreter-no-such-file-or-directory
RUN sed -i -e 's/\r$//' /docker-entrypoint-initdb.d/init-user-db.sh

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["postgres"]
