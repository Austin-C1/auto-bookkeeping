#!/bin/bash

# BlackCat V1 backend deployment script.
# Supports direct Java deployment and Docker deployment.

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

APP_NAME="blackcat-v1-backend"
JAR_NAME="blackcat-v1-backend-1.0.0.jar"
DEPLOY_DIR="./deploy"
PROFILE="${SPRING_PROFILES_ACTIVE:-prod}"

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_java() {
    if ! command -v java &> /dev/null; then
        error "Java is not installed. Please install Java 17 or newer."
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        error "Java version is too old. Java 17+ is required, current version: $JAVA_VERSION"
        exit 1
    fi

    info "Java check passed: $(java -version 2>&1 | head -n 1)"
}

build_app() {
    info "Building backend application..."

    if [ ! -x "./gradlew" ]; then
        error "Gradle Wrapper is missing or not executable."
        exit 1
    fi

    ./gradlew clean bootJar

    if [ ! -f "build/libs/$JAR_NAME" ]; then
        error "Build failed. JAR file not found."
        exit 1
    fi

    info "Build completed: build/libs/$JAR_NAME"
}

deploy_java() {
    info "Preparing Java deployment..."

    mkdir -p "$DEPLOY_DIR"
    cp "build/libs/$JAR_NAME" "$DEPLOY_DIR/"

    cat > "$DEPLOY_DIR/start.sh" <<EOF
#!/bin/bash
cd \$(dirname \$0)

JVM_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
java \$JVM_OPTS -jar $JAR_NAME --spring.profiles.active=$PROFILE
EOF

    chmod +x "$DEPLOY_DIR/start.sh"

    cat > "$DEPLOY_DIR/${APP_NAME}.service" <<EOF
[Unit]
Description=BlackCat V1 Backend Service
After=network.target mysql.service

[Service]
Type=simple
User=\${USER}
WorkingDirectory=$DEPLOY_DIR
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -XX:+UseG1GC -jar $JAR_NAME --spring.profiles.active=$PROFILE
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

    info "Java deployment files created in: $DEPLOY_DIR"
    info "Start command: cd $DEPLOY_DIR && ./start.sh"
    info "Systemd command: sudo cp ${APP_NAME}.service /etc/systemd/system/ && sudo systemctl start ${APP_NAME}"
}

deploy_docker() {
    info "Preparing Docker deployment..."

    if ! command -v docker &> /dev/null; then
        error "Docker is not installed."
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose is not installed."
        exit 1
    fi

    generate_random_string() {
        local length=${1:-32}
        openssl rand -hex "$length" 2>/dev/null || \
        tr -dc 'a-zA-Z0-9' < /dev/urandom | fold -w "$length" | head -n 1
    }

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

# Server port
SERVER_PORT=8000

# Security secrets
JWT_SECRET=${JWT_SECRET}
ENCRYPTION_KEY=${ENCRYPTION_KEY}
ADMIN_RESET_PASSWORD_KEY=${ADMIN_RESET_KEY}
EOF

        info ".env file created with random secrets."
        warn "Review and replace these values before production use:"
        warn "  DB_PASSWORD=${DB_PASSWORD:0:8}..."
        warn "  JWT_SECRET=${JWT_SECRET:0:8}..."
        warn "  ENCRYPTION_KEY=${ENCRYPTION_KEY:0:8}..."
        warn "  ADMIN_RESET_PASSWORD_KEY=${ADMIN_RESET_KEY:0:8}..."
        exit 1
    fi

    info "Building Docker images..."
    docker-compose build

    info "Starting services..."
    docker-compose up -d

    info "Docker deployment completed."
    info "View logs: docker-compose logs -f"
    info "Stop services: docker-compose down"
}

main() {
    echo "=========================================="
    echo "  BlackCat V1 Backend Deployment"
    echo "=========================================="
    echo ""

    DEPLOY_MODE="${1:-java}"

    case "$DEPLOY_MODE" in
        java)
            check_java
            build_app
            deploy_java
            ;;
        docker)
            deploy_docker
            ;;
        build)
            check_java
            build_app
            ;;
        *)
            echo "Usage: $0 [java|docker|build]"
            echo ""
            echo "  java   - build and prepare Java deployment (default)"
            echo "  docker - build and start Docker deployment"
            echo "  build  - build only"
            exit 1
            ;;
    esac
}

main "$@"
