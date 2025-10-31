# Multi-stage build for k6 with Kafka extension
FROM golang:1.24-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git ca-certificates

# Install xk6 tool
RUN go install go.k6.io/xk6/cmd/xk6@latest

# Build k6 with Kafka extension
WORKDIR /build
RUN xk6 build \
    --with github.com/mostafa/xk6-kafka@latest \
    --output k6

# Final lightweight image
FROM alpine:latest

# Install runtime dependencies
RUN apk add --no-cache ca-certificates

# Copy k6 binary from builder stage
COPY --from=builder /build/k6 /usr/bin/k6

# Create non-root user
RUN adduser -D -s /bin/sh k6user

# Switch to non-root user
USER k6user

# Set working directory
WORKDIR /home/k6user

# Default entrypoint
ENTRYPOINT ["k6"]