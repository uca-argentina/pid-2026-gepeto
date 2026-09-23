#!/bin/sh

# 1. Definir el archivo de salida
OUTPUT_FILE="./public/env-config.js"
# 2. Definir el archivo de entrada (puedes cambiarlo a .env.local, etc.)
INPUT_FILE="./.env"

echo "window.__ENV = {" > $OUTPUT_FILE

# 3. Leer el archivo línea por línea
# - 'grep -v' elimina líneas que empiezan con # (comentarios) o están vacías
grep -v '^#' $INPUT_FILE | grep -v '^$' | while read -r line; do
  
  # Solo procesamos si la variable empieza con NEXT_PUBLIC_
  if echo "$line" | grep -q "^NEXT_PUBLIC_"; then
    # Extraer clave (antes del =) y valor (después del =)
    key=$(echo "$line" | cut -d '=' -f 1)
    value=$(echo "$line" | cut -d '=' -f 2-)
    
    # Limpiar comillas si el .env ya las trae para no duplicarlas
    value=$(echo "$value" | sed 's/^"//;s/"$//')
    
    echo "  $key: \"$value\"," >> $OUTPUT_FILE
  fi

done

echo "};" >> $OUTPUT_FILE

# Ejecutar el comando que viene después (ej: npm start)
exec "$@"