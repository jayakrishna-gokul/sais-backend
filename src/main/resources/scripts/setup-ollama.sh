#!/bin/bash

echo "=== Ollama Setup ==="

# Pull Mistral 7B
echo "Downloading Mistral 7B (4.1GB)..."
ollama pull mistral:7b-instruct-q8_0

# Pull embedding model
echo "Downloading nomic-embed-text (274MB)..."
ollama pull nomic-embed-text

echo "Setup complete!"
echo "Start Ollama: ollama serve"