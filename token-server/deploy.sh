#!/bin/bash

# ============================================
# Signaling Server Docker Helper Script
# Purpose: Common operations for the server
# ============================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

CONTAINER_NAME="signaling-server-prod"
IMAGE_NAME="voice-translation-signaling-server:latest"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Helper functions
log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Show usage
usage() {
    cat << EOF
Signaling Server Docker Helper

Usage: ./deploy.sh [COMMAND]

Commands:
  build               Build Docker image
  start               Start the container
  stop                Stop the container
  restart             Restart the container
  logs                View container logs (real-time)
  logs-all            View all container logs
  health              Check container health
  stats               Show resource usage
  shell               Open shell in container
  test                Test server connectivity
  clean               Clean up Docker resources
  update              Pull latest code and restart
  status              Show container status
  ps                  List containers
  help                Show this help message

Examples:
  ./deploy.sh build          # Build the image
  ./deploy.sh start          # Start the server
  ./deploy.sh logs           # View live logs
  ./deploy.sh health         # Check if healthy
  ./deploy.sh test           # Test connectivity

EOF
}

# Build Docker image
cmd_build() {
    log_info "Building Docker image..."
    cd "$SCRIPT_DIR"
    docker build -t "$IMAGE_NAME" .
    log_success "Image built successfully"
}

# Start container
cmd_start() {
    log_info "Starting container..."
    
    # Check if running
    if docker ps | grep -q "$CONTAINER_NAME"; then
        log_warning "Container already running"
        return 0
    fi
    
    # Check if exists but stopped
    if docker ps -a | grep -q "$CONTAINER_NAME"; then
        log_info "Starting existing container..."
        docker start "$CONTAINER_NAME"
    else
        log_info "Starting new container..."
        cd "$SCRIPT_DIR"
        docker-compose up -d
    fi
    
    log_success "Container started"
    sleep 2
    
    # Show status
    cmd_health
}

# Stop container
cmd_stop() {
    log_info "Stopping container..."
    
    if ! docker ps | grep -q "$CONTAINER_NAME"; then
        log_warning "Container not running"
        return 0
    fi
    
    docker stop "$CONTAINER_NAME"
    log_success "Container stopped"
}

# Restart container
cmd_restart() {
    log_info "Restarting container..."
    cmd_stop
    sleep 1
    cmd_start
}

# View logs
cmd_logs() {
    log_info "Showing container logs (Press Ctrl+C to exit)..."
    docker logs -f "$CONTAINER_NAME" 2>/dev/null || log_error "Container not found"
}

# View all logs
cmd_logs_all() {
    log_info "Showing all container logs..."
    docker logs "$CONTAINER_NAME" 2>/dev/null || log_error "Container not found"
}

# Check health
cmd_health() {
    log_info "Checking container health..."
    
    if ! docker ps | grep -q "$CONTAINER_NAME"; then
        log_error "Container not running"
        return 1
    fi
    
    # Check health status from Docker
    HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")
    log_info "Docker health status: $HEALTH"
    
    # Test HTTP endpoint
    if curl -s -m 5 http://localhost:3000/health > /dev/null 2>&1; then
        log_success "HTTP health check passed"
    else
        log_error "HTTP health check failed"
        return 1
    fi
    
    log_success "Container is healthy"
}

# Show resource usage
cmd_stats() {
    log_info "Container resource usage (Press Ctrl+C to exit)..."
    docker stats "$CONTAINER_NAME" --no-stream || log_error "Container not found"
}

# Open shell
cmd_shell() {
    log_info "Opening shell in container..."
    docker exec -it "$CONTAINER_NAME" sh || log_error "Container not found"
}

# Test connectivity
cmd_test() {
    log_info "Testing server connectivity..."
    
    # Test health endpoint
    log_info "Testing /health endpoint..."
    if RESPONSE=$(curl -s -m 5 http://localhost:3000/health); then
        log_success "✓ Health endpoint: $RESPONSE"
    else
        log_error "✗ Health endpoint failed"
        return 1
    fi
    
    # Test token endpoint
    log_info "Testing /token endpoint..."
    if RESPONSE=$(curl -s -X POST http://localhost:3000/token \
        -H "Content-Type: application/json" \
        -d '{"room":"test-room","identity":"test-user"}' \
        -m 5); then
        
        if echo "$RESPONSE" | grep -q "token"; then
            log_success "✓ Token endpoint working"
        else
            log_error "✗ Token endpoint failed: $RESPONSE"
            return 1
        fi
    else
        log_error "✗ Token endpoint connection failed"
        return 1
    fi
    
    log_success "All tests passed!"
}

# Clean up resources
cmd_clean() {
    log_warning "This will remove Docker resources. Continue? (y/n)"
    read -r RESPONSE
    
    if [[ "$RESPONSE" != "y" && "$RESPONSE" != "Y" ]]; then
        log_info "Cancelled"
        return 0
    fi
    
    log_info "Cleaning up stopped containers..."
    docker container prune -f
    
    log_info "Cleaning up dangling images..."
    docker image prune -f
    
    log_info "Cleaning up dangling volumes..."
    docker volume prune -f
    
    log_success "Cleanup complete"
}

# Update and restart
cmd_update() {
    log_info "Updating application..."
    
    log_info "Pulling latest code..."
    cd "$SCRIPT_DIR"
    git pull origin main || log_warning "Git pull failed or not a git repo"
    
    log_info "Building new image..."
    cmd_build
    
    log_info "Restarting container..."
    cmd_restart
    
    log_success "Update complete"
}

# Show status
cmd_status() {
    log_info "Container status..."
    
    if docker ps | grep -q "$CONTAINER_NAME"; then
        log_success "Container is RUNNING"
        
        # Show more details
        docker ps --filter "name=$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
        
    elif docker ps -a | grep -q "$CONTAINER_NAME"; then
        log_warning "Container is STOPPED"
        
        # Show more details
        docker ps -a --filter "name=$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}"
        
    else
        log_error "Container not found"
    fi
    
    # Show image
    log_info "Image status..."
    if docker images | grep -q "$IMAGE_NAME"; then
        docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.Created}}" | grep "$IMAGE_NAME" || echo "Image not found"
    else
        log_warning "Image not found. Run: ./deploy.sh build"
    fi
}

# List containers
cmd_ps() {
    log_info "All containers..."
    docker ps -a
}

# Main command handler
COMMAND="${1:-help}"

case "$COMMAND" in
    build)
        cmd_build
        ;;
    start)
        cmd_start
        ;;
    stop)
        cmd_stop
        ;;
    restart)
        cmd_restart
        ;;
    logs)
        cmd_logs
        ;;
    logs-all)
        cmd_logs_all
        ;;
    health)
        cmd_health
        ;;
    stats)
        cmd_stats
        ;;
    shell)
        cmd_shell
        ;;
    test)
        cmd_test
        ;;
    clean)
        cmd_clean
        ;;
    update)
        cmd_update
        ;;
    status)
        cmd_status
        ;;
    ps)
        cmd_ps
        ;;
    help)
        usage
        ;;
    *)
        log_error "Unknown command: $COMMAND"
        echo ""
        usage
        exit 1
        ;;
esac
