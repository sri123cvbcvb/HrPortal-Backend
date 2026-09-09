# HrPortal Backend

A comprehensive Human Resource Management System (HRMS) backend built with Spring Boot.

## Technology Stack

- **Framework**: Spring Boot 4.0.3
- **Language**: Java 17
- **Database**: MySQL
- **Security**: Spring Security + JWT Authentication
- **Messaging**: RabbitMQ (AMQP)
- **WebSocket**: STOMP over SockJS
- **Build Tool**: Maven
- **ORM**: Spring Data JPA / Hibernate

## Architecture Overview

The application follows a layered architecture:

`
Controller Layer  ->  Service Layer  ->  Repository Layer  ->  Database
     |                     |
  DTOs/Models         Business Logic
     |
  Security (JWT Filter)
`

## Features

- **Authentication & Authorization**: JWT-based authentication with role-based access control (Admin/Employee)
- **Employee Management**: Full CRUD operations for employee profiles with statutory and payroll details
- **Attendance Management**: Geofence-based check-in/check-out with monthly summaries
- **Leave Management**: Leave types, balances, applications, approvals, and company holidays
- **Payroll**: Automated monthly payroll generation with tax (TDS), PF, and LOP calculations
- **Notifications**: Real-time push notifications via RabbitMQ and WebSocket

## Prerequisites

- Java 17+
- MySQL 8.0+
- Maven 3.8+
- RabbitMQ Server (for notifications)

## Environment Setup

1. Copy the example environment file:
   `ash
   cp .env.example .env
   `

2. Edit `.env` and fill in your actual values:
   `
   DB_URL=jdbc:mysql://localhost:3306/hrms_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
   DB_USERNAME=your_db_username
   DB_PASSWORD=your_db_password
   JWT_SECRET=your-secure-64-char-hex-string
   `

3. The application reads environment variables at startup. You can set them via:
   - A `.env` file (with a dotenv loader)
   - System environment variables
   - IDE run configuration

## Database Setup

The application uses `spring.jpa.hibernate.ddl-auto=update`, which automatically creates/updates tables.

1. Ensure MySQL is running
2. Create the database (or let the app create it):
   `sql
   CREATE DATABASE hrms_db;
   `
3. Configure DB credentials in your environment

## How to Run

`ash
# Using Maven Wrapper
./mvnw spring-boot:run

# Or with Maven
mvn spring-boot:run

# Build JAR
./mvnw clean package
java -jar target/backend-0.0.1-SNAPSHOT.jar
`

The server starts on `http://localhost:8080`

## API Endpoints

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | User login |
| POST | `/api/auth/signup` | User registration |

### Employee (Authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/employee/attendance/summary` | Get attendance summary |
| GET | `/api/employee/leaves/upcoming` | Get upcoming company holidays |

### Attendance (Authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/attendance/check-in` | Geofenced check-in |
| POST | `/api/attendance/check-out` | Geofenced check-out |
| GET | `/api/attendance/records` | Get attendance records |
| GET | `/api/attendance/detail` | Detailed attendance view |
| GET | `/api/attendance/monthly` | Monthly attendance data |

### Leave Management (Authenticated)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/employee/leaves/types` | Get leave types |
| GET | `/api/employee/leaves/balances` | Get leave balances |
| GET | `/api/employee/leaves/history` | Get leave history |
| POST | `/api/employee/leaves/apply` | Apply for leave |

### Admin Endpoints (ROLE_ADMIN)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/employees` | List all employees |
| POST | `/api/admin/employees` | Create employee |
| PUT | `/api/admin/employees/{id}` | Update employee |
| DELETE | `/api/admin/employees/{id}` | Delete employee |
| POST | `/api/admin/payroll/generate` | Generate payroll |
| GET/POST | `/api/admin/leave-management/*` | Manage leaves |
| GET/POST | `/api/admin/leaves/*` | Company holidays |

## Project Structure

`
src/main/java/com/hrms/backend/
+-- BackendApplication.java
+-- config/
|   +-- DataLoader.java
|   +-- RabbitMQConfig.java
|   +-- WebSocketConfig.java
+-- controller/
|   +-- AuthController.java
|   +-- AdminController.java
|   +-- EmployeeController.java
|   +-- AttendanceController.java
|   +-- LeaveRequestController.java
|   +-- LeaveAdminController.java
|   +-- CompanyLeaveController.java
|   +-- PayrollController.java
+-- dto/
|   +-- LoginRequest.java
|   +-- SignupRequest.java
|   +-- JwtResponse.java
|   +-- MessageResponse.java
|   +-- CheckInRequest.java
|   +-- AttendanceSummaryDto.java
|   +-- MonthlyAttendanceDto.java
|   +-- LeaveRequestDto.java
|   +-- UpdateEmployeeRequest.java
+-- model/
|   +-- User.java
|   +-- Role.java
|   +-- RoleName.java
|   +-- AttendanceRecord.java
|   +-- LeaveRequest.java
|   +-- LeaveType.java
|   +-- LeaveBalance.java
|   +-- CompanyLeave.java
|   +-- Payslip.java
|   +-- PayslipComponent.java
+-- repository/
|   +-- UserRepository.java
|   +-- RoleRepository.java
|   +-- AttendanceRepository.java
|   +-- LeaveRequestRepository.java
|   +-- LeaveTypeRepository.java
|   +-- LeaveBalanceRepository.java
|   +-- CompanyLeaveRepository.java
|   +-- PayslipRepository.java
+-- security/
|   +-- WebSecurityConfig.java
|   +-- JwtUtils.java
|   +-- AuthTokenFilter.java
|   +-- JwtAuthEntryPoint.java
|   +-- UserDetailsImpl.java
|   +-- UserDetailsServiceImpl.java
+-- service/
|   +-- AttendanceService.java
|   +-- AttendanceScheduler.java
|   +-- LeaveService.java
|   +-- PayrollService.java
|   +-- PayrollScheduler.java
|   +-- NotificationConsumer.java
+-- util/
    +-- GeoUtils.java
`

## Git Branching Strategy

This project follows **Git Flow**:

- `main` - Production-ready releases
- `develop` - Integration branch for features
- `feature/*` - Individual feature branches

Feature branches are created from and merged into `develop`.

## License

This project is proprietary software.
