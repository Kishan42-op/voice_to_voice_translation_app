# ============================================
# Production-Ready Node.js 20 Alpine Dockerfile
# Socket.IO Signaling Server for LiveKit
# ============================================

# Stage 1: Builder
# Purpose: Install dependencies and prepare the application
FROM node:20-alpine AS builder

# Install build essentials (required for native modules)
RUN apk add --no-cache \
    python3 \
    make \
    g++ \
    gcc

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies with production flag
# Use --frozen-lockfile if package-lock.json exists for reproducible builds
RUN npm ci --only=production && \
    npm cache clean --force

# ============================================
# Stage 2: Runtime
# Purpose: Minimal production image without build tools
# ============================================
FROM node:20-alpine

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nodejs -u 1001

WORKDIR /app

# Copy built node_modules from builder
COPY --from=builder --chown=nodejs:nodejs /app/node_modules ./node_modules

# Copy application files
COPY --chown=nodejs:nodejs . .

# Switch to non-root user
USER nodejs

# Expose the Socket.IO port
EXPOSE 3000

# Health check
# Checks if the server is responding to HTTP requests
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD node -e "require('http').get('http://localhost:3000/health', (r) => {if (r.statusCode !== 200) throw new Error(r.statusCode)})"

# Start the server
CMD ["node", "local/server.js"]
