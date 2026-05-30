# 🐳 Docker Commands Quick Reference

## Image Management

```bash
# Build image
docker build -t voice-translation-signaling-server:latest .

# List images
docker images

# Remove image
docker rmi voice-translation-signaling-server:latest

# Tag image for registry
docker tag voice-translation-signaling-server:latest myregistry/app:latest

# Push to registry
docker push myregistry/app:latest
```

## Container Lifecycle

```bash
# Run container (detached)
docker run -d --name signaling-server-prod \
  --restart=always \
  -p 3000:3000 \
  --env-file .env \
  voice-translation-signaling-server:latest

# Run container (interactive)
docker run -it voice-translation-signaling-server:latest bash

# List running containers
docker ps

# List all containers
docker ps -a

# Start stopped container
docker start signaling-server-prod

# Stop running container
docker stop signaling-server-prod

# Restart container
docker restart signaling-server-prod

# Remove container
docker rm signaling-server-prod

# Force stop and remove
docker stop signaling-server-prod && docker rm signaling-server-prod
```

## Logs & Debugging

```bash
# View container logs (last output)
docker logs signaling-server-prod

# Tail logs (real-time)
docker logs -f signaling-server-prod

# Show last N lines
docker logs --tail=50 signaling-server-prod

# Show logs with timestamps
docker logs -f -t signaling-server-prod

# Save logs to file
docker logs signaling-server-prod > container.log 2>&1

# Execute command in running container
docker exec signaling-server-prod npm list

# Open shell in running container
docker exec -it signaling-server-prod sh

# Copy files from container
docker cp signaling-server-prod:/app/logs ./local-logs
```

## Inspection & Monitoring

```bash
# Show container details
docker inspect signaling-server-prod

# View resource usage (live)
docker stats signaling-server-prod

# View container processes
docker top signaling-server-prod

# Check health status
docker inspect --format='{{json .State.Health}}' signaling-server-prod

# View container network settings
docker network inspect bridge

# Get container IP
docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' signaling-server-prod
```

## Docker Compose Commands

```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# View status
docker-compose ps

# View logs
docker-compose logs -f

# Restart service
docker-compose restart signaling-server

# Rebuild image and start
docker-compose up -d --build

# Remove volumes
docker-compose down -v

# Pull latest images
docker-compose pull

# Execute command in service
docker-compose exec signaling-server npm list

# Show service logs (last output)
docker-compose logs signaling-server

# Show specific number of lines
docker-compose logs --tail=100 signaling-server
```

## Cleanup & Maintenance

```bash
# Remove stopped containers
docker container prune

# Remove unused images
docker image prune

# Remove unused volumes
docker volume prune

# Remove everything unused
docker system prune -a

# Show disk usage
docker system df

# Update container resource limits
docker update --memory 1024m --cpus 2 signaling-server-prod
```

## Network Debugging

```bash
# List networks
docker network ls

# Inspect network
docker network inspect bridge

# Test connectivity to container
docker exec signaling-server-prod ping google.com

# Test local port
docker exec signaling-server-prod netstat -tulpn

# Check DNS resolution
docker exec signaling-server-prod nslookup google.com
```

## Troubleshooting One-Liners

```bash
# Check if port is listening
docker exec signaling-server-prod netstat -tulpn | grep 3000

# Get container PID
docker inspect -f '{{.State.Pid}}' signaling-server-prod

# Get container gateway
docker inspect -f '{{range.NetworkSettings.Networks}}{{.Gateway}}{{end}}' signaling-server-prod

# Get all container IPs
docker inspect -f '{{json .NetworkSettings.Networks}}' signaling-server-prod

# Show container creation time
docker inspect -f '{{.Created}}' signaling-server-prod

# Show container uptime
docker inspect -f '{{.State.StartedAt}}' signaling-server-prod
```

## Common Issues & Solutions

### Port already in use
```bash
# Find container using port 3000
docker ps --filter "expose=3000"

# Find process on host using port 3000 (Linux)
sudo lsof -i :3000

# Kill container and restart
docker restart signaling-server-prod
```

### Out of memory
```bash
# Check memory usage
docker stats --no-stream

# Increase memory limit
docker update --memory 1024m signaling-server-prod

# Restart to apply
docker restart signaling-server-prod
```

### Container not starting
```bash
# Check logs for errors
docker logs signaling-server-prod

# Try running in foreground to see errors
docker run --rm -it voice-translation-signaling-server:latest
```

### WebSocket not working
```bash
# Check if port is exposed
docker port signaling-server-prod

# Test connectivity
curl http://localhost:3000/health

# Check firewall
sudo ufw status
```

## Performance Monitoring

```bash
# Real-time stats
watch -n 1 'docker stats --no-stream'

# Top processes in container
docker top signaling-server-prod

# Memory usage only
docker stats --no-stream --format "{{.Container}}: {{.MemUsage}}"

# CPU usage only
docker stats --no-stream --format "{{.Container}}: {{.CPUPerc}}"
```

## Best Practices

1. **Always use specific image tags**, not `latest`
2. **Set resource limits** to prevent runaway containers
3. **Use health checks** for automatic restart detection
4. **Log to stdout/stderr** for Docker log aggregation
5. **Use `.dockerignore`** to reduce image size
6. **Pin dependency versions** in package.json
7. **Use non-root users** in containers
8. **Clean up regularly** to free disk space
9. **Monitor resource usage** in production
10. **Test locally before deploying** to production
