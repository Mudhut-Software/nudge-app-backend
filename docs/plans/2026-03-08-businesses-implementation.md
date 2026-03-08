# Businesses Package Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the businesses package with categories, business CRUD, membership, and invitations as described in the [design doc](./2026-03-08-businesses-design.md).

**Architecture:** Flat package structure under `com.mudhut.nudge.businesses` with entities, repositories, services, controllers, and models sub-packages. Two-layer role system: platform roles on User, business roles on BusinessMember. TDD with Spring Boot test patterns matching the existing `users` package.

**Tech Stack:** Kotlin, Spring Boot 3.4.2, Spring Data JPA, PostgreSQL, Spring Security, JUnit 5, Mockito, MockMvc

---

## Shorthand

- `SRC` = `src/main/kotlin/com/mudhut/nudge`
- `TEST` = `src/test/kotlin/com/mudhut/nudge`

---

### Task 1: Modify UserRole Enum and Fix NudgeUserDetailsService

**Files:**
- Modify: `SRC/users/entities/UserRole.kt`
- Modify: `SRC/users/services/helpers/NudgeUserDetailsService.kt`

**Step 1: Update UserRole enum**

Replace the contents of `SRC/users/entities/UserRole.kt`:

```kotlin
package com.mudhut.nudge.users.entities

enum class UserRole {
    SUPER_ADMIN,
    ADMIN,
    SUPPORT,
    BASIC_USER
}
```

**Step 2: Fix NudgeUserDetailsService to use actual user role**

Replace the contents of `SRC/users/services/helpers/NudgeUserDetailsService.kt`:

```kotlin
package com.mudhut.nudge.users.services.helpers

import com.mudhut.nudge.users.repositories.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class NudgeUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            .orElseThrow { UsernameNotFoundException("User not found with username: $username") }

        val authorities = listOf(
            SimpleGrantedAuthority("ROLE_${user.role?.name ?: "BASIC_USER"}")
        )

        return org.springframework.security.core.userdetails.User(
            user.email,
            user.password,
            authorities
        )
    }
}
```

**Step 3: Update SecurityConfig to secure new endpoints**

Modify `SRC/config/SecurityConfig.kt` — update the `authorizeHttpRequests` block:

```kotlin
.authorizeHttpRequests { auth ->
    auth
        .requestMatchers("/reset-password").permitAll()
        .requestMatchers("/verify-email").permitAll()
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/v1/categories"
        ).hasAnyRole("SUPER_ADMIN", "ADMIN")
        .anyRequest().authenticated()
}
```

**Step 4: Verify the app compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/users/entities/UserRole.kt \
        src/main/kotlin/com/mudhut/nudge/users/services/helpers/NudgeUserDetailsService.kt \
        src/main/kotlin/com/mudhut/nudge/config/SecurityConfig.kt
git commit -m "refactor: update UserRole to platform-level roles and fix auth service"
```

---

### Task 2: Create Business Enums and Custom Exceptions

**Files:**
- Create: `SRC/businesses/entities/BusinessRole.kt`
- Create: `SRC/businesses/entities/BusinessStatus.kt`
- Create: `SRC/businesses/entities/InvitationStatus.kt`
- Create: `SRC/utils/exceptions/BusinessNotFoundException.kt`
- Create: `SRC/utils/exceptions/BusinessAccessDeniedException.kt`
- Create: `SRC/utils/exceptions/InvitationException.kt`
- Modify: `SRC/utils/exceptions/GlobalExceptionHandler.kt`

**Step 1: Create BusinessRole enum**

Create `SRC/businesses/entities/BusinessRole.kt`:

```kotlin
package com.mudhut.nudge.businesses.entities

enum class BusinessRole {
    OWNER,
    ADMIN,
    MANAGER,
    STAFF
}
```

**Step 2: Create BusinessStatus enum**

Create `SRC/businesses/entities/BusinessStatus.kt`:

```kotlin
package com.mudhut.nudge.businesses.entities

enum class BusinessStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
```

**Step 3: Create InvitationStatus enum**

Create `SRC/businesses/entities/InvitationStatus.kt`:

```kotlin
package com.mudhut.nudge.businesses.entities

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}
```

**Step 4: Create custom exceptions**

Create `SRC/utils/exceptions/BusinessNotFoundException.kt`:

```kotlin
package com.mudhut.nudge.utils.exceptions

class BusinessNotFoundException(message: String) : RuntimeException(message)
```

Create `SRC/utils/exceptions/BusinessAccessDeniedException.kt`:

```kotlin
package com.mudhut.nudge.utils.exceptions

class BusinessAccessDeniedException(message: String) : RuntimeException(message)
```

Create `SRC/utils/exceptions/InvitationException.kt`:

```kotlin
package com.mudhut.nudge.utils.exceptions

class InvitationException(message: String) : RuntimeException(message)
```

**Step 5: Add exception handlers to GlobalExceptionHandler**

Add these handlers to `SRC/utils/exceptions/GlobalExceptionHandler.kt` (before the generic `handleGenericException`):

```kotlin
@ExceptionHandler(BusinessNotFoundException::class)
fun handleBusinessNotFoundException(ex: BusinessNotFoundException): ResponseEntity<ErrorResponse> {
    logger.warn("Business not found: {}", ex.message)
    return ResponseEntity(
        ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "Business not found"),
        HttpStatus.NOT_FOUND
    )
}

@ExceptionHandler(BusinessAccessDeniedException::class)
fun handleBusinessAccessDeniedException(ex: BusinessAccessDeniedException): ResponseEntity<ErrorResponse> {
    logger.warn("Business access denied: {}", ex.message)
    return ResponseEntity(
        ErrorResponse(ERROR_CODE_AUTHORIZATION, ex.message ?: "Access denied"),
        HttpStatus.FORBIDDEN
    )
}

@ExceptionHandler(InvitationException::class)
fun handleInvitationException(ex: InvitationException): ResponseEntity<ErrorResponse> {
    logger.warn("Invitation error: {}", ex.message)
    return ResponseEntity(
        ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Invitation error"),
        HttpStatus.BAD_REQUEST
    )
}
```

**Step 6: Verify the app compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

**Step 7: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/entities/ \
        src/main/kotlin/com/mudhut/nudge/utils/exceptions/
git commit -m "feat: add business enums and custom exceptions"
```

---

### Task 3: Create BusinessCategory Entity, Repository, and DTOs

**Files:**
- Create: `SRC/businesses/entities/BusinessCategory.kt`
- Create: `SRC/businesses/repositories/BusinessCategoryRepository.kt`
- Create: `SRC/businesses/models/CreateCategoryRequest.kt`
- Create: `SRC/businesses/models/CategoryResponse.kt`

**Step 1: Create BusinessCategory entity**

Create `SRC/businesses/entities/BusinessCategory.kt`:

```kotlin
package com.mudhut.nudge.businesses.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "business_categories")
class BusinessCategory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @field:NotBlank
    @Column(unique = true)
    var name: String? = null,

    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: BusinessCategory? = null,

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    var children: MutableList<BusinessCategory> = mutableListOf(),

    var isActive: Boolean = true,

    @CreationTimestamp
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null
)
```

**Step 2: Create BusinessCategoryRepository**

Create `SRC/businesses/repositories/BusinessCategoryRepository.kt`:

```kotlin
package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessCategoryRepository : JpaRepository<BusinessCategory, Long> {
    fun findByParentIsNullAndIsActiveTrue(): List<BusinessCategory>
    fun findByParentIdAndIsActiveTrue(parentId: Long): List<BusinessCategory>
    fun existsByName(name: String): Boolean
}
```

**Step 3: Create DTOs**

Create `SRC/businesses/models/CreateCategoryRequest.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.NotBlank

data class CreateCategoryRequest(
    @field:NotBlank(message = "Category name is required")
    var name: String? = null,

    var description: String? = null,

    var parentId: Long? = null
)
```

Create `SRC/businesses/models/CategoryResponse.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

data class CategoryResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val parentId: Long?,
    val isActive: Boolean,
    val hasChildren: Boolean
)
```

**Step 4: Verify the app compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/
git commit -m "feat: add BusinessCategory entity, repository, and DTOs"
```

---

### Task 4: Create BusinessCategoryService and Controller

**Files:**
- Create: `SRC/businesses/services/BusinessCategoryService.kt`
- Create: `SRC/businesses/controllers/BusinessCategoryController.kt`
- Create: `TEST/businesses/controllers/BusinessCategoryControllerTest.kt`

**Step 1: Write the failing test**

Create `TEST/businesses/controllers/BusinessCategoryControllerTest.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.services.BusinessCategoryService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@AutoConfigureMockMvc
class BusinessCategoryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessCategoryService: BusinessCategoryService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun testCreateCategory_Success() {
        val request = CreateCategoryRequest(
            name = "Healthcare",
            description = "Healthcare services"
        )

        val response = CategoryResponse(
            id = 1L,
            name = "Healthcare",
            description = "Healthcare services",
            parentId = null,
            isActive = true,
            hasChildren = false
        )

        Mockito.`when`(businessCategoryService.createCategory(Mockito.any(CreateCategoryRequest::class.java)))
            .thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Healthcare"))
    }

    @Test
    @WithMockUser(roles = ["BASIC_USER"])
    fun testCreateCategory_ForbiddenForBasicUser() {
        val request = CreateCategoryRequest(name = "Healthcare")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    @WithMockUser
    fun testGetTopLevelCategories_Success() {
        val categories = listOf(
            CategoryResponse(1L, "Healthcare", null, null, true, true),
            CategoryResponse(2L, "Home Services", null, null, true, false)
        )

        Mockito.`when`(businessCategoryService.getTopLevelCategories()).thenReturn(categories)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("Healthcare"))
    }

    @Test
    @WithMockUser
    fun testGetSubcategories_Success() {
        val subcategories = listOf(
            CategoryResponse(3L, "E-Pharmacy", "Online pharmacy", 1L, true, false)
        )

        Mockito.`when`(businessCategoryService.getSubcategories(1L)).thenReturn(subcategories)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories/1/subcategories"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("E-Pharmacy"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].parentId").value(1))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=BusinessCategoryControllerTest -q`
Expected: FAIL — `BusinessCategoryService` and controller don't exist yet

**Step 3: Create BusinessCategoryService**

Create `SRC/businesses/services/BusinessCategoryService.kt`:

```kotlin
package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import org.springframework.stereotype.Service

@Service
class BusinessCategoryService(
    private val categoryRepository: BusinessCategoryRepository
) {

    fun createCategory(request: CreateCategoryRequest): CategoryResponse {
        if (categoryRepository.existsByName(request.name!!)) {
            throw IllegalArgumentException("Category with name '${request.name}' already exists")
        }

        val category = BusinessCategory().apply {
            name = request.name
            description = request.description
        }

        if (request.parentId != null) {
            val parent = categoryRepository.findById(request.parentId!!)
                .orElseThrow { IllegalArgumentException("Parent category not found with id: ${request.parentId}") }
            category.parent = parent
        }

        val saved = categoryRepository.save(category)
        return toResponse(saved)
    }

    fun getTopLevelCategories(): List<CategoryResponse> {
        return categoryRepository.findByParentIsNullAndIsActiveTrue()
            .map { toResponse(it) }
    }

    fun getSubcategories(parentId: Long): List<CategoryResponse> {
        return categoryRepository.findByParentIdAndIsActiveTrue(parentId)
            .map { toResponse(it) }
    }

    private fun toResponse(category: BusinessCategory): CategoryResponse {
        return CategoryResponse(
            id = category.id!!,
            name = category.name!!,
            description = category.description,
            parentId = category.parent?.id,
            isActive = category.isActive,
            hasChildren = category.children.isNotEmpty()
        )
    }
}
```

**Step 4: Create BusinessCategoryController**

Create `SRC/businesses/controllers/BusinessCategoryController.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.services.BusinessCategoryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/categories")
class BusinessCategoryController(
    private val categoryService: BusinessCategoryService
) {

    @PostMapping
    fun createCategory(@Valid @RequestBody request: CreateCategoryRequest): ResponseEntity<CategoryResponse> {
        val category = categoryService.createCategory(request)
        return ResponseEntity(category, HttpStatus.CREATED)
    }

    @GetMapping
    fun getTopLevelCategories(): ResponseEntity<List<CategoryResponse>> {
        return ResponseEntity.ok(categoryService.getTopLevelCategories())
    }

    @GetMapping("/{id}/subcategories")
    fun getSubcategories(@PathVariable id: Long): ResponseEntity<List<CategoryResponse>> {
        return ResponseEntity.ok(categoryService.getSubcategories(id))
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=BusinessCategoryControllerTest -q`
Expected: 4 tests PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessCategoryService.kt \
        src/main/kotlin/com/mudhut/nudge/businesses/controllers/BusinessCategoryController.kt \
        src/test/kotlin/com/mudhut/nudge/businesses/
git commit -m "feat: add BusinessCategory service, controller, and tests"
```

---

### Task 5: Create Business Entity, Repository, and DTOs

**Files:**
- Create: `SRC/businesses/entities/Business.kt`
- Create: `SRC/businesses/repositories/BusinessRepository.kt`
- Create: `SRC/businesses/models/CreateBusinessRequest.kt`
- Create: `SRC/businesses/models/UpdateBusinessRequest.kt`
- Create: `SRC/businesses/models/BusinessResponse.kt`

**Step 1: Create Business entity**

Create `SRC/businesses/entities/Business.kt`:

```kotlin
package com.mudhut.nudge.businesses.entities

import com.mudhut.nudge.users.entities.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "businesses")
class Business(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @field:NotBlank
    var name: String? = null,

    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: BusinessCategory? = null,

    var phone: String? = null,

    var email: String? = null,

    var logoUrl: String? = null,

    var address: String? = null,

    @field:NotBlank
    var serviceArea: String? = null,

    @Enumerated(EnumType.STRING)
    var status: BusinessStatus = BusinessStatus.ACTIVE,

    @CreationTimestamp
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null
)
```

**Step 2: Create BusinessRepository**

Create `SRC/businesses/repositories/BusinessRepository.kt`:

```kotlin
package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.Business
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessRepository : JpaRepository<Business, Long> {
    fun findByOwnerId(ownerId: Long): List<Business>
}
```

**Step 3: Create DTOs**

Create `SRC/businesses/models/CreateBusinessRequest.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateBusinessRequest(
    @field:NotBlank(message = "Business name is required")
    var name: String? = null,

    var description: String? = null,

    @field:NotNull(message = "Category is required")
    var categoryId: Long? = null,

    var phone: String? = null,

    var email: String? = null,

    var logoUrl: String? = null,

    var address: String? = null,

    @field:NotBlank(message = "Service area is required")
    var serviceArea: String? = null
)
```

Create `SRC/businesses/models/UpdateBusinessRequest.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

data class UpdateBusinessRequest(
    var name: String? = null,
    var description: String? = null,
    var categoryId: Long? = null,
    var phone: String? = null,
    var email: String? = null,
    var logoUrl: String? = null,
    var address: String? = null,
    var serviceArea: String? = null
)
```

Create `SRC/businesses/models/BusinessResponse.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessStatus

data class BusinessResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val ownerId: Long,
    val ownerEmail: String,
    val categoryId: Long,
    val categoryName: String,
    val phone: String?,
    val email: String?,
    val logoUrl: String?,
    val address: String?,
    val serviceArea: String,
    val status: BusinessStatus
)
```

**Step 4: Verify the app compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/
git commit -m "feat: add Business entity, repository, and DTOs"
```

---

### Task 6: Create BusinessMember Entity and Repository

**Files:**
- Create: `SRC/businesses/entities/BusinessMember.kt`
- Create: `SRC/businesses/repositories/BusinessMemberRepository.kt`
- Create: `SRC/businesses/models/BusinessMemberResponse.kt`
- Create: `SRC/businesses/models/UpdateMemberRoleRequest.kt`

**Step 1: Create BusinessMember entity**

Create `SRC/businesses/entities/BusinessMember.kt`:

```kotlin
package com.mudhut.nudge.businesses.entities

import com.mudhut.nudge.users.entities.User
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "business_members",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "business_id"])]
)
class BusinessMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @Enumerated(EnumType.STRING)
    var role: BusinessRole? = null,

    var isActive: Boolean = true,

    @CreationTimestamp
    var joinedAt: LocalDateTime? = null,

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null
)
```

**Step 2: Create BusinessMemberRepository**

Create `SRC/businesses/repositories/BusinessMemberRepository.kt`:

```kotlin
package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BusinessMemberRepository : JpaRepository<BusinessMember, Long> {
    fun findByBusinessIdAndIsActiveTrue(businessId: Long): List<BusinessMember>
    fun findByUserIdAndIsActiveTrue(userId: Long): List<BusinessMember>
    fun findByBusinessIdAndUserId(businessId: Long, userId: Long): Optional<BusinessMember>
    fun existsByBusinessIdAndUserId(businessId: Long, userId: Long): Boolean
    fun findByBusinessIdAndRole(businessId: Long, role: BusinessRole): List<BusinessMember>
}
```

**Step 3: Create DTOs**

Create `SRC/businesses/models/BusinessMemberResponse.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessRole
import java.time.LocalDateTime

data class BusinessMemberResponse(
    val id: Long,
    val userId: Long,
    val userEmail: String,
    val businessId: Long,
    val role: BusinessRole,
    val isActive: Boolean,
    val joinedAt: LocalDateTime?
)
```

Create `SRC/businesses/models/UpdateMemberRoleRequest.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessRole
import jakarta.validation.constraints.NotNull

data class UpdateMemberRoleRequest(
    @field:NotNull(message = "Role is required")
    var role: BusinessRole? = null
)
```

**Step 4: Verify the app compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/
git commit -m "feat: add BusinessMember entity, repository, and DTOs"
```

---

### Task 7: Create BusinessInvitation Entity and Repository

**Files:**
- Create: `SRC/businesses/entities/BusinessInvitation.kt`
- Create: `SRC/businesses/repositories/BusinessInvitationRepository.kt`
- Create: `SRC/businesses/models/InviteMemberRequest.kt`
- Create: `SRC/businesses/models/InvitationResponse.kt`

**Step 1: Create BusinessInvitation entity**

Create `SRC/businesses/entities/BusinessInvitation.kt`:

```kotlin
package com.mudhut.nudge.businesses.entities

import com.mudhut.nudge.users.entities.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "business_invitations")
class BusinessInvitation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    var inviter: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id")
    var invitee: User? = null,

    @field:NotBlank
    var email: String? = null,

    @Enumerated(EnumType.STRING)
    var role: BusinessRole? = null,

    @Enumerated(EnumType.STRING)
    var status: InvitationStatus = InvitationStatus.PENDING,

    @Column(unique = true)
    var token: String? = null,

    var expiryDate: LocalDateTime? = null,

    @CreationTimestamp
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null
) {
    companion object {
        const val EXPIRY_DAYS = 7L
    }

    fun isExpired(): Boolean = expiryDate?.isBefore(LocalDateTime.now()) ?: true
}
```

**Step 2: Create BusinessInvitationRepository**

Create `SRC/businesses/repositories/BusinessInvitationRepository.kt`:

```kotlin
package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessInvitation
import com.mudhut.nudge.businesses.entities.InvitationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface BusinessInvitationRepository : JpaRepository<BusinessInvitation, Long> {
    fun findByToken(token: String): Optional<BusinessInvitation>
    fun findByBusinessIdAndStatus(businessId: Long, status: InvitationStatus): List<BusinessInvitation>
    fun findByEmailAndStatus(email: String, status: InvitationStatus): List<BusinessInvitation>
    fun existsByBusinessIdAndEmailAndStatus(businessId: Long, email: String, status: InvitationStatus): Boolean
}
```

**Step 3: Create DTOs**

Create `SRC/businesses/models/InviteMemberRequest.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class InviteMemberRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Please provide a valid email")
    var email: String? = null,

    @field:NotNull(message = "Role is required")
    var role: BusinessRole? = null
)
```

Create `SRC/businesses/models/InvitationResponse.kt`:

```kotlin
package com.mudhut.nudge.businesses.models

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.InvitationStatus
import java.time.LocalDateTime

data class InvitationResponse(
    val id: Long,
    val businessId: Long,
    val businessName: String,
    val inviterEmail: String,
    val inviteeEmail: String,
    val role: BusinessRole,
    val status: InvitationStatus,
    val expiryDate: LocalDateTime?,
    val createdAt: LocalDateTime?
)
```

**Step 4: Verify the app compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/
git commit -m "feat: add BusinessInvitation entity, repository, and DTOs"
```

---

### Task 8: Create BusinessService and Controller

**Files:**
- Create: `SRC/businesses/services/BusinessService.kt`
- Create: `SRC/businesses/controllers/BusinessController.kt`
- Create: `TEST/businesses/controllers/BusinessControllerTest.kt`

**Step 1: Write the failing test**

Create `TEST/businesses/controllers/BusinessControllerTest.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.models.BusinessResponse
import com.mudhut.nudge.businesses.models.CreateBusinessRequest
import com.mudhut.nudge.businesses.models.UpdateBusinessRequest
import com.mudhut.nudge.businesses.services.BusinessService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@AutoConfigureMockMvc
class BusinessControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessService: BusinessService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testCreateBusiness_Success() {
        val request = CreateBusinessRequest(
            name = "Test Pharmacy",
            categoryId = 1L,
            serviceArea = "Kampala"
        )

        val response = BusinessResponse(
            id = 1L,
            name = "Test Pharmacy",
            description = null,
            ownerId = 1L,
            ownerEmail = "owner@test.com",
            categoryId = 1L,
            categoryName = "Healthcare",
            phone = null,
            email = null,
            logoUrl = null,
            address = null,
            serviceArea = "Kampala",
            status = BusinessStatus.ACTIVE
        )

        Mockito.`when`(businessService.createBusiness(
            Mockito.any(CreateBusinessRequest::class.java),
            Mockito.anyString()
        )).thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Test Pharmacy"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.serviceArea").value("Kampala"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testGetBusiness_Success() {
        val response = BusinessResponse(
            id = 1L,
            name = "Test Pharmacy",
            description = null,
            ownerId = 1L,
            ownerEmail = "owner@test.com",
            categoryId = 1L,
            categoryName = "Healthcare",
            phone = null,
            email = null,
            logoUrl = null,
            address = null,
            serviceArea = "Kampala",
            status = BusinessStatus.ACTIVE
        )

        Mockito.`when`(businessService.getBusinessById(1L)).thenReturn(response)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Test Pharmacy"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testGetMyBusinesses_Success() {
        val businesses = listOf(
            BusinessResponse(
                id = 1L, name = "Biz 1", description = null, ownerId = 1L,
                ownerEmail = "owner@test.com", categoryId = 1L, categoryName = "Healthcare",
                phone = null, email = null, logoUrl = null, address = null,
                serviceArea = "Kampala", status = BusinessStatus.ACTIVE
            )
        )

        Mockito.`when`(businessService.getMyBusinesses(Mockito.anyString())).thenReturn(businesses)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/my"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
    }

    @Test
    fun testCreateBusiness_Unauthenticated() {
        val request = CreateBusinessRequest(
            name = "Test Pharmacy",
            categoryId = 1L,
            serviceArea = "Kampala"
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=BusinessControllerTest -q`
Expected: FAIL — `BusinessService` and controller don't exist yet

**Step 3: Create BusinessService**

Create `SRC/businesses/services/BusinessService.kt`:

```kotlin
package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.models.BusinessResponse
import com.mudhut.nudge.businesses.models.CreateBusinessRequest
import com.mudhut.nudge.businesses.models.UpdateBusinessRequest
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class BusinessService(
    private val businessRepository: BusinessRepository,
    private val businessCategoryRepository: BusinessCategoryRepository,
    private val businessMemberRepository: BusinessMemberRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createBusiness(request: CreateBusinessRequest, ownerEmail: String): BusinessResponse {
        val owner = userRepository.findByEmail(ownerEmail)
            .orElseThrow { EntityNotFoundException("User not found with email: $ownerEmail") }

        val category = businessCategoryRepository.findById(request.categoryId!!)
            .orElseThrow { IllegalArgumentException("Category not found with id: ${request.categoryId}") }

        val business = Business().apply {
            name = request.name
            description = request.description
            this.owner = owner
            this.category = category
            phone = request.phone
            email = request.email
            logoUrl = request.logoUrl
            address = request.address
            serviceArea = request.serviceArea
        }

        val savedBusiness = businessRepository.save(business)

        val ownerMembership = BusinessMember().apply {
            user = owner
            this.business = savedBusiness
            role = BusinessRole.OWNER
        }
        businessMemberRepository.save(ownerMembership)

        return toResponse(savedBusiness)
    }

    fun getBusinessById(id: Long): BusinessResponse {
        val business = businessRepository.findById(id)
            .orElseThrow { BusinessNotFoundException("Business not found with id: $id") }
        return toResponse(business)
    }

    @Transactional
    fun updateBusiness(id: Long, request: UpdateBusinessRequest, userEmail: String): BusinessResponse {
        val business = businessRepository.findById(id)
            .orElseThrow { BusinessNotFoundException("Business not found with id: $id") }

        requireRole(id, userEmail, BusinessRole.ADMIN)

        request.name?.let { business.name = it }
        request.description?.let { business.description = it }
        request.categoryId?.let { categoryId ->
            val category = businessCategoryRepository.findById(categoryId)
                .orElseThrow { IllegalArgumentException("Category not found with id: $categoryId") }
            business.category = category
        }
        request.phone?.let { business.phone = it }
        request.email?.let { business.email = it }
        request.logoUrl?.let { business.logoUrl = it }
        request.address?.let { business.address = it }
        request.serviceArea?.let { business.serviceArea = it }

        val saved = businessRepository.save(business)
        return toResponse(saved)
    }

    @Transactional
    fun deleteBusiness(id: Long, userEmail: String) {
        val business = businessRepository.findById(id)
            .orElseThrow { BusinessNotFoundException("Business not found with id: $id") }

        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        if (business.owner?.id != user.id) {
            throw BusinessAccessDeniedException("Only the owner can delete this business")
        }

        businessRepository.delete(business)
    }

    fun getMyBusinesses(ownerEmail: String): List<BusinessResponse> {
        val owner = userRepository.findByEmail(ownerEmail)
            .orElseThrow { EntityNotFoundException("User not found") }
        return businessRepository.findByOwnerId(owner.id!!)
            .map { toResponse(it) }
    }

    fun requireRole(businessId: Long, userEmail: String, minimumRole: BusinessRole) {
        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val member = businessMemberRepository.findByBusinessIdAndUserId(businessId, user.id!!)
            .orElseThrow { BusinessAccessDeniedException("You are not a member of this business") }

        if (!member.isActive) {
            throw BusinessAccessDeniedException("Your membership is inactive")
        }

        val roleHierarchy = listOf(BusinessRole.OWNER, BusinessRole.ADMIN, BusinessRole.MANAGER, BusinessRole.STAFF)
        val userRoleIndex = roleHierarchy.indexOf(member.role)
        val requiredRoleIndex = roleHierarchy.indexOf(minimumRole)

        if (userRoleIndex > requiredRoleIndex) {
            throw BusinessAccessDeniedException("Insufficient role. Required: $minimumRole or higher")
        }
    }

    private fun toResponse(business: Business): BusinessResponse {
        return BusinessResponse(
            id = business.id!!,
            name = business.name!!,
            description = business.description,
            ownerId = business.owner!!.id!!,
            ownerEmail = business.owner!!.email!!,
            categoryId = business.category!!.id!!,
            categoryName = business.category!!.name!!,
            phone = business.phone,
            email = business.email,
            logoUrl = business.logoUrl,
            address = business.address,
            serviceArea = business.serviceArea!!,
            status = business.status
        )
    }
}
```

**Step 4: Create BusinessController**

Create `SRC/businesses/controllers/BusinessController.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.BusinessResponse
import com.mudhut.nudge.businesses.models.CreateBusinessRequest
import com.mudhut.nudge.businesses.models.UpdateBusinessRequest
import com.mudhut.nudge.businesses.services.BusinessService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/businesses")
class BusinessController(
    private val businessService: BusinessService
) {

    @PostMapping
    fun createBusiness(
        @Valid @RequestBody request: CreateBusinessRequest,
        authentication: Authentication
    ): ResponseEntity<BusinessResponse> {
        val business = businessService.createBusiness(request, authentication.name)
        return ResponseEntity(business, HttpStatus.CREATED)
    }

    @GetMapping("/{id}")
    fun getBusiness(@PathVariable id: Long): ResponseEntity<BusinessResponse> {
        return ResponseEntity.ok(businessService.getBusinessById(id))
    }

    @PutMapping("/{id}")
    fun updateBusiness(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateBusinessRequest,
        authentication: Authentication
    ): ResponseEntity<BusinessResponse> {
        return ResponseEntity.ok(businessService.updateBusiness(id, request, authentication.name))
    }

    @DeleteMapping("/{id}")
    fun deleteBusiness(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        businessService.deleteBusiness(id, authentication.name)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/my")
    fun getMyBusinesses(authentication: Authentication): ResponseEntity<List<BusinessResponse>> {
        return ResponseEntity.ok(businessService.getMyBusinesses(authentication.name))
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=BusinessControllerTest -q`
Expected: 4 tests PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessService.kt \
        src/main/kotlin/com/mudhut/nudge/businesses/controllers/BusinessController.kt \
        src/test/kotlin/com/mudhut/nudge/businesses/
git commit -m "feat: add BusinessService, controller, and tests"
```

---

### Task 9: Create BusinessMemberService and Controller

**Files:**
- Create: `SRC/businesses/services/BusinessMemberService.kt`
- Create: `SRC/businesses/controllers/BusinessMemberController.kt`
- Create: `TEST/businesses/controllers/BusinessMemberControllerTest.kt`

**Step 1: Write the failing test**

Create `TEST/businesses/controllers/BusinessMemberControllerTest.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.models.BusinessMemberResponse
import com.mudhut.nudge.businesses.models.UpdateMemberRoleRequest
import com.mudhut.nudge.businesses.services.BusinessMemberService
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class BusinessMemberControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessMemberService: BusinessMemberService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testGetMembers_Success() {
        val members = listOf(
            BusinessMemberResponse(
                id = 1L, userId = 1L, userEmail = "owner@test.com",
                businessId = 1L, role = BusinessRole.OWNER, isActive = true,
                joinedAt = LocalDateTime.now()
            )
        )

        Mockito.`when`(businessMemberService.getMembers(Mockito.eq(1L), Mockito.anyString()))
            .thenReturn(members)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/members"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].role").value("OWNER"))
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testUpdateMemberRole_Success() {
        val request = UpdateMemberRoleRequest(role = BusinessRole.MANAGER)
        val response = BusinessMemberResponse(
            id = 2L, userId = 2L, userEmail = "staff@test.com",
            businessId = 1L, role = BusinessRole.MANAGER, isActive = true,
            joinedAt = LocalDateTime.now()
        )

        Mockito.`when`(businessMemberService.updateMemberRole(
            Mockito.eq(1L), Mockito.eq(2L),
            Mockito.any(UpdateMemberRoleRequest::class.java), Mockito.anyString()
        )).thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/businesses/1/members/2/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.role").value("MANAGER"))
    }

    @Test
    @WithMockUser(username = "member@test.com")
    fun testLeaveBusiness_Success() {
        Mockito.`when`(businessMemberService.leaveBusiness(Mockito.eq(1L), Mockito.anyString()))
            .thenReturn(GeneralRequestResponse("Successfully left the business"))

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/businesses/1/members/me"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Successfully left the business"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=BusinessMemberControllerTest -q`
Expected: FAIL

**Step 3: Create BusinessMemberService**

Create `SRC/businesses/services/BusinessMemberService.kt`:

```kotlin
package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.models.BusinessMemberResponse
import com.mudhut.nudge.businesses.models.UpdateMemberRoleRequest
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class BusinessMemberService(
    private val businessMemberRepository: BusinessMemberRepository,
    private val userRepository: UserRepository,
    private val businessService: BusinessService
) {

    fun getMembers(businessId: Long, userEmail: String): List<BusinessMemberResponse> {
        businessService.requireRole(businessId, userEmail, BusinessRole.MANAGER)
        return businessMemberRepository.findByBusinessIdAndIsActiveTrue(businessId)
            .map { toResponse(it) }
    }

    @Transactional
    fun updateMemberRole(
        businessId: Long,
        memberId: Long,
        request: UpdateMemberRoleRequest,
        userEmail: String
    ): BusinessMemberResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        val actingUser = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val actingMember = businessMemberRepository.findByBusinessIdAndUserId(businessId, actingUser.id!!)
            .orElseThrow { BusinessAccessDeniedException("You are not a member of this business") }

        val targetMember = businessMemberRepository.findById(memberId)
            .orElseThrow { EntityNotFoundException("Member not found with id: $memberId") }

        if (targetMember.business?.id != businessId) {
            throw IllegalArgumentException("Member does not belong to this business")
        }

        if (targetMember.role == BusinessRole.OWNER) {
            throw BusinessAccessDeniedException("Cannot change the owner's role")
        }

        if (request.role == BusinessRole.OWNER) {
            throw BusinessAccessDeniedException("Cannot assign OWNER role")
        }

        if (actingMember.role == BusinessRole.ADMIN && targetMember.role == BusinessRole.ADMIN) {
            throw BusinessAccessDeniedException("An ADMIN cannot change another ADMIN's role")
        }

        targetMember.role = request.role
        val saved = businessMemberRepository.save(targetMember)
        return toResponse(saved)
    }

    @Transactional
    fun removeMember(businessId: Long, memberId: Long, userEmail: String): GeneralRequestResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        val actingUser = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val actingMember = businessMemberRepository.findByBusinessIdAndUserId(businessId, actingUser.id!!)
            .orElseThrow { BusinessAccessDeniedException("You are not a member of this business") }

        val targetMember = businessMemberRepository.findById(memberId)
            .orElseThrow { EntityNotFoundException("Member not found with id: $memberId") }

        if (targetMember.business?.id != businessId) {
            throw IllegalArgumentException("Member does not belong to this business")
        }

        if (targetMember.role == BusinessRole.OWNER) {
            throw BusinessAccessDeniedException("Cannot remove the owner")
        }

        if (actingMember.role == BusinessRole.ADMIN && targetMember.role == BusinessRole.ADMIN) {
            throw BusinessAccessDeniedException("An ADMIN cannot remove another ADMIN")
        }

        targetMember.isActive = false
        businessMemberRepository.save(targetMember)
        return GeneralRequestResponse("Member removed successfully")
    }

    @Transactional
    fun leaveBusiness(businessId: Long, userEmail: String): GeneralRequestResponse {
        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val member = businessMemberRepository.findByBusinessIdAndUserId(businessId, user.id!!)
            .orElseThrow { EntityNotFoundException("You are not a member of this business") }

        if (member.role == BusinessRole.OWNER) {
            throw BusinessAccessDeniedException("Owner cannot leave the business. Transfer ownership or delete the business instead.")
        }

        member.isActive = false
        businessMemberRepository.save(member)
        return GeneralRequestResponse("Successfully left the business")
    }

    fun getUserMemberships(userEmail: String): List<BusinessMemberResponse> {
        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }
        return businessMemberRepository.findByUserIdAndIsActiveTrue(user.id!!)
            .map { toResponse(it) }
    }

    private fun toResponse(member: com.mudhut.nudge.businesses.entities.BusinessMember): BusinessMemberResponse {
        return BusinessMemberResponse(
            id = member.id!!,
            userId = member.user!!.id!!,
            userEmail = member.user!!.email!!,
            businessId = member.business!!.id!!,
            role = member.role!!,
            isActive = member.isActive,
            joinedAt = member.joinedAt
        )
    }
}
```

**Step 4: Create BusinessMemberController**

Create `SRC/businesses/controllers/BusinessMemberController.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.BusinessMemberResponse
import com.mudhut.nudge.businesses.models.UpdateMemberRoleRequest
import com.mudhut.nudge.businesses.services.BusinessMemberService
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/members")
class BusinessMemberController(
    private val memberService: BusinessMemberService
) {

    @GetMapping
    fun getMembers(
        @PathVariable businessId: Long,
        authentication: Authentication
    ): ResponseEntity<List<BusinessMemberResponse>> {
        return ResponseEntity.ok(memberService.getMembers(businessId, authentication.name))
    }

    @PutMapping("/{memberId}/role")
    fun updateMemberRole(
        @PathVariable businessId: Long,
        @PathVariable memberId: Long,
        @Valid @RequestBody request: UpdateMemberRoleRequest,
        authentication: Authentication
    ): ResponseEntity<BusinessMemberResponse> {
        return ResponseEntity.ok(
            memberService.updateMemberRole(businessId, memberId, request, authentication.name)
        )
    }

    @DeleteMapping("/{memberId}")
    fun removeMember(
        @PathVariable businessId: Long,
        @PathVariable memberId: Long,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(memberService.removeMember(businessId, memberId, authentication.name))
    }

    @DeleteMapping("/me")
    fun leaveBusiness(
        @PathVariable businessId: Long,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(memberService.leaveBusiness(businessId, authentication.name))
    }
}
```

Also create a controller for the user-facing membership endpoint. Create `SRC/businesses/controllers/UserMembershipController.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.BusinessMemberResponse
import com.mudhut.nudge.businesses.services.BusinessMemberService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me/businesses")
class UserMembershipController(
    private val memberService: BusinessMemberService
) {

    @GetMapping
    fun getMyMemberships(authentication: Authentication): ResponseEntity<List<BusinessMemberResponse>> {
        return ResponseEntity.ok(memberService.getUserMemberships(authentication.name))
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=BusinessMemberControllerTest -q`
Expected: 3 tests PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessMemberService.kt \
        src/main/kotlin/com/mudhut/nudge/businesses/controllers/BusinessMemberController.kt \
        src/main/kotlin/com/mudhut/nudge/businesses/controllers/UserMembershipController.kt \
        src/test/kotlin/com/mudhut/nudge/businesses/
git commit -m "feat: add BusinessMember service, controllers, and tests"
```

---

### Task 10: Create BusinessInvitationService and Controller

**Files:**
- Create: `SRC/businesses/services/BusinessInvitationService.kt`
- Create: `SRC/businesses/controllers/BusinessInvitationController.kt`
- Create: `TEST/businesses/controllers/BusinessInvitationControllerTest.kt`

**Step 1: Write the failing test**

Create `TEST/businesses/controllers/BusinessInvitationControllerTest.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.InvitationStatus
import com.mudhut.nudge.businesses.models.InvitationResponse
import com.mudhut.nudge.businesses.models.InviteMemberRequest
import com.mudhut.nudge.businesses.services.BusinessInvitationService
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class BusinessInvitationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var invitationService: BusinessInvitationService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testSendInvitation_Success() {
        val request = InviteMemberRequest(
            email = "newuser@test.com",
            role = BusinessRole.STAFF
        )

        val response = InvitationResponse(
            id = 1L, businessId = 1L, businessName = "Test Biz",
            inviterEmail = "admin@test.com", inviteeEmail = "newuser@test.com",
            role = BusinessRole.STAFF, status = InvitationStatus.PENDING,
            expiryDate = LocalDateTime.now().plusDays(7), createdAt = LocalDateTime.now()
        )

        Mockito.`when`(invitationService.sendInvitation(
            Mockito.eq(1L),
            Mockito.any(InviteMemberRequest::class.java),
            Mockito.anyString()
        )).thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.inviteeEmail").value("newuser@test.com"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PENDING"))
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testSendInvitation_OwnerRoleRejected() {
        val request = InviteMemberRequest(
            email = "newuser@test.com",
            role = BusinessRole.OWNER
        )

        Mockito.`when`(invitationService.sendInvitation(
            Mockito.eq(1L),
            Mockito.any(InviteMemberRequest::class.java),
            Mockito.anyString()
        )).thenThrow(IllegalArgumentException("Cannot invite with OWNER role"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "invitee@test.com")
    fun testAcceptInvitation_Success() {
        Mockito.`when`(invitationService.acceptInvitation(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(GeneralRequestResponse("Invitation accepted"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/invitations/some-token/accept")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Invitation accepted"))
    }

    @Test
    @WithMockUser(username = "invitee@test.com")
    fun testDeclineInvitation_Success() {
        Mockito.`when`(invitationService.declineInvitation(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(GeneralRequestResponse("Invitation declined"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/invitations/some-token/decline")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Invitation declined"))
    }

    @Test
    @WithMockUser(username = "user@test.com")
    fun testGetMyInvitations_Success() {
        val invitations = listOf(
            InvitationResponse(
                id = 1L, businessId = 1L, businessName = "Test Biz",
                inviterEmail = "admin@test.com", inviteeEmail = "user@test.com",
                role = BusinessRole.STAFF, status = InvitationStatus.PENDING,
                expiryDate = LocalDateTime.now().plusDays(7), createdAt = LocalDateTime.now()
            )
        )

        Mockito.`when`(invitationService.getMyInvitations(Mockito.anyString()))
            .thenReturn(invitations)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/invitations/my"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=BusinessInvitationControllerTest -q`
Expected: FAIL

**Step 3: Create BusinessInvitationService**

Create `SRC/businesses/services/BusinessInvitationService.kt`:

```kotlin
package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessInvitation
import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.InvitationStatus
import com.mudhut.nudge.businesses.models.InvitationResponse
import com.mudhut.nudge.businesses.models.InviteMemberRequest
import com.mudhut.nudge.businesses.repositories.BusinessInvitationRepository
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.UrlService
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.InvitationException
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class BusinessInvitationService(
    private val invitationRepository: BusinessInvitationRepository,
    private val businessRepository: BusinessRepository,
    private val businessMemberRepository: BusinessMemberRepository,
    private val userRepository: UserRepository,
    private val businessService: BusinessService,
    private val emailService: IEmailService,
    private val urlService: UrlService
) {

    @Transactional
    fun sendInvitation(
        businessId: Long,
        request: InviteMemberRequest,
        inviterEmail: String
    ): InvitationResponse {
        businessService.requireRole(businessId, inviterEmail, BusinessRole.ADMIN)

        if (request.role == BusinessRole.OWNER) {
            throw IllegalArgumentException("Cannot invite with OWNER role")
        }

        val business = businessRepository.findById(businessId)
            .orElseThrow { BusinessNotFoundException("Business not found") }

        val inviter = userRepository.findByEmail(inviterEmail)
            .orElseThrow { EntityNotFoundException("Inviter not found") }

        if (businessMemberRepository.existsByBusinessIdAndUserId(businessId,
            userRepository.findByEmail(request.email!!)
                .map { it.id!! }
                .orElse(-1L)
        )) {
            throw InvitationException("User is already a member of this business")
        }

        if (invitationRepository.existsByBusinessIdAndEmailAndStatus(
                businessId, request.email!!, InvitationStatus.PENDING
        )) {
            throw InvitationException("A pending invitation already exists for this email")
        }

        val invitee = userRepository.findByEmail(request.email!!).orElse(null)

        val invitation = BusinessInvitation().apply {
            this.business = business
            this.inviter = inviter
            this.invitee = invitee
            email = request.email
            role = request.role
            token = UUID.randomUUID().toString()
            expiryDate = LocalDateTime.now().plusDays(BusinessInvitation.EXPIRY_DAYS)
        }

        val saved = invitationRepository.save(invitation)

        sendInvitationEmail(saved, business.name!!)

        return toResponse(saved)
    }

    fun getPendingInvitations(businessId: Long, userEmail: String): List<InvitationResponse> {
        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)
        return invitationRepository.findByBusinessIdAndStatus(businessId, InvitationStatus.PENDING)
            .map { toResponse(it) }
    }

    @Transactional
    fun acceptInvitation(token: String, userEmail: String): GeneralRequestResponse {
        val invitation = invitationRepository.findByToken(token)
            .orElseThrow { InvitationException("Invitation not found") }

        if (invitation.status != InvitationStatus.PENDING) {
            throw InvitationException("Invitation is no longer pending")
        }

        if (invitation.isExpired()) {
            invitation.status = InvitationStatus.EXPIRED
            invitationRepository.save(invitation)
            throw InvitationException("Invitation has expired")
        }

        if (invitation.email != userEmail) {
            throw InvitationException("This invitation is not for your email address")
        }

        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val member = BusinessMember().apply {
            this.user = user
            business = invitation.business
            role = invitation.role
        }
        businessMemberRepository.save(member)

        invitation.status = InvitationStatus.ACCEPTED
        invitation.invitee = user
        invitationRepository.save(invitation)

        return GeneralRequestResponse("Invitation accepted")
    }

    @Transactional
    fun declineInvitation(token: String, userEmail: String): GeneralRequestResponse {
        val invitation = invitationRepository.findByToken(token)
            .orElseThrow { InvitationException("Invitation not found") }

        if (invitation.status != InvitationStatus.PENDING) {
            throw InvitationException("Invitation is no longer pending")
        }

        if (invitation.email != userEmail) {
            throw InvitationException("This invitation is not for your email address")
        }

        invitation.status = InvitationStatus.DECLINED
        invitationRepository.save(invitation)

        return GeneralRequestResponse("Invitation declined")
    }

    @Transactional
    fun cancelInvitation(businessId: Long, invitationId: Long, userEmail: String): GeneralRequestResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        val invitation = invitationRepository.findById(invitationId)
            .orElseThrow { InvitationException("Invitation not found") }

        if (invitation.business?.id != businessId) {
            throw IllegalArgumentException("Invitation does not belong to this business")
        }

        if (invitation.status != InvitationStatus.PENDING) {
            throw InvitationException("Can only cancel pending invitations")
        }

        invitationRepository.delete(invitation)
        return GeneralRequestResponse("Invitation cancelled")
    }

    fun getMyInvitations(userEmail: String): List<InvitationResponse> {
        return invitationRepository.findByEmailAndStatus(userEmail, InvitationStatus.PENDING)
            .filter { !it.isExpired() }
            .map { toResponse(it) }
    }

    fun resolveInvitationsForNewUser(userEmail: String) {
        val user = userRepository.findByEmail(userEmail).orElse(null) ?: return
        val pendingInvitations = invitationRepository.findByEmailAndStatus(userEmail, InvitationStatus.PENDING)
        pendingInvitations.forEach { invitation ->
            invitation.invitee = user
            invitationRepository.save(invitation)
        }
    }

    private fun sendInvitationEmail(invitation: BusinessInvitation, businessName: String) {
        val inviteUrl = urlService.buildUrlWithParam(
            "/api/v1/invitations/${invitation.token}/accept",
            "token", invitation.token!!
        )
        val subject = "You've been invited to join $businessName"
        val body = """
            You have been invited to join $businessName as ${invitation.role?.name}.

            Click the link below to accept the invitation:
            $inviteUrl

            This invitation expires on ${invitation.expiryDate}.
        """.trimIndent()
        emailService.sendEmail(invitation.email!!, subject, body)
    }

    private fun toResponse(invitation: BusinessInvitation): InvitationResponse {
        return InvitationResponse(
            id = invitation.id!!,
            businessId = invitation.business!!.id!!,
            businessName = invitation.business!!.name!!,
            inviterEmail = invitation.inviter!!.email!!,
            inviteeEmail = invitation.email!!,
            role = invitation.role!!,
            status = invitation.status,
            expiryDate = invitation.expiryDate,
            createdAt = invitation.createdAt
        )
    }
}
```

**Step 4: Create BusinessInvitationController**

Create `SRC/businesses/controllers/BusinessInvitationController.kt`:

```kotlin
package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.InvitationResponse
import com.mudhut.nudge.businesses.models.InviteMemberRequest
import com.mudhut.nudge.businesses.services.BusinessInvitationService
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class BusinessInvitationController(
    private val invitationService: BusinessInvitationService
) {

    @PostMapping("/api/v1/businesses/{businessId}/invitations")
    fun sendInvitation(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: InviteMemberRequest,
        authentication: Authentication
    ): ResponseEntity<InvitationResponse> {
        val invitation = invitationService.sendInvitation(businessId, request, authentication.name)
        return ResponseEntity(invitation, HttpStatus.CREATED)
    }

    @GetMapping("/api/v1/businesses/{businessId}/invitations")
    fun getPendingInvitations(
        @PathVariable businessId: Long,
        authentication: Authentication
    ): ResponseEntity<List<InvitationResponse>> {
        return ResponseEntity.ok(invitationService.getPendingInvitations(businessId, authentication.name))
    }

    @DeleteMapping("/api/v1/businesses/{businessId}/invitations/{invitationId}")
    fun cancelInvitation(
        @PathVariable businessId: Long,
        @PathVariable invitationId: Long,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(
            invitationService.cancelInvitation(businessId, invitationId, authentication.name)
        )
    }

    @PostMapping("/api/v1/invitations/{token}/accept")
    fun acceptInvitation(
        @PathVariable token: String,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(invitationService.acceptInvitation(token, authentication.name))
    }

    @PostMapping("/api/v1/invitations/{token}/decline")
    fun declineInvitation(
        @PathVariable token: String,
        authentication: Authentication
    ): ResponseEntity<GeneralRequestResponse> {
        return ResponseEntity.ok(invitationService.declineInvitation(token, authentication.name))
    }

    @GetMapping("/api/v1/invitations/my")
    fun getMyInvitations(
        authentication: Authentication
    ): ResponseEntity<List<InvitationResponse>> {
        return ResponseEntity.ok(invitationService.getMyInvitations(authentication.name))
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=BusinessInvitationControllerTest -q`
Expected: 5 tests PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessInvitationService.kt \
        src/main/kotlin/com/mudhut/nudge/businesses/controllers/BusinessInvitationController.kt \
        src/test/kotlin/com/mudhut/nudge/businesses/
git commit -m "feat: add BusinessInvitation service, controller, and tests"
```

---

### Task 11: Hook Invitation Resolution into Registration Flow

**Files:**
- Modify: `SRC/users/services/RegistrationService.kt`

**Step 1: Update RegistrationService to inject BusinessInvitationService and resolve invitations**

In `SRC/users/services/RegistrationService.kt`, add the injection and call:

Add to constructor:
```kotlin
private val businessInvitationService: com.mudhut.nudge.businesses.services.BusinessInvitationService
```

After `val savedUser = userRepository.save(newUser)`, before the verification token logic, add:
```kotlin
businessInvitationService.resolveInvitationsForNewUser(savedUser.email!!)
```

The full `createUser` try block becomes:
```kotlin
try {
    val savedUser = userRepository.save(newUser)
    businessInvitationService.resolveInvitationsForNewUser(savedUser.email!!)
    val token = verificationService.createVerificationToken(newUser)
    verificationService.sendVerificationEmail(savedUser, token)
    return savedUser
} catch (e: Exception) {
    throw RuntimeException("Error occurred while creating user", e)
}
```

**Step 2: Verify the app compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

**Step 3: Run all tests**

Run: `./mvnw test -q`
Expected: All tests PASS

**Step 4: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/users/services/RegistrationService.kt
git commit -m "feat: resolve pending invitations on user registration"
```

---

### Task 12: Run Full Test Suite and Verify

**Step 1: Run all tests**

Run: `./mvnw test -q`
Expected: All tests PASS

**Step 2: Verify the app starts**

Run: `./mvnw spring-boot:run` (ensure DB is running)
Expected: Application starts without errors

**Step 3: Final commit if any fixes were needed**

If any adjustments were required, commit them:
```bash
git add -A
git commit -m "fix: resolve issues found during full test run"
```
