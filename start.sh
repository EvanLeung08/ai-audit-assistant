#!/bin/bash

# AI Audit Assistant - Start Script
# This script starts the Spring Boot application

echo "🚀 Starting AI Audit Assistant..."
echo "================================"

# Check if Maven wrapper exists
if [ -f "./mvnw" ]; then
    echo "Using Maven Wrapper..."
    chmod +x ./mvnw
    ./mvnw spring-boot:run
else
    echo "Maven wrapper not found, trying system mvn..."
    mvn spring-boot:run
fi
