# CI/CD Pipeline Setup

This document explains how to set up and use the CI/CD pipeline for the Java Echo Bridge application.

## Files Created

1. **Dockerfile** - Multi-stage build for the Spring Boot application
2. **build-and-test.sh** - Bash script that builds, tests, and validates the Docker container
3. **.github/workflows/ci-cd.yml** - GitHub Actions workflow for automated CI/CD
4. **Updated application.properties** - Now uses environment variables with defaults

## Environment Variables

The application now uses environment variables instead of hardcoded properties:

- `SPRING_APPLICATION_NAME` (default: java_app)
- `SPRING_LOGGING_LEVEL_ROOT` (default: INFO)

## GitHub Actions Setup

### Required Secrets

Add these secrets to your GitHub repository:

1. `DOCKER_USERNAME` - Your Docker Hub username
2. `DOCKER_PASSWORD` - Your Docker Hub password or access token

### Workflow Triggers

- **Push to main/develop**: Triggers full pipeline
- **Pull requests to main**: Triggers build and test only

### Pipeline Stages

1. **Build and Test**
   - Sets up JDK 25
   - Caches Gradle dependencies
   - Runs the build-and-test.sh script
   - Generates test reports
   - Uploads build artifacts

2. **Docker Build and Push** (main branch only)
   - Builds Docker image
   - Pushes to Docker Hub
   - Supports multi-platform builds (amd64/arm64)

3. **Deploy** (main branch only)
   - Placeholder for deployment commands
   - Can be customized for your deployment target

## Local Usage

### Running the Build Script

```bash
chmod +x build-and-test.sh
./build-and-test.sh
```

### Building Docker Image Locally

```bash
docker build -t java-app:latest .
docker run -p 8080:8080 java-app:latest
```

### Running with Environment Variables

```bash
export SPRING_APPLICATION_NAME=my_app
export SPRING_LOGGING_LEVEL_ROOT=DEBUG
docker run -p 8080:8080 -e SPRING_APPLICATION_NAME -e SPRING_LOGGING_LEVEL_ROOT java-app:latest
```

## Health Check

The pipeline includes a health check that expects the application to have an actuator health endpoint at `/actuator/health`. Make sure your application includes this dependency in `build.gradle`:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

## Customization

### Adding New Environment Variables

1. Add the variable to the GitHub Actions workflow env section
2. Update application.properties to use the new environment variable
3. Pass the variable in your Docker run commands

### Deployment Configuration

Modify the `deploy` job in `.github/workflows/ci-cd.yml` to match your deployment target (Kubernetes, ECS, etc.).

## Troubleshooting

### Build Failures

- Check that Java 25 is properly installed
- Verify all dependencies are available
- Ensure Docker is running for container tests

### Docker Issues

- Make sure Docker daemon is running
- Check Docker Hub credentials in GitHub secrets
- Verify Dockerfile syntax

### Test Failures

- Review test reports in GitHub Actions
- Check application logs in Docker container
- Verify health endpoint is accessible
