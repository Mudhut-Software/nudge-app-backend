# Business Multiple Phone Numbers Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow businesses to have multiple phone numbers (max 5) with bulk and individual management endpoints.

**Architecture:** New `BusinessPhoneNumber` entity with `@OneToMany` on `Business`. Replace single `phone` field across entity/DTOs. Add a `BusinessPhoneNumberService` for dedicated add/remove logic. Add dedicated endpoints on `BusinessController`.

**Tech Stack:** Spring Boot 3.4.2, Kotlin, Spring Data JPA, Jakarta Validation, MockMvc tests

---

### Task 1: Create BusinessPhoneNumber entity and repository

**Files:**
- Create: `src/main/kotlin/com/mudhut/nudge/businesses/entities/BusinessPhoneNumber.kt`
- Create: `src/main/kotlin/com/mudhut/nudge/businesses/repositories/BusinessPhoneNumberRepository.kt`

**Step 1: Create the entity**

```kotlin
package com.mudhut.nudge.businesses.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "business_phone_numbers",
    uniqueConstraints = [UniqueConstraint(columnNames = ["business_id", "phone_number"])]
)
class BusinessPhoneNumber(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @field:NotBlank
    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @CreationTimestamp
    var createdAt: LocalDateTime? = null
)
```

**Step 2: Create the repository**

```kotlin
package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessPhoneNumberRepository : JpaRepository<BusinessPhoneNumber, Long> {
    fun findByBusinessId(businessId: Long): List<BusinessPhoneNumber>
    fun countByBusinessId(businessId: Long): Long
    fun existsByBusinessIdAndPhoneNumber(businessId: Long, phoneNumber: String): Boolean
}
```

**Step 3: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/entities/BusinessPhoneNumber.kt src/main/kotlin/com/mudhut/nudge/businesses/repositories/BusinessPhoneNumberRepository.kt
git commit -m "feat: add BusinessPhoneNumber entity and repository"
```

---

### Task 2: Update Business entity — replace phone with phoneNumbers

**Files:**
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/entities/Business.kt`

**Step 1: Replace phone field**

In `Business.kt`, remove:
```kotlin
var phone: String? = null,
```

And add in its place:
```kotlin
@OneToMany(mappedBy = "business", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
var phoneNumbers: MutableList<BusinessPhoneNumber> = mutableListOf(),
```

Also add import at top:
```kotlin
import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
```

Note: `CascadeType.ALL` + `orphanRemoval = true` means phone numbers are saved/deleted when the business is saved, and removed phone numbers are deleted from DB.

**Step 2: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/entities/Business.kt
git commit -m "feat: replace phone field with phoneNumbers relationship on Business"
```

---

### Task 3: Update DTOs — CreateBusinessRequest, UpdateBusinessRequest, BusinessResponse

**Files:**
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/models/CreateBusinessRequest.kt`
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/models/UpdateBusinessRequest.kt`
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/models/BusinessResponse.kt`

**Step 1: Update CreateBusinessRequest**

Replace `var phone: String? = null,` with:
```kotlin
var phoneNumbers: List<String>? = null,
```

**Step 2: Update UpdateBusinessRequest**

Replace `var phone: String? = null,` with:
```kotlin
var phoneNumbers: List<String>? = null,
```

**Step 3: Update BusinessResponse**

Replace `val phone: String?,` with:
```kotlin
val phoneNumbers: List<String>,
```

**Step 4: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/models/CreateBusinessRequest.kt src/main/kotlin/com/mudhut/nudge/businesses/models/UpdateBusinessRequest.kt src/main/kotlin/com/mudhut/nudge/businesses/models/BusinessResponse.kt
git commit -m "feat: update business DTOs to use phoneNumbers list"
```

---

### Task 4: Update BusinessService — handle phone numbers in create, update, and toResponse

**Files:**
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessService.kt`

**Step 1: Update imports**

Add:
```kotlin
import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.repositories.BusinessPhoneNumberRepository
```

**Step 2: Add BusinessPhoneNumberRepository to constructor**

Change constructor to:
```kotlin
class BusinessService(
    private val businessRepository: BusinessRepository,
    private val businessCategoryRepository: BusinessCategoryRepository,
    private val businessMemberRepository: BusinessMemberRepository,
    private val userRepository: UserRepository,
    private val businessPhoneNumberRepository: BusinessPhoneNumberRepository
) {
```

**Step 3: Update createBusiness method**

Replace `phone = request.phone` in the `Business().apply` block with nothing (remove the line).

After `val savedBusiness = businessRepository.save(business)`, before the owner membership creation, add:
```kotlin
request.phoneNumbers?.let { numbers ->
    if (numbers.size > 5) {
        throw IllegalArgumentException("A business can have at most 5 phone numbers")
    }
    val phoneEntities = numbers.map { number ->
        BusinessPhoneNumber().apply {
            phoneNumber = number
            this.business = savedBusiness
        }
    }
    businessPhoneNumberRepository.saveAll(phoneEntities)
    savedBusiness.phoneNumbers.addAll(phoneEntities)
}
```

**Step 4: Update updateBusiness method**

Replace `request.phone?.let { business.phone = it }` with:
```kotlin
request.phoneNumbers?.let { numbers ->
    if (numbers.size > 5) {
        throw IllegalArgumentException("A business can have at most 5 phone numbers")
    }
    business.phoneNumbers.clear()
    businessRepository.flush()
    val phoneEntities = numbers.map { number ->
        BusinessPhoneNumber().apply {
            phoneNumber = number
            this.business = business
        }
    }
    business.phoneNumbers.addAll(phoneEntities)
}
```

**Step 5: Update toResponse method**

Replace `phone = business.phone,` with:
```kotlin
phoneNumbers = business.phoneNumbers.map { it.phoneNumber!! },
```

**Step 6: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessService.kt
git commit -m "feat: handle phone numbers in BusinessService create/update/toResponse"
```

---

### Task 5: Create BusinessPhoneNumberService with tests

**Files:**
- Create: `src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessPhoneNumberService.kt`
- Create: `src/test/kotlin/com/mudhut/nudge/businesses/services/BusinessPhoneNumberServiceTest.kt`

**Step 1: Write tests**

```kotlin
package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.repositories.BusinessPhoneNumberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class BusinessPhoneNumberServiceTest {

    @Mock
    private lateinit var businessPhoneNumberRepository: BusinessPhoneNumberRepository

    @Mock
    private lateinit var businessRepository: BusinessRepository

    @Mock
    private lateinit var businessService: BusinessService

    @InjectMocks
    private lateinit var phoneNumberService: BusinessPhoneNumberService

    @Test
    fun `addPhoneNumber adds number successfully`() {
        val business = Business(id = 1L, name = "Test Biz")
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(business))
        `when`(businessPhoneNumberRepository.countByBusinessId(1L)).thenReturn(2L)
        `when`(businessPhoneNumberRepository.existsByBusinessIdAndPhoneNumber(1L, "+256700000000")).thenReturn(false)
        `when`(businessPhoneNumberRepository.save(any())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as BusinessPhoneNumber
            entity.id = 10L
            entity
        }

        val result = phoneNumberService.addPhoneNumber(1L, "+256700000000", "admin@test.com")

        assertEquals("+256700000000", result.phoneNumber)
        verify(businessService).requireRole(1L, "admin@test.com", com.mudhut.nudge.businesses.entities.BusinessRole.ADMIN)
    }

    @Test
    fun `addPhoneNumber throws when max reached`() {
        val business = Business(id = 1L, name = "Test Biz")
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(business))
        `when`(businessPhoneNumberRepository.countByBusinessId(1L)).thenReturn(5L)

        assertThrows<IllegalArgumentException> {
            phoneNumberService.addPhoneNumber(1L, "+256700000000", "admin@test.com")
        }
    }

    @Test
    fun `addPhoneNumber throws when duplicate`() {
        val business = Business(id = 1L, name = "Test Biz")
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(business))
        `when`(businessPhoneNumberRepository.countByBusinessId(1L)).thenReturn(2L)
        `when`(businessPhoneNumberRepository.existsByBusinessIdAndPhoneNumber(1L, "+256700000000")).thenReturn(true)

        assertThrows<IllegalArgumentException> {
            phoneNumberService.addPhoneNumber(1L, "+256700000000", "admin@test.com")
        }
    }

    @Test
    fun `addPhoneNumber throws when business not found`() {
        `when`(businessRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<BusinessNotFoundException> {
            phoneNumberService.addPhoneNumber(99L, "+256700000000", "admin@test.com")
        }
    }

    @Test
    fun `removePhoneNumber removes successfully`() {
        val business = Business(id = 1L, name = "Test Biz")
        val phoneNumber = BusinessPhoneNumber(id = 10L, phoneNumber = "+256700000000", business = business)
        `when`(businessPhoneNumberRepository.findById(10L)).thenReturn(Optional.of(phoneNumber))

        phoneNumberService.removePhoneNumber(1L, 10L, "admin@test.com")

        verify(businessService).requireRole(1L, "admin@test.com", com.mudhut.nudge.businesses.entities.BusinessRole.ADMIN)
        verify(businessPhoneNumberRepository).delete(phoneNumber)
    }

    @Test
    fun `removePhoneNumber throws when phone number not found`() {
        `when`(businessPhoneNumberRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<IllegalArgumentException> {
            phoneNumberService.removePhoneNumber(1L, 99L, "admin@test.com")
        }
    }

    @Test
    fun `removePhoneNumber throws when phone number belongs to different business`() {
        val otherBusiness = Business(id = 2L, name = "Other Biz")
        val phoneNumber = BusinessPhoneNumber(id = 10L, phoneNumber = "+256700000000", business = otherBusiness)
        `when`(businessPhoneNumberRepository.findById(10L)).thenReturn(Optional.of(phoneNumber))

        assertThrows<IllegalArgumentException> {
            phoneNumberService.removePhoneNumber(1L, 10L, "admin@test.com")
        }
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=BusinessPhoneNumberServiceTest -Dsurefire.failIfNoTests=false`
Expected: Compilation error (service doesn't exist yet)

**Step 3: Create the service**

```kotlin
package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessPhoneNumberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BusinessPhoneNumberService(
    private val businessPhoneNumberRepository: BusinessPhoneNumberRepository,
    private val businessRepository: BusinessRepository,
    private val businessService: BusinessService
) {

    @Transactional
    fun addPhoneNumber(businessId: Long, phoneNumber: String, userEmail: String): BusinessPhoneNumber {
        val business = businessRepository.findById(businessId)
            .orElseThrow { BusinessNotFoundException("Business not found with id: $businessId") }

        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        if (businessPhoneNumberRepository.countByBusinessId(businessId) >= 5) {
            throw IllegalArgumentException("A business can have at most 5 phone numbers")
        }

        if (businessPhoneNumberRepository.existsByBusinessIdAndPhoneNumber(businessId, phoneNumber)) {
            throw IllegalArgumentException("Phone number '$phoneNumber' already exists for this business")
        }

        val entity = BusinessPhoneNumber().apply {
            this.phoneNumber = phoneNumber
            this.business = business
        }

        return businessPhoneNumberRepository.save(entity)
    }

    @Transactional
    fun removePhoneNumber(businessId: Long, phoneNumberId: Long, userEmail: String) {
        val phoneNumber = businessPhoneNumberRepository.findById(phoneNumberId)
            .orElseThrow { IllegalArgumentException("Phone number not found with id: $phoneNumberId") }

        if (phoneNumber.business?.id != businessId) {
            throw IllegalArgumentException("Phone number does not belong to this business")
        }

        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        businessPhoneNumberRepository.delete(phoneNumber)
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=BusinessPhoneNumberServiceTest`
Expected: All 7 tests PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessPhoneNumberService.kt src/test/kotlin/com/mudhut/nudge/businesses/services/BusinessPhoneNumberServiceTest.kt
git commit -m "feat: add BusinessPhoneNumberService with add/remove and tests"
```

---

### Task 6: Add dedicated phone number endpoints to BusinessController

**Files:**
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/controllers/BusinessController.kt`
- Create: `src/main/kotlin/com/mudhut/nudge/businesses/models/AddPhoneNumberRequest.kt`

**Step 1: Create AddPhoneNumberRequest DTO**

```kotlin
package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.NotBlank

data class AddPhoneNumberRequest(
    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String? = null
)
```

**Step 2: Create PhoneNumberResponse DTO**

```kotlin
package com.mudhut.nudge.businesses.models

data class PhoneNumberResponse(
    val id: Long,
    val phoneNumber: String
)
```

**Step 3: Add endpoints to BusinessController**

Add imports:
```kotlin
import com.mudhut.nudge.businesses.models.AddPhoneNumberRequest
import com.mudhut.nudge.businesses.models.PhoneNumberResponse
import com.mudhut.nudge.businesses.services.BusinessPhoneNumberService
```

Add `businessPhoneNumberService` to constructor:
```kotlin
class BusinessController(
    private val businessService: BusinessService,
    private val businessPhoneNumberService: BusinessPhoneNumberService
) {
```

Add these endpoints:
```kotlin
@PostMapping("/{id}/phone-numbers")
fun addPhoneNumber(
    @PathVariable id: Long,
    @Valid @RequestBody request: AddPhoneNumberRequest,
    authentication: Authentication
): ResponseEntity<PhoneNumberResponse> {
    val saved = businessPhoneNumberService.addPhoneNumber(id, request.phoneNumber!!, authentication.name)
    return ResponseEntity(PhoneNumberResponse(saved.id!!, saved.phoneNumber!!), HttpStatus.CREATED)
}

@DeleteMapping("/{id}/phone-numbers/{phoneNumberId}")
fun removePhoneNumber(
    @PathVariable id: Long,
    @PathVariable phoneNumberId: Long,
    authentication: Authentication
): ResponseEntity<Void> {
    businessPhoneNumberService.removePhoneNumber(id, phoneNumberId, authentication.name)
    return ResponseEntity.noContent().build()
}
```

**Step 4: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/controllers/BusinessController.kt src/main/kotlin/com/mudhut/nudge/businesses/models/AddPhoneNumberRequest.kt src/main/kotlin/com/mudhut/nudge/businesses/models/PhoneNumberResponse.kt
git commit -m "feat: add dedicated phone number add/remove endpoints"
```

---

### Task 7: Update existing tests to use phoneNumbers instead of phone

**Files:**
- Modify: `src/test/kotlin/com/mudhut/nudge/businesses/controllers/BusinessControllerTest.kt`

**Step 1: Update all BusinessResponse references**

In all `BusinessResponse(...)` constructors in the test file, replace `phone = null,` with `phoneNumbers = emptyList(),` and `phone = "...",` with `phoneNumbers = listOf("..."),`.

There are 4 instances in the file (lines ~72, ~100, ~124, ~128 area). All should change from `phone = null,` to `phoneNumbers = emptyList(),`.

**Step 2: Run tests to verify they pass**

Run: `./mvnw test -Dtest=BusinessControllerTest`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add src/test/kotlin/com/mudhut/nudge/businesses/controllers/BusinessControllerTest.kt
git commit -m "test: update BusinessControllerTest to use phoneNumbers"
```

---

### Task 8: Add controller tests for phone number endpoints

**Files:**
- Modify: `src/test/kotlin/com/mudhut/nudge/businesses/controllers/BusinessControllerTest.kt`

**Step 1: Add imports and mock bean**

Add imports:
```kotlin
import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.models.AddPhoneNumberRequest
import com.mudhut.nudge.businesses.services.BusinessPhoneNumberService
```

Add mock bean:
```kotlin
@MockitoBean
private lateinit var businessPhoneNumberService: BusinessPhoneNumberService
```

**Step 2: Add test methods**

```kotlin
@Test
@WithMockUser(username = "admin@test.com")
fun testAddPhoneNumber_Success() {
    val request = AddPhoneNumberRequest(phoneNumber = "+256700000000")
    val business = Business(id = 1L, name = "Test Biz")
    val saved = BusinessPhoneNumber(id = 10L, phoneNumber = "+256700000000", business = business)

    Mockito.`when`(businessPhoneNumberService.addPhoneNumber(
        Mockito.eq(1L), Mockito.eq("+256700000000"), Mockito.anyString()
    )).thenReturn(saved)

    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v1/businesses/1/phone-numbers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
    )
        .andExpect(MockMvcResultMatchers.status().isCreated)
        .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(10))
        .andExpect(MockMvcResultMatchers.jsonPath("$.phoneNumber").value("+256700000000"))
}

@Test
@WithMockUser(username = "admin@test.com")
fun testRemovePhoneNumber_Success() {
    Mockito.doNothing().`when`(businessPhoneNumberService)
        .removePhoneNumber(Mockito.eq(1L), Mockito.eq(10L), Mockito.anyString())

    mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/businesses/1/phone-numbers/10"))
        .andExpect(MockMvcResultMatchers.status().isNoContent)
}

@Test
fun testAddPhoneNumber_Unauthenticated() {
    val request = AddPhoneNumberRequest(phoneNumber = "+256700000000")

    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v1/businesses/1/phone-numbers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
    )
        .andExpect(MockMvcResultMatchers.status().isForbidden)
}
```

**Step 3: Run tests to verify they pass**

Run: `./mvnw test -Dtest=BusinessControllerTest`
Expected: All tests PASS

**Step 4: Commit**

```bash
git add src/test/kotlin/com/mudhut/nudge/businesses/controllers/BusinessControllerTest.kt
git commit -m "test: add controller tests for phone number add/remove endpoints"
```

---

### Task 9: Run full test suite

**Step 1: Run all tests**

Run: `./mvnw test`
Expected: All tests PASS

**Step 2: Fix any failures if needed**

Check that all existing tests that reference `phone` on `BusinessResponse` have been updated to `phoneNumbers`.
