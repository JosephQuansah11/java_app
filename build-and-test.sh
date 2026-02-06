#!/bin/bash

# Exit on any error
set -e

echo "🚀 Starting build and test process..."

# Ensure gradlew is executable
chmod +x ./gradlew

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Run tests
echo "🧪 Running tests..."
./gradlew test

# Build the application
echo "🔨 Building the application..."
./gradlew build

# Build Docker image
echo "🐳 Building Docker image..."
docker build -t java-echobridge-app:latest .

# Run Docker container for testing
echo "🏃 Running Docker container for testing..."
docker run -d --name java-echobridge-app-test -p 8080:8080 java-echobridge-app:latest

# Wait for the application to start
echo "⏳ Waiting for application to start..."
sleep 30

# Health check
echo "🔍 Performing health check..."
if curl -f http://localhost:8080/actuator/health; then
    echo "✅ Health check passed!"
else
    echo "❌ Health check failed!"
    docker stop java-echobridge-app-test
    docker rm java-echobridge-app-test
    exit 1
fi

# Stop and remove test container
echo "🧹 Cleaning up test container..."
docker stop java-echobridge-app-test
docker rm java-echobridge-app-test

echo "🎉 Build and test process completed successfully!"
