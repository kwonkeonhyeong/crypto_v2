#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

DOMAIN=${1:-prayer.example.com}
EMAIL=${2:-admin@example.com}

echo "Initializing SSL certificate for $DOMAIN..."

# Certbot 디렉토리 생성
mkdir -p "$PROJECT_DIR/docker/certbot/conf"
mkdir -p "$PROJECT_DIR/docker/certbot/www"

# 더미 인증서 생성 (Nginx 시작용)
if [ ! -f "$PROJECT_DIR/docker/certbot/conf/live/$DOMAIN/fullchain.pem" ]; then
    echo "Creating dummy certificate..."
    mkdir -p "$PROJECT_DIR/docker/certbot/conf/live/$DOMAIN"
    openssl req -x509 -nodes -newkey rsa:4096 -days 1 \
        -keyout "$PROJECT_DIR/docker/certbot/conf/live/$DOMAIN/privkey.pem" \
        -out "$PROJECT_DIR/docker/certbot/conf/live/$DOMAIN/fullchain.pem" \
        -subj "/CN=$DOMAIN"
fi

# Nginx 시작
cd "$PROJECT_DIR/docker"
docker-compose -f docker-compose.prod.yml up -d nginx

echo "Waiting for Nginx to start..."
sleep 5

# Let's Encrypt 인증서 발급
docker-compose -f docker-compose.prod.yml run --rm certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email \
    -d "$DOMAIN"

# Nginx 재시작
docker-compose -f docker-compose.prod.yml restart nginx

echo "SSL certificate initialized for $DOMAIN"
echo ""
echo "Next steps:"
echo "1. Update docker/nginx/conf.d/default.conf to enable HTTPS server block"
echo "2. Update the server_name and ssl_certificate paths with your domain"
echo "3. Run: docker-compose -f docker-compose.prod.yml restart nginx"
