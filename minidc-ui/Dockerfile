# Multi-stage Dockerfile for minidc-ui
# Build stage
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files
COPY minidc-ui/package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY minidc-ui/ .

# Build the application
RUN npm run build

# Runtime stage - Nginx
FROM nginx:alpine

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY minidc-ui/nginx.conf /etc/nginx/conf.d/default.conf

# Create non-root user
RUN addgroup -g 1000 minidc && adduser -D -u 1000 -G minidc minidc

# Set ownership
RUN chown -R minidc:minidc /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d

USER minidc

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

# Expose port
EXPOSE 8080

# Run nginx
CMD ["nginx", "-g", "daemon off;"]
