# Test Suite Documentation

This directory contains comprehensive unit and integration tests for the Uptime Checker application.

## Test Structure

### Unit Tests (`src/test/java/com/isofuture/uptime/service/` and `repository/`)

#### Service Tests
- **UserServiceTest**: Tests user CRUD operations with role-based access control
  - Admin vs regular user scenarios
  - Own profile vs other user's profile access
  - Soft delete functionality
  - Password change operations

- **AuthServiceTest**: Tests authentication and registration
  - Successful login/logout
  - Invalid credentials handling
  - User registration with role assignment
  - Duplicate email prevention

- **MonitorServiceTest**: Tests monitor management with role-based access
  - Admin sees all monitors
  - Regular users see only own monitors
  - Create, update, delete operations with ownership checks

- **CheckServiceTest**: Tests check execution and result recording
  - Admin can execute any check
  - Users can execute own checks
  - Worker API access for result recording

#### Repository Tests
- **UserRepositoryTest**: Tests user repository queries
  - Case-insensitive email lookup
  - Active user filtering (excludes soft-deleted)
  - Soft delete functionality

- **MonitoredUrlRepositoryTest**: Tests monitor repository queries
  - Find by owner
  - Find ready for check
  - Find in-progress monitors
  - Ownership verification

### Integration Tests (`src/test/java/com/isofuture/uptime/integration/`)

#### UserControllerIntegrationTest
Tests the `/api/users` endpoints with live JWT authentication:
- **Role-based access**: Admin vs regular user permissions
- **Cross-referencing**: Verifies data state via both API calls and service/repository calls
- **Scenarios tested**:
  - Admin listing all users
  - User viewing own profile
  - User cannot view other user's profile
  - Admin creating users
  - User updating own profile
  - Password change operations
  - Soft delete with state verification

#### MonitorControllerIntegrationTest
Tests the `/api/monitors` endpoints with role-based scenarios:
- **Admin access**: Can see and manage all monitors
- **User access**: Can only see and manage own monitors
- **Cross-referencing**: Verifies monitor state via API, service, and repository
- **Scenarios tested**:
  - List monitors (admin vs user)
  - Create monitor
  - Update own monitor
  - Cannot update other user's monitor
  - Delete operations with ownership checks

#### CheckControllerIntegrationTest
Tests the `/api/checks` endpoints with comprehensive cross-referencing:
- **JWT authentication**: Regular users and admins
- **Worker API key authentication**: For worker-specific endpoints
- **Cross-referencing**: Verifies data consistency across:
  - API response
  - Service layer calls
  - Repository queries
  - Database state
- **Scenarios tested**:
  - Execute check (own monitor)
  - Cannot execute other user's monitor
  - Worker fetching next checks
  - Worker recording results
  - State verification after operations (inProgress, nextCheckAt, etc.)

## Test Configuration

### Test Profile (`application-test.yml`)
- Uses H2 in-memory database for fast test execution
- Configured with test JWT secret and worker API keys
- Transactions are rolled back after each test

### Base Test Class (`BaseTest.java`)
Provides common utilities for creating test entities:
- `createUserEntity()`: Creates user entities with roles
- `createSecurityUser()`: Creates SecurityUser instances
- `createAdminUser()`: Creates admin users
- `createRegularUser()`: Creates regular users

## Running Tests

### Run all tests:
```bash
mvn test
```

### Run specific test class:
```bash
mvn test -Dtest=UserServiceTest
```

### Run integration tests only:
```bash
mvn test -Dtest=*IntegrationTest
```

### Run unit tests only:
```bash
mvn test -Dtest=*Test -Dtest=!*IntegrationTest
```

## Test Coverage

### Role-Based Scenarios
All tests include scenarios for:
- **Admin users**: Full access to all resources
- **Regular users**: Access limited to own resources
- **Workers**: API key-based access for check operations

### Cross-Referencing Pattern
Integration tests follow a pattern of:
1. Make API call with JWT token
2. Verify API response
3. Cross-reference via service layer
4. Cross-reference via repository layer
5. Verify database state

This ensures data consistency across all layers of the application.

## Key Testing Patterns

### JWT Token Generation
Integration tests generate JWT tokens by calling the `/api/auth/login` endpoint, ensuring real authentication flow is tested.

### Transaction Management
All integration tests use `@Transactional` to ensure test isolation - each test runs in its own transaction that is rolled back.

### Mock vs Real
- **Unit tests**: Use Mockito to mock dependencies
- **Integration tests**: Use real Spring context with H2 database

## Future Enhancements

Consider adding:
- Performance tests for high-load scenarios
- Security tests for edge cases (token expiration, invalid tokens)
- Concurrent access tests for check operations
- End-to-end tests with TestContainers using real MySQL


