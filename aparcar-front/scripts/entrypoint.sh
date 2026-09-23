#!/bin/sh

echo "window.__ENV = {" > ./public/env-config.js
printenv | grep NEXT_PUBLIC_ | while read -r line; do
  key=$(echo "$line" | cut -d '=' -f 1)
  value=$(echo "$line" | cut -d '=' -f 2-)
  echo "  $key: \"$value\"," >> ./public/env-config.js
done
echo "};" >> ./public/env-config.js

exec "$@"
