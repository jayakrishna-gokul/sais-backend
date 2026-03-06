#!/bin/bash

echo "=== Sapphire AI Service - Startup Script ==="

# Check Java
if ! command -v java &> /dev/null; then
    echo "ERROR: Java 21 not found"
    exit 1
fi

# Check PostgreSQL
echo "Checking PostgreSQL..."
if ! pg_isready -h localhost -p 5432 &> /dev/null; then
    echo "ERROR: PostgreSQL not running"
    echo "Start with: sudo systemctl start postgresql"
    exit 1
fi

# Check Qdrant
echo "Checking Qdrant..."
if ! curl -s http://localhost:6333/collections &> /dev/null; then
    echo "WARNING: Qdrant not running"
    echo "Start with: docker run -p 6333:6333 qdrant/qdrant"
fi

# Check Ollama
echo "Checking Ollama..."
if ! curl -s http://localhost:11434/api/tags &> /dev/null; then
    echo "WARNING: Ollama not running"
    echo "Start with: ollama serve"
fi

# Set environment variables
export DB_PASSWORD=${DB_PASSWORD:-changeme}
export JWT_SECRET=${JWT_SECRET:-change-this-in-production}

# Start application
echo "Starting Sapphire AI Service..."
java -jar target/sapphire-ai-service.jar \
    --spring.profiles.active=prod \
    --server.port=8080