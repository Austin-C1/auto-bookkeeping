#!/bin/bash

# BlackCat V1 all-in-one deployment script.
# Deploys the full stack with Docker.

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

check_docker() {
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed."
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose is not installed."
        exit 1
    fi

    info "Docker check passed."
}

generate_random_string() {
    local length=${1:-32}
    openssl rand -hex "$length" 2>/dev/null || \
    tr -dc 'a-zA-Z0-9' < /dev/urandom | fold -w "$length" | head -n 1
}

create_env_file() {
    if [ ! -f ".env" ]; then
        warn ".env file not found. Creating a sample file with random secrets."

        DB_PASSWORD=$(generate_random_string 32)
        JWT_SECRET=$(generate_random_string 64)
        ENCRYPTION_KEY=$(generate_random_string 64)
        ADMIN_RESET_KEY=$(generate_random_string 32)

        cat > .env <<EOF
# Database
DB_URL=jdbc:mysql://mysql:3306/blackcat_v1?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=${DB_PASSWORD}

# Spring profile
SPRING_PROFILES_ACTIVE=prod

# External HTTP port
SERVER_PORT=80

# Security secrets
JWT_SECRET=${JWT_SECRET}
ENCRYPTION_KEY=${ENCRYPTION_KEY}
ADMIN_RESET_PASSWORD_KEY=${ADMIN_RESET_KEY}

# Optional log levels
# LOG_LEVEL_ROOT=WARN
# LOG_LEVEL_APP=INFO
EOF

        info ".env file created with random secrets."
        warn "Review and replace these values before production use:"
        warn "  DB_PASSWORD=${DB_PASSWORD:0:8}..."
        warn "  JWT_SECRET=${JWT_SECRET:0:8}..."
        warn "  ENCRYPTION_KEY=${ENCRYPTION_KEY:0:8}..."
        warn "  ADMIN_RESET_PASSWORD_KEY=${ADMIN_RESET_KEY:0:8}..."
        exit 1
    fi
}

check_security_config() {
    DEFAULT_JWT_SECRET="change-me-in-production"
    DEFAULT_ENCRYPTION_KEY="change-me-in-production"
    DEFAULT_ADMIN_RESET_KEY="change-me-in-production"

    local jwt_secret=""
    local encryption_key=""
    local admin_reset_key=""

    if [ -f ".env" ]; then
        jwt_secret=$(grep "^JWT_SECRET=" .env 2>/dev/null | cut -d'=' -f2- | sed 's/^"//;s/"$//' || echo "")
        encryption_key=$(grep "^ENCRYPTION_KEY=" .env 2>/dev/null | cut -d'=' -f2- | sed 's/^"//;s/"$//' || echo "")
        admin_reset_key=$(grep "^ADMIN_RESET_PASSWORD_KEY=" .env 2>/dev/null | cut -d'=' -f2- | sed 's/^"//;s/"$//' || echo "")
    fi

    if [ -n "$JWT_SECRET" ]; then
        jwt_secret="$JWT_SECRET"
    fi
    if [ -n "$ENCRYPTION_KEY" ]; then
        encryption_key="$ENCRYPTION_KEY"
    fi
    if [ -n "$ADMIN_RESET_PASSWORD_KEY" ]; then
        admin_reset_key="$ADMIN_RESET_PASSWORD_KEY"
    fi

    local errors=0

    if [ -z "$jwt_secret" ] || [ "$jwt_secret" = "$DEFAULT_JWT_SECRET" ]; then
        error "JWT_SECRET must not use the placeholder value '${DEFAULT_JWT_SECRET}'."
        errors=$((errors + 1))
    fi

    if [ -z "$encryption_key" ] || [ "$encryption_key" = "$DEFAULT_ENCRYPTION_KEY" ]; then
        error "ENCRYPTION_KEY must not use the placeholder value '${DEFAULT_ENCRYPTION_KEY}'."
        errors=$((errors + 1))
    fi

    if [ -z "$admin_reset_key" ] || [ "$admin_reset_key" = "$DEFAULT_ADMIN_RESET_KEY" ]; then
        error "ADMIN_RESET_PASSWORD_KEY must not use the placeholder value '${DEFAULT_ADMIN_RESET_KEY}'."
        errors=$((errors + 1))
    fi

    if [ "$errors" -gt 0 ]; then
        echo ""
        error "Security configuration check failed. Deployment aborted."
        echo ""
        info "Generate secure values with:"
        info "  openssl rand -hex 32"
        info "  openssl rand -hex 64"
        exit 1
    fi

    info "Security configuration check passed."
}

deploy() {
    check_security_config

    USE_DOCKER_HUB="${USE_DOCKER_HUB:-false}"
    if [ "$USE_DOCKER_HUB" = "true" ]; then
        info "Using Docker Hub image..."
        info "Pulling latest image..."
        docker pull blackcat-v1:latest || warn "Failed to pull Docker Hub image. Falling back to local build."
        warn "Make sure docker-compose.yml uses image: blackcat-v1:latest when Docker Hub mode is enabled."
    else
        if [ -z "${DOCKER_VERSION}" ] && [ -f ".env" ]; then
            DOCKER_VERSION=$(grep "^DOCKER_VERSION=" .env 2>/dev/null | cut -d'=' -f2- | sed 's/^["'\'']//;s/["'\'']$//' | tr -d '\r')
        fi
        if [ -z "${DOCKER_VERSION}" ]; then
            CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "dev")
            DOCKER_VERSION=$(echo "$CURRENT_BRANCH" | tr '/' '-')
        fi
        export DOCKER_VERSION

        info "Building Docker image locally, version=${DOCKER_VERSION}..."

        mkdir -p backend/build/libs

        export VERSION="${DOCKER_VERSION}"
        export GIT_TAG="${DOCKER_VERSION}"
        export GITHUB_REPO_URL=

        docker-compose build
    fi

    info "Starting services..."
    docker-compose up -d

    info "Waiting for services to start..."
    sleep 5

    info "Service status:"
    docker-compose ps

    info "View logs: docker-compose logs -f"
    info "Stop services: docker-compose down"
}

main() {
    echo "=========================================="
    echo "  BlackCat V1 Deployment"
    echo "=========================================="
    echo ""

    if [ "$1" = "--use-docker-hub" ] || [ "$1" = "-d" ]; then
        export USE_DOCKER_HUB=true
        info "Docker Hub image mode enabled."
        echo ""
    fi

    check_docker
    create_env_file
    deploy

    echo ""
    info "Deployment completed."
    info "Access URL: http://localhost:${SERVER_PORT:-80}"
    echo ""

    if [ "$USE_DOCKER_HUB" != "true" ]; then
        if [ -z "${DOCKER_VERSION}" ] && [ -f ".env" ]; then
            DOCKER_VERSION=$(grep "^DOCKER_VERSION=" .env 2>/dev/null | cut -d'=' -f2- | sed 's/^["'\'']//;s/["'\'']$//' | tr -d '\r')
        fi
        if [ -z "${DOCKER_VERSION}" ]; then
            CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "dev")
            DOCKER_VERSION=$(echo "$CURRENT_BRANCH" | tr '/' '-')
        fi
        info "Local build version: ${DOCKER_VERSION}"
        info "For production, prefer a published image:"
        info "  ./deploy.sh --use-docker-hub"
        info "  or update docker-compose.yml to use image: blackcat-v1:latest"
    fi
}

main "$@"
