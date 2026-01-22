#!/bin/bash

MAX_RETRIES=30
RETRY_INTERVAL=2

check_backend() {
    docker exec prayer-backend wget -q --spider http://localhost:8080/actuator/health 2>/dev/null
    return $?
}

check_frontend() {
    docker exec prayer-frontend wget -q --spider http://localhost:80/ 2>/dev/null || \
    docker exec prayer-nginx wget -q --spider http://frontend:80/ 2>/dev/null
    return $?
}

check_redis() {
    docker exec prayer-redis redis-cli ping > /dev/null 2>&1
    return $?
}

echo "Checking service health..."

for i in $(seq 1 $MAX_RETRIES); do
    echo "Attempt $i/$MAX_RETRIES..."

    backend_ok=false
    frontend_ok=false
    redis_ok=false

    if check_backend; then
        echo "  [OK] Backend is healthy"
        backend_ok=true
    else
        echo "  [X] Backend is not responding"
    fi

    if check_frontend; then
        echo "  [OK] Frontend is healthy"
        frontend_ok=true
    else
        echo "  [X] Frontend is not responding"
    fi

    if check_redis; then
        echo "  [OK] Redis is healthy"
        redis_ok=true
    else
        echo "  [X] Redis is not responding"
    fi

    if $backend_ok && $frontend_ok && $redis_ok; then
        echo ""
        echo "All services are healthy!"
        exit 0
    fi

    sleep $RETRY_INTERVAL
done

echo ""
echo "Health check failed after $MAX_RETRIES attempts"
exit 1
