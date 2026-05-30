# Production Deployment Checklist & Setup Guide

## Pre-Deployment (Before Server Setup)

### Code & Repository
- [ ] Application code is in Git repository
- [ ] `.env` values are documented (without secrets)
- [ ] `.dockerignore` is properly configured
- [ ] `package.json` has all dependencies locked in `package-lock.json`
- [ ] No hardcoded secrets in code
- [ ] README has clear build/run instructions

### Environment Preparation
- [ ] E2E Cloud VM is provisioned (Ubuntu 20.04 LTS or later)
- [ ] SSH access configured to VM
- [ ] SSH key saved locally
- [ ] VM public IP or DNS name documented
- [ ] Security group allows port 3000 (or your chosen port)
- [ ] LiveKit API credentials obtained
- [ ] Domain name (optional, for SSL/TLS)

### Local Testing
- [ ] Docker is installed locally
- [ ] Image builds successfully: `docker build -t test:latest .`
- [ ] Container runs locally: `docker run -p 3000:3000 --env-file .env test:latest`
- [ ] Health endpoint responds: `curl http://localhost:3000/health`
- [ ] Token generation works locally
- [ ] WebSocket connects successfully

---

## Initial Server Setup (First Time)

### 1. Connect to VM & Install Docker

```bash
# SSH into VM
ssh -i your-key.pem ubuntu@your-vm-ip

# Update system
sudo apt-get update
sudo apt-get upgrade -y
sudo apt-get install -y git curl

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker --version
docker run hello-world  # Should print success
```

- [ ] Docker installed and running
- [ ] User can run Docker commands without sudo
- [ ] Docker version is recent (20.10+)

### 2. Setup Application Directory

```bash
# Create directory structure
sudo mkdir -p /opt/signaling-server
cd /opt/signaling-server

# Set permissions
sudo chown $USER:$USER /opt/signaling-server

# Clone/copy application
git clone <your-repo> .
# OR
scp -r token-server/* user@vm-ip:/opt/signaling-server/
```

- [ ] `/opt/signaling-server` directory created
- [ ] Application files copied
- [ ] User has read/write permissions
- [ ] All Docker files present (Dockerfile, docker-compose.yml)

### 3. Configure Environment

```bash
# Copy environment template
cp .env.example .env

# Edit with production values
nano .env
# OR
vi .env
```

**Edit these values:**
```
PORT=3000
LIVEKIT_API_KEY=your-production-key
LIVEKIT_API_SECRET=your-production-secret
NODE_ENV=production
LOG_LEVEL=info
```

- [ ] `.env` file created
- [ ] All required variables set (LIVEKIT_API_KEY, LIVEKIT_API_SECRET)
- [ ] PORT set to 3000
- [ ] NODE_ENV set to "production"
- [ ] File permissions: `chmod 600 .env`

### 4. Build Docker Image

```bash
# Make sure you're in the app directory
cd /opt/signaling-server

# Build image
docker build -t voice-translation-signaling-server:latest .

# Verify
docker images | grep voice-translation

# Expected output:
# voice-translation-signaling-server   latest   <image-id>   ...   200MB
```

- [ ] Image builds without errors
- [ ] Image size is ~200MB
- [ ] Image tag is correct

### 5. Test Container Locally

```bash
# Test run
docker-compose up -d

# Wait 2 seconds
sleep 2

# Check status
docker ps | grep signaling-server

# Test health
curl http://localhost:3000/health

# View logs
docker logs -f signaling-server-prod

# If all good, stop for now
docker-compose down
```

- [ ] Container starts successfully
- [ ] Health check returns `{"ok":true}`
- [ ] No errors in logs
- [ ] Port 3000 is accessible

### 6. Setup Firewall (If UFW Enabled)

```bash
# Check firewall status
sudo ufw status

# If enabled, open port
sudo ufw allow 3000/tcp

# Verify
sudo ufw status numbered
```

- [ ] Firewall status checked
- [ ] Port 3000 allowed (if firewall enabled)
- [ ] UFW rules verified

### 7. Configure Systemd Service (Optional but Recommended)

```bash
# Copy service file
sudo cp signaling-server.service /etc/systemd/system/

# Reload systemd
sudo systemctl daemon-reload

# Enable service (auto-start on reboot)
sudo systemctl enable signaling-server

# Start service
sudo systemctl start signaling-server

# Check status
sudo systemctl status signaling-server

# View logs
sudo journalctl -u signaling-server -f
```

- [ ] Service file copied to `/etc/systemd/system/`
- [ ] Systemd daemon reloaded
- [ ] Service enabled for auto-start
- [ ] Service started successfully
- [ ] Status shows "active (running)"

### 8. Enable Log Rotation

```bash
# Create log rotation config
sudo tee /etc/logrotate.d/docker-signaling > /dev/null << 'EOF'
/var/log/docker-signaling/*.log {
    daily
    rotate 7
    compress
    delaycompress
    notifempty
    create 0600 root root
    sharedscripts
}
EOF

# Test rotation
sudo logrotate -f /etc/logrotate.d/docker-signaling
```

- [ ] Log rotation configured
- [ ] Old logs will be cleaned up automatically

---

## Post-Deployment Verification

### Immediate Checks (5 minutes)

```bash
# Check container is running
docker ps | grep signaling-server

# Check logs for errors
docker logs signaling-server-prod | tail -20

# Test health endpoint
curl http://localhost:3000/health

# Monitor initial startup
docker stats signaling-server-prod --no-stream
```

- [ ] Container running
- [ ] No error messages in logs
- [ ] Health check passes
- [ ] Memory usage < 100MB
- [ ] CPU usage normal

### Short-term Checks (1 hour)

```bash
# Monitor logs continuously
docker logs -f signaling-server-prod &

# Run connectivity tests
curl -X POST http://localhost:3000/token \
  -H "Content-Type: application/json" \
  -d '{"room":"test-room","identity":"test-user"}'

# Check health repeatedly
for i in {1..10}; do curl -s http://localhost:3000/health; sleep 6; done
```

- [ ] No new errors in logs
- [ ] Token generation successful
- [ ] Health checks all pass
- [ ] Container didn't restart unexpectedly

### Medium-term Checks (24 hours)

```bash
# Check resource usage
docker stats signaling-server-prod --no-stream

# Check logs for warnings
docker logs signaling-server-prod | grep -i "warning\|error"

# Verify restart policy works
docker stop signaling-server-prod
sleep 5
docker ps | grep signaling-server  # Should be running again

# Test from external machine
curl http://<vm-ip>:3000/health
```

- [ ] Memory usage stable
- [ ] No memory leaks detected
- [ ] Container auto-restarted after stop
- [ ] External access works
- [ ] No unusual log patterns

---

## Ongoing Maintenance

### Daily Checks

```bash
# Quick health check
curl http://localhost:3000/health

# Check container status
docker ps | grep signaling-server

# Monitor resource usage
docker stats signaling-server-prod --no-stream

# Recent log review
docker logs --tail=100 signaling-server-prod | grep -i "error"
```

### Weekly Tasks

```bash
# Full log rotation
sudo logrotate /etc/logrotate.d/docker-signaling

# Update system
sudo apt-get update
sudo apt-get upgrade -y

# Check for Docker updates
docker version

# Disk usage check
df -h

# Container inspection
docker inspect signaling-server-prod
```

### Monthly Tasks

```bash
# Update base image
docker pull node:20-alpine

# Rebuild image with latest base
cd /opt/signaling-server
docker build -t voice-translation-signaling-server:latest .

# Test new image locally
docker-compose down
docker-compose up -d
# ... test thoroughly ...

# If all good, keep running
# If issues, roll back to previous version
```

### Security Updates

```bash
# When Node.js has security patch
docker build --no-cache -t voice-translation-signaling-server:latest .
docker-compose down
docker-compose up -d

# Verify after update
curl http://localhost:3000/health
docker logs -f signaling-server-prod | head -20
```

---

## Troubleshooting During Deployment

### Port Already in Use

```bash
# Find what's using port 3000
sudo lsof -i :3000
# OR
sudo netstat -tulpn | grep 3000

# Stop conflicting service
docker stop <container-name>
# OR
sudo kill -9 <PID>
```

### Container Won't Start

```bash
# Check logs for specific error
docker logs signaling-server-prod

# Common issues:
# - Missing .env file → cp .env.example .env
# - Wrong image name → docker build -t correct-name:tag .
# - Port already in use → change PORT in .env
# - Corrupted node_modules → docker system prune -a
```

### Out of Memory

```bash
# Check current limit
docker inspect signaling-server-prod | grep Memory

# Increase limit
docker update --memory 1024m signaling-server-prod

# Or in docker-compose.yml:
# deploy:
#   resources:
#     limits:
#       memory: 1G
```

### WebSocket Connection Fails

```bash
# Test connectivity
telnet localhost 3000
# OR
nc -zv localhost 3000

# Check firewall
sudo ufw status
sudo ufw allow 3000/tcp

# View connection details
docker exec signaling-server-prod netstat -tulpn

# CORS issue in logs?
# Check CORS configuration in server.js
```

### High CPU Usage

```bash
# Monitor CPU
docker stats signaling-server-prod

# Check logs for infinite loops
docker logs -f signaling-server-prod | head -50

# Try restart
docker restart signaling-server-prod

# If persists, investigate app code
```

---

## Performance Monitoring

### Resource Limits Setup

In `docker-compose.yml` (already configured):
```yaml
deploy:
  resources:
    limits:
      cpus: "1"
      memory: 512M
    reservations:
      cpus: "0.5"
      memory: 256M
```

### Scaling Considerations

If you need to handle more connections:
- [ ] Increase memory limit to 1G (in docker-compose.yml)
- [ ] Increase CPU limit to 2 cores
- [ ] Consider running multiple instances with load balancer
- [ ] Monitor connection count in application logs

### Monitoring Tools (Optional)

```bash
# Prometheus exporter for Docker
docker run -d \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -p 9323:9323 \
  prom/docker-discovery:latest

# Grafana for visualization
docker run -d \
  -p 3001:3000 \
  --name grafana \
  grafana/grafana:latest
```

---

## Backup & Recovery

### Daily Logs Backup

```bash
# Backup logs
sudo tar -czf /backup/signaling-logs-$(date +%Y%m%d).tar.gz \
  /var/lib/docker/containers/*/

# Store on external location
sudo scp /backup/signaling-logs-*.tar.gz backup-server:/backups/
```

### Application Backup

```bash
# Backup entire application directory
sudo tar -czf /backup/signaling-app-$(date +%Y%m%d).tar.gz \
  /opt/signaling-server/

# Keep 30 days of backups
find /backup -name "signaling-*.tar.gz" -mtime +30 -delete
```

### Docker Image Backup

```bash
# Save image
docker save voice-translation-signaling-server:latest | \
  gzip > signaling-server-backup.tar.gz

# Load image
docker load < signaling-server-backup.tar.gz
```

---

## Recovery Procedures

### If Container Crashes

```bash
# Check what happened
docker logs signaling-server-prod | tail -30

# Restart
docker restart signaling-server-prod

# If restart fails, check resources
docker stats --no-stream

# May need to increase memory limit
docker update --memory 1024m signaling-server-prod
docker start signaling-server-prod
```

### If Image is Corrupted

```bash
# Remove old image
docker rmi voice-translation-signaling-server:latest

# Rebuild from source
cd /opt/signaling-server
docker build -t voice-translation-signaling-server:latest .

# Start container
docker-compose up -d
```

### If Server Won't Boot

```bash
# Try manual restart
docker-compose down
docker-compose up -d

# If still failing:
# 1. Check logs
docker logs signaling-server-prod

# 2. Check system resources
df -h  # Disk space
free -h  # Memory

# 3. Check environment
cat /opt/signaling-server/.env

# 4. Try clean restart
docker-compose down -v
docker-compose up -d
```

---

## Sign-Off Checklist

- [ ] Pre-deployment checks completed
- [ ] Server setup finished
- [ ] Docker image built successfully
- [ ] Container runs and stays running
- [ ] All tests pass
- [ ] Firewall configured
- [ ] Service auto-starts on reboot
- [ ] Logs are collecting properly
- [ ] Health monitoring in place
- [ ] Backup strategy documented
- [ ] Team trained on operations
- [ ] Emergency contacts documented
- [ ] Runbook created for troubleshooting

---

## Post-Deployment Documentation

Create a file `/opt/signaling-server/PRODUCTION_NOTES.md` with:
- [ ] Deployment date: ___________
- [ ] Deployed by: ___________
- [ ] LiveKit instance: ___________
- [ ] VM specs: ___________
- [ ] Contact info: ___________
- [ ] Known limitations: ___________
- [ ] Future improvements: ___________

---

**Deployment Date:** ___________
**Approved By:** ___________
**Last Reviewed:** ___________
