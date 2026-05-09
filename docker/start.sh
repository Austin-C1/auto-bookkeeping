#!/bin/bash

set -e

echo "========================================="
echo "  BlackCat V1 container startup"
echo "========================================="

DEFAULT_JWT_SECRET="change-me-in-production"
DEFAULT_ENCRYPTION_KEY="change-me-in-production"
DEFAULT_ADMIN_RESET_KEY="change-me-in-production"

check_security_config() {
    local errors=0

    if [ -z "$JWT_SECRET" ] || [ "$JWT_SECRET" = "$DEFAULT_JWT_SECRET" ]; then
        echo "Error: JWT_SECRET cannot use the default value '${DEFAULT_JWT_SECRET}'"
        errors=$((errors + 1))
    fi

    if [ -z "$ENCRYPTION_KEY" ] || [ "$ENCRYPTION_KEY" = "$DEFAULT_ENCRYPTION_KEY" ]; then
        echo "Error: ENCRYPTION_KEY cannot use the default value '${DEFAULT_ENCRYPTION_KEY}'"
        errors=$((errors + 1))
    fi

    if [ -z "$ADMIN_RESET_PASSWORD_KEY" ] || [ "$ADMIN_RESET_PASSWORD_KEY" = "$DEFAULT_ADMIN_RESET_KEY" ]; then
        echo "Error: ADMIN_RESET_PASSWORD_KEY cannot use the default value '${DEFAULT_ADMIN_RESET_KEY}'"
        errors=$((errors + 1))
    fi

    if [ $errors -gt 0 ]; then
        echo ""
        echo "Security configuration check failed. Container will not start."
        exit 1
    fi

    echo "Security configuration check passed"
}

check_security_config

cleanup() {
    echo "Shutdown signal received, cleaning up..."
    if [ -n "$BACKEND_PID" ]; then
        kill $BACKEND_PID 2>/dev/null || true
    fi
    nginx -s quit 2>/dev/null || true
    exit 0
}

trap cleanup SIGTERM SIGINT

echo "Starting backend service..."
java -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} &
BACKEND_PID=$!
echo "Backend service started (PID: $BACKEND_PID, Port: 8000)"

echo "Waiting for backend health..."
for i in {1..60}; do
    if curl -f http://localhost:8000/api/system/health > /dev/null 2>&1; then
        echo "Backend health check passed"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "Backend startup timeout"
        exit 1
    fi
    sleep 1
done

echo "Starting Nginx..."
echo "========================================="
echo "  Container startup complete"
echo "  - Backend service: http://localhost:8000"
echo "  - Frontend service: http://localhost:8080"
echo "========================================="

exec nginx -g "daemon off;"
