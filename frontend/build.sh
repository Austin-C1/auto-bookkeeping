#!/bin/bash

# BlackCat V1 frontend build script.
# Supports optional API and WebSocket endpoints through environment variables.

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_node() {
    if ! command -v node &> /dev/null; then
        error "Node.js is not installed. Please install Node.js 18 or newer."
        exit 1
    fi

    NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
    if [ "$NODE_VERSION" -lt 18 ]; then
        error "Node.js version is too old. Node.js 18+ is required, current version: $(node -v)"
        exit 1
    fi

    info "Node.js check passed: $(node -v)"
}

create_env_file() {
    if [ -n "$VITE_API_URL" ] || [ -n "$VITE_WS_URL" ]; then
        info "Detected custom endpoint variables, creating .env.production..."

        if [ -n "$VITE_API_URL" ]; then
            API_URL="$VITE_API_URL"
            if [[ $API_URL == http* ]]; then
                PROTOCOL=$(echo "$API_URL" | sed -E 's|^([^:]+)://.*|\1|')
                if [ "$PROTOCOL" = "https" ]; then
                    WS_PROTOCOL="wss"
                else
                    WS_PROTOCOL="ws"
                fi
                HOST_PORT=$(echo "$API_URL" | sed -E 's|^[^:]+://([^/]+).*|\1|')
                WS_URL="${WS_PROTOCOL}://${HOST_PORT}"
            else
                error "VITE_API_URL must be in the form http://host:port or https://host:port"
                exit 1
            fi
        elif [ -n "$VITE_WS_URL" ]; then
            WS_URL="$VITE_WS_URL"
            if [[ $WS_URL == ws* ]]; then
                WS_PROTOCOL=$(echo "$WS_URL" | sed -E 's|^([^:]+)://.*|\1|')
                if [ "$WS_PROTOCOL" = "wss" ]; then
                    API_PROTOCOL="https"
                else
                    API_PROTOCOL="http"
                fi
                HOST_PORT=$(echo "$WS_URL" | sed -E 's|^[^:]+://([^/]+).*|\1|')
                API_URL="${API_PROTOCOL}://${HOST_PORT}"
            else
                error "VITE_WS_URL must be in the form ws://host:port or wss://host:port"
                exit 1
            fi
        fi

        if [ -n "$VITE_WS_URL" ]; then
            WS_URL="$VITE_WS_URL"
        fi

        cat > .env.production <<EOF
# Optional backend endpoints for cross-origin deployments.
VITE_API_URL=${VITE_API_URL:-}
VITE_WS_URL=${VITE_WS_URL:-${WS_URL:-}}
EOF

        info "Created .env.production"
        if [ -n "$VITE_API_URL" ]; then
            info "  API URL: $VITE_API_URL"
        fi
        if [ -n "$VITE_WS_URL" ] || [ -n "$WS_URL" ]; then
            info "  WebSocket URL: ${VITE_WS_URL:-$WS_URL}"
        fi
    else
        info "No custom endpoints set. The frontend will use relative /api and /ws paths."
    fi
}

build_app() {
    info "Building frontend application..."

    if [ ! -d "node_modules" ]; then
        info "Installing dependencies..."
        npm install
    fi

    info "Running build..."
    npm run build

    if [ ! -d "dist" ]; then
        error "Build failed. dist directory not found."
        exit 1
    fi

    info "Build completed: dist/"
    info "Build size: $(du -sh dist | cut -f1)"
}

main() {
    echo "=========================================="
    echo "  BlackCat V1 Frontend Build"
    echo "=========================================="
    echo ""

    check_node

    if [ "$1" = "--api-url" ] && [ -n "$2" ]; then
        export VITE_API_URL="$2"
        info "Using custom API URL: $VITE_API_URL"
    elif [ "$1" = "--ws-url" ] && [ -n "$2" ]; then
        export VITE_WS_URL="$2"
        info "Using custom WebSocket URL: $VITE_WS_URL"
    fi

    create_env_file

    if [ -z "$VITE_API_URL" ] && [ -z "$VITE_WS_URL" ]; then
        warn "Make sure your production server proxies /api and /ws to the backend."
    fi

    build_app

    echo ""
    info "Build completed."
    info "Deployment options:"
    if [ -n "$VITE_API_URL" ] || [ -n "$VITE_WS_URL" ]; then
        info "  Deploy dist/ to any static file server. The app will connect directly to the configured backend."
    else
        info "  1. Deploy dist/ to a static file server such as Nginx or Apache."
        info "  2. Proxy /api to the backend HTTP endpoint and /ws to the backend WebSocket endpoint."
    fi
    info "  3. Preview locally with: npx serve -s dist -l 3000"
    echo ""
    info "Examples:"
    info "  VITE_API_URL=http://your-backend.com:8000 ./build.sh"
    info "  ./build.sh --api-url http://your-backend.com:8000"
}

main "$@"
