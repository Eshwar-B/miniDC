# MiniDC REST API Documentation

All endpoints respond with `application/json`. Authenticated routes require an `Authorization: Bearer <token>` header containing a valid HS256 JWT.

---

## 1. Authentication Endpoints

### 1.1 Register Account
- **POST** `/api/auth/register`
- **Request Body:**
```json
{
  "email": "borrower@example.com",
  "password": "Password123!",
  "displayName": "Alice Boini",
  "inviteCode": "INV-A1B2-C3D4"
}
```
- **Response (201 Created):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 1.2 User Login
- **POST** `/api/auth/login`
- **Request Body:**
```json
{
  "email": "borrower@example.com",
  "password": "Password123!"
}
```
- **Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 1.3 Identity Profile
- **GET** `/api/me`
- **Response (200 OK):**
```json
{
  "id": "c1386b0f-8b2b-426b-85d0-9a3c94f58ad9",
  "email": "borrower@example.com",
  "displayName": "Alice Boini",
  "role": "MEMBER",
  "banned": false,
  "createdAt": "2026-11-06T10:00:00Z"
}
```

---

## 2. Node Management (Lender)

### 2.1 Register Compute Node
- **POST** `/api/nodes`
- **Request Body:**
```json
{
  "name": "ThinkPad-P14s"
}
```
- **Response (201 Created):**
```json
{
  "nodeId": "8f86f78a-f5b2-4d15-8408-f46bb121ef3c",
  "agentToken": "uQ2m3vK_8X9v..."
}
```
*(Token is shown only once and stored BCrypt-hashed on the server)*

### 2.2 List My Nodes
- **GET** `/api/nodes/mine`
- **Response (200 OK):**
```json
[
  {
    "id": "8f86f78a-f5b2-4d15-8408-f46bb121ef3c",
    "name": "ThinkPad-P14s",
    "status": "ONLINE",
    "totalRamMb": 32768,
    "totalCores": 16,
    "totalDiskGb": 512,
    "address": "192.168.1.150",
    "lastHeartbeat": "2026-11-06T12:00:15Z"
  }
]
```

### 2.3 Pause / Resume Node
- **PUT** `/api/nodes/{id}/pause`
- **PUT** `/api/nodes/{id}/resume`

---

## 3. Capacity Offers

### 3.1 Create Offer
- **POST** `/api/offers`
- **Request Body:**
```json
{
  "nodeId": "8f86f78a-f5b2-4d15-8408-f46bb121ef3c",
  "maxRamMb": 8192,
  "maxCores": 4,
  "maxDiskGb": 40,
  "maxHours": 8,
  "windowStart": "09:00:00",
  "windowEnd": "18:00:00",
  "autoApprove": true,
  "allowedUserIds": null
}
```

### 3.2 List Available Marketplace Offers
- **GET** `/api/offers/available`
- **Response (200 OK):**
```json
[
  {
    "id": "2d1a3c5e-8b9a-4c2d-9f1e-3a5b7c9d1e2f",
    "nodeId": "8f86f78a-f5b2-4d15-8408-f46bb121ef3c",
    "maxRamMb": 8192,
    "maxCores": 4,
    "maxDiskGb": 40,
    "maxHours": 8,
    "autoApprove": true,
    "active": true
  }
]
```

---

## 4. Lease Management

### 4.1 Request Lease (Borrower)
- **POST** `/api/leases`
- **Request Body:**
```json
{
  "offerId": "2d1a3c5e-8b9a-4c2d-9f1e-3a5b7c9d1e2f",
  "ramMb": 4096,
  "cores": 2,
  "diskGb": 20,
  "requestedHours": 2
}
```
- **Response (201 Created):**
```json
{
  "id": "e4f8a3c2-1d5b-4c7e-9a2f-8b3c4d5e6f7a",
  "offerId": "2d1a3c5e-8b9a-4c2d-9f1e-3a5b7c9d1e2f",
  "borrowerId": "c1386b0f-8b2b-426b-85d0-9a3c94f58ad9",
  "nodeId": "8f86f78a-f5b2-4d15-8408-f46bb121ef3c",
  "ramMb": 4096,
  "cores": 2,
  "diskGb": 20,
  "requestedHours": 2,
  "state": "REQUESTED",
  "vmName": "lease-e4f8a3c2",
  "createdAt": "2026-11-06T12:05:00Z"
}
```

### 4.2 Lifecycle Transitions
- **POST** `/api/leases/{id}/approve`
- **POST** `/api/leases/{id}/reject?reason=Capacity+reallocated`
- **POST** `/api/leases/{id}/kill?reason=Host+maintenance`
- **POST** `/api/leases/{id}/end`

### 4.3 Connect to Remote Desktop
- **GET** `/api/leases/{id}/connect`
- **Response (200 OK):**
```json
{
  "url": "http://localhost:8081/guacamole/#/client/lease-e4f8a3c2?token=stub-token-e4f8a3c2"
}
```

---

## 5. Guacamole Connection Payload Schema

When a lease is in the `ACTIVE` state, the server constructs this JSON payload, signs it using HMAC-SHA256, and encrypts it with AES-128-CBC for `guacamole-auth-json`:

```json
{
  "username": "borrower@example.com",
  "expires": 1793976000000,
  "connections": {
    "vm": {
      "protocol": "rdp",
      "parameters": {
        "hostname": "192.168.1.150",
        "port": "33001",
        "username": "minidc",
        "password": "decrypted-one-time-password",
        "security": "any",
        "ignore-cert": "true",
        "resize-method": "display-update",
        "enable-wallpaper": "false",
        "disable-copy": "true",
        "disable-paste": "false"
      }
    }
  }
}
```

---

## 6. Error Responses

Standardized ApiError structure returned on 4xx/5xx responses:

```json
{
  "timestamp": "2026-11-06T12:05:00Z",
  "status": 409,
  "code": "CAPACITY_EXCEEDED",
  "message": "Node capacity exceeded for requested resources"
}
```

### Standard Error Codes
- `CAPACITY_EXCEEDED`: Node RAM/CPU/Disk exhausted or max active leases per user reached.
- `OUT_OF_WINDOW`: Request submitted outside of the lender's designated daily time window.
- `NODE_OFFLINE`: Target compute node is currently unreachable.
- `FORBIDDEN_USER`: Borrower account is banned or not in the allowed user list.
- `INVALID_STATE`: Attempted state transition is disallowed by the lease state machine.

