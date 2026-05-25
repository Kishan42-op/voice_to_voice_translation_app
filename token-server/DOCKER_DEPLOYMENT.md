# Docker Deployment Guide - Socket.IO Signaling Server

## Overview
This guide provides complete instructions for containerizing and deploying the Socket.IO signaling server on Ubuntu VM with Docker on E2E Cloud.

---

## 📁 Recommended Folder Structure

```
/opt/signaling-server/
├── docker-compose.yml       # Docker orchestration
├── Dockerfile               # Container build config
├── .dockerignore            # Files to exclude from image
├── .env                     # Production environment variables
├── .env.example             # Template for env variables
├── app/
│   ├── local/
│   │   └── server.js       # Main application
│   ├── api/
│   │   ├── token.js
│   │   └── health.js
│   ├── package.json
│   ├── package-lock.json
│   └── node_modules/
└── logs/                    # Docker container logs
```

---

## 🚀 Quick Start

### 1. Prepare the Server

```bash
# SSH into your E2E Cloud Ubuntu VM
ssh user@your-vm-ip

# Update system packages
sudo apt-get update && sudo apt-get upgrade -y

# Install Docker (if not already installed)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add your user to docker group (optional, for non-sudo commands)
sudo usermod -aG docker $USER
newgrp docker

# Verify Docker installation
docker --version
docker run hello-world
```

### 2. Set Up Application Directory

```bash
# Create application directory
sudo mkdir -p /opt/signaling-server
cd /opt/signaling-server

# Copy files from your repository
# (Assuming you're deploying from Git or sftp)
git clone <your-repo> .
# or
scp -r token-server/* user@vm-ip:/opt/signaling-server/
```

### 3. Configure Environment Variables

```bash
# Create .env file from template
cp .env.example .env

# Edit with your LiveKit credentials
nano .env

# Add your credentials:
# LIVEKIT_API_KEY=your-key-here
# LIVEKIT_API_SECRET=your-secret-here
```

### 4. Build Docker Image

```bash
# Build the image
docker build -t voice-translation-signaling-server:latest .

# Verify the image was created
docker images | grep voice-translation
```

### 5. Run Container

#### Option A: Using Docker Directly

```bash
# Run container in the background
docker run -d \
  --name signaling-server-prod \
  --restart=always \
  -p 3000:3000 \
  --env-file .env \
  -v /var/log/signaling-server:/app/logs \
  voice-translation-signaling-server:latest

# Verify container is running
docker ps | grep signaling-server
```

#### Option B: Using Docker Compose (Recommended)

```bash
# Start services in background
docker-compose up -d

# Verify services
docker-compose ps

# View logs
docker-compose logs -f signaling-server
```

---

## 🔧 Common Operations

### View Logs

```bash
# Real-time logs from container
docker logs -f signaling-server-prod

# Last 100 lines
docker logs --tail=100 signaling-server-prod

# With timestamps
docker logs -f -t signaling-server-prod

# Using docker-compose
docker-compose logs -f signaling-server
```

### Stop Container

```bash
# Using Docker
docker stop signaling-server-prod

# Using Docker Compose
docker-compose stop signaling-server

# Force stop (if not responding)
docker kill signaling-server-prod
```

### Start/Restart Container

```bash
# Start stopped container
docker start signaling-server-prod

# Restart running container
docker restart signaling-server-prod

# Using docker-compose
docker-compose restart signaling-server
```

### Monitor Container Health

```bash
# Check container status
docker ps | grep signaling-server-prod

# Inspect container details
docker inspect signaling-server-prod

# Check health status
docker inspect --format='{{json .State.Health}}' signaling-server-prod | python3 -m json.tool

# View resource usage
docker stats signaling-server-prod
```

### Update Application

```bash
# If you update the code:

# 1. Pull latest changes (if using Git)
git pull origin main

# 2. Rebuild image
docker build -t voice-translation-signaling-server:latest .

# 3. Remove old container
docker stop signaling-server-prod
docker rm signaling-server-prod

# 4. Start new container
docker-compose up -d signaling-server
```

### View Container Logs (Persistent)

```bash
# Check where logs are stored
docker inspect signaling-server-prod | grep -A 5 LogPath

# View persistent logs
tail -f /var/lib/docker/containers/<container-id>/<container-id>-json.log
```

---

## ⚠️ Common Deployment Mistakes to Avoid

### 1. **Exposed Secrets in Environment**
❌ **Wrong:**
```bash
docker run ... -e LIVEKIT_API_SECRET=mysecret ...
```
✅ **Correct:** Use `.env` file with proper permissions
```bash
chmod 600 .env
docker run ... --env-file .env ...
```

### 2. **Not Setting Resource Limits**
❌ **Wrong:** Container can consume all system resources
```bash
docker run ...  # No limits!
```
✅ **Correct:** Set memory and CPU limits
```bash
docker run ... -m 512m --cpus 1 ...
# Or in docker-compose.yml
deploy:
  resources:
    limits:
      memory: 512M
      cpus: "1"
```

### 3. **Running as Root User**
❌ **Wrong:** Security risk
```dockerfile
# No USER specified, runs as root
```
✅ **Correct:** Use non-root user
```dockerfile
RUN adduser -S nodejs -u 1001
USER nodejs
```

### 4. **Not Using Health Checks**
❌ **Wrong:** Can't detect if server is actually working
```bash
docker run ... # No health check
```
✅ **Correct:** Always include health checks
```bash
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:3000/health || exit 1
```

### 5. **No Restart Policy**
❌ **Wrong:** Container stops, application is down
```bash
docker run ... # No restart policy
```
✅ **Correct:** Set restart policy
```bash
docker run ... --restart=always ...
```

### 6. **Forgetting Port Mappings**
❌ **Wrong:** Can't access the server from outside
```bash
docker run ... # Port not exposed
```
✅ **Correct:** Expose the port
```bash
docker run -p 3000:3000 ...
```

### 7. **Not Binding to 0.0.0.0**
❌ **Wrong:** Server only listens on localhost inside container
```javascript
httpServer.listen(3000, 'localhost')
```
✅ **Correct:** Bind to all interfaces
```javascript
httpServer.listen(3000, '0.0.0.0')
```

### 8. **Large Docker Images**
❌ **Wrong:** Using bloated base images
```dockerfile
FROM node:20  # ~1GB
```
✅ **Correct:** Use Alpine for smaller images
```dockerfile
FROM node:20-alpine  # ~200MB
```

### 9. **Not Cleaning Up Old Images**
❌ **Wrong:** Disk fills up with unused images
```bash
# No cleanup
```
✅ **Correct:** Periodically clean up
```bash
docker image prune -a
docker container prune
```

### 10. **WebSocket Connection Issues**
❌ **Wrong:** Firewall blocks WebSocket traffic
```bash
# Firewall rules don't allow port 3000
```
✅ **Correct:** Open ports and configure CORS
```bash
# UFW (Ubuntu)
sudo ufw allow 3000/tcp

# Also ensure CORS is properly configured in app
```

---

## 🔍 Testing the Deployment

### Health Check

```bash
# Test health endpoint
curl http://localhost:3000/health

# Expected response:
# {"ok":true}
```

### Token Generation

```bash
# Test token endpoint
curl -X POST http://localhost:3000/token \
  -H "Content-Type: application/json" \
  -d '{"room":"test-room","identity":"test-user"}'

# Expected response:
# {"token":"eyJ..."}
```

### WebSocket Connection (using Node.js)

```bash
# Create test-socket.js
cat > test-socket.js << 'EOF'
import { io } from "socket.io-client";

const socket = io("http://localhost:3000", {
  transports: ["websocket"],
});

socket.on("connect", () => {
  console.log("✓ Connected to socket server");
  console.log("Socket ID:", socket.id);
  
  socket.emit("register", { uid: "test-user-123" });
});

socket.on("register-ack", (data) => {
  console.log("✓ Registered:", data);
  socket.disconnect();
});

socket.on("error", (err) => {
  console.error("✗ Error:", err);
});
EOF

# Run test
node test-socket.js
```

---

## 📊 Performance Optimization Tips

### 1. **Layer Caching**
The Dockerfile uses multi-stage builds and ordered commands to maximize layer caching:
```dockerfile
# Dependencies rarely change, so they're cached
COPY package*.json ./
RUN npm ci --only=production

# Application code changes frequently, so it's last
COPY . .
```

### 2. **Alpine Image Benefits**
- **Size:** 200MB vs 1GB (Node.js)
- **Security:** Minimal attack surface
- **Speed:** Faster pulls and starts

### 3. **Resource Limits**
Set appropriate limits to prevent runaway processes:
```yaml
deploy:
  resources:
    limits:
      memory: 512M
      cpus: "1"
```

### 4. **Connection Pooling**
For production, consider adding:
```javascript
// Max concurrent connections
io.engine.maxHttpBufferSize = 1e6; // 1MB

// Connection timeout
io.engine.connectTimeout = 45000; // 45 seconds
```

---

## 🔐 Security Checklist

- [ ] `.env` file with secrets is NOT in Docker image (uses `.dockerignore`)
- [ ] Running as non-root user (nodejs)
- [ ] Using dumb-init for proper signal handling
- [ ] CORS is properly configured (not `origin: "*"` in production)
- [ ] Health checks enabled
- [ ] Resource limits set
- [ ] Firewall only allows necessary ports
- [ ] `.env` file has restricted permissions (`chmod 600`)
- [ ] No secrets in docker-compose.yml
- [ ] Regular security updates for base image

---

## 🆘 Troubleshooting

### Container won't start

```bash
# Check logs
docker logs signaling-server-prod

# Common issues:
# - Port already in use
# - Missing environment variables
# - Corrupted node_modules
```

### Out of memory errors

```bash
# Increase memory limit
docker update --memory 1024m signaling-server-prod

# Or in docker-compose.yml
deploy:
  resources:
    limits:
      memory: 1G
```

### WebSocket connection failures

```bash
# Check firewall
sudo ufw status
sudo ufw allow 3000/tcp

# Test connectivity
telnet localhost 3000
nc -zv localhost 3000
```

### High CPU usage

```bash
# Monitor resources
docker stats signaling-server-prod

# Check logs for issues
docker logs -f signaling-server-prod

# May need to restart
docker restart signaling-server-prod
```

---

## 📝 Production Deployment Checklist

- [ ] Test deployment on staging environment first
- [ ] Set up proper monitoring and alerting
- [ ] Configure log aggregation (if available)
- [ ] Set up automated backups
- [ ] Document all custom environment variables
- [ ] Create runbook for common issues
- [ ] Test disaster recovery procedures
- [ ] Set up CI/CD pipeline for automatic deployments
- [ ] Enable Docker events logging
- [ ] Create network policies if using multiple containers

---

## 🚀 Advanced: Setting Up Reverse Proxy (Optional)

For production with SSL/TLS:

```nginx
# nginx.conf
upstream signaling_server {
    server signaling-server:3000;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /etc/ssl/certs/cert.pem;
    ssl_certificate_key /etc/ssl/private/key.pem;

    location / {
        proxy_pass http://signaling_server;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /socket.io {
        proxy_pass http://signaling_server/socket.io;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## 📞 Support & Monitoring

For production environments, consider setting up:
- **Prometheus** for metrics collection
- **Grafana** for visualization
- **AlertManager** for alerting
- **ELK Stack** for centralized logging

Example Prometheus metrics endpoint could be added to track:
- Active connections
- Message throughput
- Call success rates
- Error rates

---

## ✅ Verification Checklist

After deployment, verify:

```bash
# 1. Container is running
docker ps | grep signaling-server

# 2. Logs show no errors
docker logs -f signaling-server-prod | head -20

# 3. Health check passes
curl http://localhost:3000/health

# 4. Port is open
sudo ss -tulpn | grep 3000

# 5. Resource usage is normal
docker stats signaling-server-prod

# 6. Can generate tokens
curl -X POST http://localhost:3000/token \
  -H "Content-Type: application/json" \
  -d '{"room":"test","identity":"user"}'
```

---

## 📚 Additional Resources

- [Docker Official Node.js Image](https://hub.docker.com/_/node)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Socket.IO Server Documentation](https://socket.io/docs/v4/server-api/)
- [LiveKit Server SDK](https://github.com/livekit/server-sdk-js)

---

**Last Updated:** May 2026
