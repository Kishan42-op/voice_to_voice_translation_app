# Deploy to E2E Cloud - Quick Steps

## 1. SSH to VM
```bash
ssh -i key.pem ubuntu@YOUR-VM-IP
```

## 2. Install Docker
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
```

## 3. Get Code
```bash
mkdir -p /opt/signaling-server
cd /opt/signaling-server
git clone YOUR-REPO .
```

## 4. Configure
```bash
cp .env.example .env
nano .env
# Add: LIVEKIT_API_KEY, LIVEKIT_API_SECRET
chmod 600 .env
```

## 5. Build and run
```bash
docker run -d \
  --name voice_translation_signaling_server \
  --env-file file_path of .env file \
  -p 3000:3000 \
  --restart unless-stopped \
  voice-translation-signaling-server:latest
```

## 6. Test
```bash
curl http://localhost:3000/health
# Expected: {"ok":true}
```

## 7. Open Port
```bash
sudo ufw allow 3000/tcp
```

## 8. Verify (External)
```bash
curl http://YOUR-VM-IP:3000/health
```

---

## Common Commands

| Command | Purpose |
|---------|---------|
| `docker logs -f signaling-server-prod` | View logs |
| `docker-compose restart signaling-server` | Restart |
| `docker-compose down` | Stop |
| `docker stats signaling-server-prod --no-stream` | Stats |

---

## Troubleshoot

**Port in use:** `docker-compose down` then restart

**Container won't start:** `docker logs signaling-server-prod`

**Can't access:** `sudo ufw allow 3000/tcp`

**Out of memory:** `docker update --memory 1024m signaling-server-prod`
