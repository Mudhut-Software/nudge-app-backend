# Businesses Package Design

## Overview

Add a `businesses` package to Nudge that allows users to create businesses, invite members, and manage roles. Businesses are categorized by type (e-pharmacies, mobile mechanics, catering, plumbing, etc.) and the core purpose is to connect service providers with clients through scheduled appointments (appointments are a future feature).

## Key Decisions

- **Two-layer role system**: Platform-level roles on `User` (for Nudge staff) and business-level roles on `BusinessMember` (per-business context)
- **Role-based permissions only**: Fixed capabilities per business role (OWNER > ADMIN > MANAGER > STAFF). No granular permission system for now.
- **Database-managed categories**: `BusinessCategory` entity with parent-child self-reference for subcategories, manageable via admin API
- **Single owner per business**: Direct FK on `Business` entity. Owner is also a `BusinessMember` with `OWNER` role.
- **Invitation with acceptance**: Invitees must explicitly accept. Pending invitations are resolved on signup.
- **Flat package structure**: Mirrors existing `users` package conventions

## Entity Relationship Diagram

```
User 1:N Business (as owner)
User N:M Business (through BusinessMember)
Business N:1 BusinessCategory
BusinessCategory self-referencing (parent -> children for subcategories)
BusinessInvitation N:1 Business, references inviter (User) and optionally invitee (User)
```

## Entities

### BusinessCategory

| Column      | Type          | Constraints                                    |
| ----------- | ------------- | ---------------------------------------------- |
| id          | Long          | PK, auto-generated                             |
| name        | String        | unique, not blank                              |
| description | String        | nullable                                       |
| parent_id   | Long          | FK -> business_categories (nullable, self-ref) |
| is_active   | Boolean       | default true                                   |
| created_at  | LocalDateTime | auto                                           |
| updated_at  | LocalDateTime | auto                                           |

- Top-level: `parent_id = null` (e.g., "Healthcare", "Home Services", "Events")
- Subcategories: `parent_id` points to parent (e.g., "E-Pharmacy" -> "Healthcare")

### Business

| Column       | Type          | Constraints                      |
| ------------ | ------------- | -------------------------------- |
| id           | Long          | PK, auto-generated              |
| name         | String        | not blank                        |
| description  | String        | nullable                         |
| owner_id     | Long          | FK -> users, not null            |
| category_id  | Long          | FK -> business_categories, not null |
| phone        | String        | nullable                         |
| email        | String        | nullable                         |
| logo_url     | String        | nullable                         |
| address      | String        | nullable                         |
| service_area | String        | not blank                        |
| status       | Enum          | ACTIVE, INACTIVE, SUSPENDED (default ACTIVE) |
| created_at   | LocalDateTime | auto                             |
| updated_at   | LocalDateTime | auto                             |

### BusinessMember

| Column      | Type          | Constraints                        |
| ----------- | ------------- | ---------------------------------- |
| id          | Long          | PK, auto-generated                 |
| user_id     | Long          | FK -> users, not null              |
| business_id | Long          | FK -> businesses, not null         |
| role        | Enum          | OWNER, ADMIN, MANAGER, STAFF       |
| is_active   | Boolean       | default true                       |
| joined_at   | LocalDateTime | auto                               |
| updated_at  | LocalDateTime | auto                               |

- Unique constraint on `(user_id, business_id)`

### BusinessInvitation

| Column      | Type          | Constraints                              |
| ----------- | ------------- | ---------------------------------------- |
| id          | Long          | PK, auto-generated                       |
| business_id | Long          | FK -> businesses, not null               |
| inviter_id  | Long          | FK -> users, not null                    |
| invitee_id  | Long          | FK -> users, nullable                    |
| email       | String        | not blank                                |
| role        | Enum          | ADMIN, MANAGER, STAFF (not OWNER)        |
| status      | Enum          | PENDING, ACCEPTED, DECLINED, EXPIRED     |
| token       | String        | unique                                   |
| expiry_date | LocalDateTime | default 7 days                           |
| created_at  | LocalDateTime | auto                                     |
| updated_at  | LocalDateTime | auto                                     |

## Enums

### UserRole (modified — platform level)

```
SUPER_ADMIN   — Full platform control (Nudge founders/CTO)
ADMIN         — Manage categories, review businesses, handle reports
SUPPORT       — Handle user issues, view data, limited actions
BASIC_USER    — Regular user (business owners + clients)
```

### BusinessRole (new — per-business context)

```
OWNER    — Full business control, delete business
ADMIN    — Edit profile, manage members/invitations
MANAGER  — View/manage appointments
STAFF    — View own appointments, handle assigned tasks
```

### BusinessStatus (new)

```
ACTIVE, INACTIVE, SUSPENDED
```

### InvitationStatus (new)

```
PENDING, ACCEPTED, DECLINED, EXPIRED
```

## Role Permission Matrix (Business Level)

| Capability                     | OWNER | ADMIN | MANAGER | STAFF |
| ------------------------------ | ----- | ----- | ------- | ----- |
| Delete business                | x     |       |         |       |
| Edit business profile          | x     | x     |         |       |
| Manage members (invite/remove) | x     | x     |         |       |
| Change member roles            | x     | x     |         |       |
| View all appointments          | x     | x     | x       |       |
| Manage appointments            | x     | x     | x       |       |
| View own appointments          | x     | x     | x       | x     |
| Handle assigned tasks          | x     | x     | x       | x     |

Rules:
- OWNER is auto-assigned at business creation — cannot be invited as OWNER
- ADMIN cannot remove/change the OWNER
- ADMIN can manage MANAGER and STAFF but not other ADMINs — only OWNER manages ADMINs
- Appointment capabilities listed for future reference

## API Endpoints

### Business Categories (platform admin)

| Method | Path                                  | Description                    | Auth              |
| ------ | ------------------------------------- | ------------------------------ | ----------------- |
| POST   | `/api/v1/categories`                  | Create category                | SUPER_ADMIN/ADMIN |
| GET    | `/api/v1/categories`                  | List top-level categories      | Authenticated     |
| GET    | `/api/v1/categories/{id}/subcategories` | List subcategories           | Authenticated     |

### Businesses

| Method | Path                        | Description              | Auth                    |
| ------ | --------------------------- | ------------------------ | ----------------------- |
| POST   | `/api/v1/businesses`        | Create business          | Authenticated           |
| GET    | `/api/v1/businesses/{id}`   | Get business details     | Authenticated           |
| PUT    | `/api/v1/businesses/{id}`   | Update business          | Business OWNER/ADMIN    |
| DELETE | `/api/v1/businesses/{id}`   | Delete business          | Business OWNER          |
| GET    | `/api/v1/businesses/my`     | List my owned businesses | Authenticated           |

### Business Members

| Method | Path                                            | Description        | Auth                          |
| ------ | ----------------------------------------------- | ------------------ | ----------------------------- |
| GET    | `/api/v1/businesses/{id}/members`               | List members       | Business OWNER/ADMIN/MANAGER  |
| PUT    | `/api/v1/businesses/{id}/members/{memberId}/role` | Change role      | Business OWNER/ADMIN          |
| DELETE | `/api/v1/businesses/{id}/members/{memberId}`    | Remove member      | Business OWNER/ADMIN          |
| DELETE | `/api/v1/businesses/{id}/members/me`            | Leave business     | Self (except OWNER)           |

### Business Invitations

| Method | Path                                                  | Description          | Auth               |
| ------ | ----------------------------------------------------- | -------------------- | ------------------- |
| POST   | `/api/v1/businesses/{id}/invitations`                 | Send invitation      | Business OWNER/ADMIN |
| GET    | `/api/v1/businesses/{id}/invitations`                 | List pending invites | Business OWNER/ADMIN |
| POST   | `/api/v1/invitations/{token}/accept`                  | Accept invitation    | Invitee             |
| POST   | `/api/v1/invitations/{token}/decline`                 | Decline invitation   | Invitee             |
| DELETE | `/api/v1/businesses/{id}/invitations/{invitationId}`  | Cancel invitation    | Business OWNER/ADMIN |
| GET    | `/api/v1/invitations/my`                              | My pending invites   | Authenticated       |

### User Memberships

| Method | Path                       | Description                     | Auth          |
| ------ | -------------------------- | ------------------------------- | ------------- |
| GET    | `/api/v1/users/me/businesses` | List businesses I'm a member of | Authenticated |

## Invitation Flow

1. OWNER/ADMIN sends invite (POST with email + role)
   - User exists? Set invitee_id, send email notification
   - User doesn't exist? invitee_id = null, send email with invite link
   - Already a member? Reject
   - Pending invite exists? Reject
   - Creates BusinessInvitation (PENDING, UUID token, 7-day expiry)

2. Invitee accepts (POST /invitations/{token}/accept)
   - Validates: token exists, not expired, status=PENDING, user email matches
   - Creates BusinessMember, sets invitation status=ACCEPTED

3. Invitee declines (POST /invitations/{token}/decline)
   - Sets status=DECLINED

4. New user signup resolution
   - After registration, check for invitations matching email
   - Set invitee_id on matching invitations (still requires explicit accept)

## Changes to Existing Code

### UserRole enum
```
Before: SUPER_ADMIN, BUSINESS_ADMIN, BUSINESS_STAFF, BASIC_USER
After:  SUPER_ADMIN, ADMIN, SUPPORT, BASIC_USER
```

### NudgeUserDetailsService
Update to use the user's actual `role` field instead of hardcoding `BASIC_USER`.

### SecurityConfig
- `/api/v1/categories` POST: SUPER_ADMIN/ADMIN only
- `/api/v1/businesses/**`: Authenticated users
- `/api/v1/invitations/**`: Authenticated users
- Business-level role checks in service layer (contextual per business)

### RegistrationService
Add post-registration hook to resolve pending invitations by matching email.

## Package Structure

```
com.mudhut.nudge.businesses/
├── entities/
│   ├── Business.kt
│   ├── BusinessMember.kt
│   ├── BusinessCategory.kt
│   ├── BusinessInvitation.kt
│   ├── BusinessRole.kt (enum)
│   ├── BusinessStatus.kt (enum)
│   └── InvitationStatus.kt (enum)
├── repositories/
│   ├── BusinessRepository.kt
│   ├── BusinessMemberRepository.kt
│   ├── BusinessCategoryRepository.kt
│   └── BusinessInvitationRepository.kt
├── services/
│   ├── BusinessService.kt
│   ├── BusinessMemberService.kt
│   ├── BusinessCategoryService.kt
│   └── BusinessInvitationService.kt
├── controllers/
│   ├── BusinessController.kt
│   ├── BusinessMemberController.kt
│   ├── BusinessCategoryController.kt
│   └── BusinessInvitationController.kt
└── models/
    ├── CreateBusinessRequest.kt
    ├── UpdateBusinessRequest.kt
    ├── BusinessResponse.kt
    ├── CreateCategoryRequest.kt
    ├── InviteMemberRequest.kt
    ├── UpdateMemberRoleRequest.kt
    └── BusinessMemberResponse.kt
```
