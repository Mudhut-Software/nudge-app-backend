# Category CRUD Endpoints Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add get-by-ID, update, and soft-delete endpoints for categories/subcategories.

**Architecture:** Extend existing `BusinessCategoryController`, `BusinessCategoryService`, and `BusinessCategoryRepository`. Add a `CategoryNotFoundException`, an `UpdateCategoryRequest` DTO, and update `SecurityConfig` for role-based access on PUT/DELETE. All changes follow the existing layered patterns.

**Tech Stack:** Spring Boot 3.4.2, Kotlin, Spring Security, Spring Data JPA, Jakarta Validation, MockMvc tests

---

### Task 1: Add CategoryNotFoundException

**Files:**
- Create: `src/main/kotlin/com/mudhut/nudge/utils/exceptions/CategoryNotFoundException.kt`
- Modify: `src/main/kotlin/com/mudhut/nudge/utils/exceptions/GlobalExceptionHandler.kt`

**Step 1: Create the exception class**

```kotlin
package com.mudhut.nudge.utils.exceptions

class CategoryNotFoundException(message: String) : RuntimeException(message)
```

**Step 2: Add handler in GlobalExceptionHandler**

Add this method to `GlobalExceptionHandler` (after the `handleBusinessNotFoundException` method around line 178):

```kotlin
@ExceptionHandler(CategoryNotFoundException::class)
fun handleCategoryNotFoundException(ex: CategoryNotFoundException): ResponseEntity<ErrorResponse> {
    logger.warn("Category not found: {}", ex.message)
    return ResponseEntity(
        ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "Category not found"),
        HttpStatus.NOT_FOUND
    )
}
```

**Step 3: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/utils/exceptions/CategoryNotFoundException.kt src/main/kotlin/com/mudhut/nudge/utils/exceptions/GlobalExceptionHandler.kt
git commit -m "feat: add CategoryNotFoundException with global handler"
```

---

### Task 2: Add UpdateCategoryRequest DTO

**Files:**
- Create: `src/main/kotlin/com/mudhut/nudge/businesses/models/UpdateCategoryRequest.kt`

**Step 1: Create the DTO**

```kotlin
package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.Size

data class UpdateCategoryRequest(
    @field:Size(min = 1, message = "Category name must not be blank")
    val name: String? = null,

    val description: String? = null,

    val parentId: Long? = null,

    val isActive: Boolean? = null
)
```

Note: We use `@Size(min = 1)` instead of `@NotBlank` because the field is optional (null means "don't change"), but if provided it must not be empty.

**Step 2: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/models/UpdateCategoryRequest.kt
git commit -m "feat: add UpdateCategoryRequest DTO"
```

---

### Task 3: Add repository method

**Files:**
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/repositories/BusinessCategoryRepository.kt`

**Step 1: Add existsByNameAndIdNot method**

Add this method to the `BusinessCategoryRepository` interface (used to check name uniqueness excluding the current category):

```kotlin
fun existsByNameAndIdNot(name: String, id: Long): Boolean
```

The full file should be:

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
    fun existsByNameAndIdNot(name: String, id: Long): Boolean
}
```

**Step 2: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/repositories/BusinessCategoryRepository.kt
git commit -m "feat: add existsByNameAndIdNot to BusinessCategoryRepository"
```

---

### Task 4: Add service methods (getCategoryById, updateCategory, deleteCategory)

**Files:**
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessCategoryService.kt`

**Step 1: Write tests for getCategoryById**

**Test file:** `src/test/kotlin/com/mudhut/nudge/businesses/services/BusinessCategoryServiceTest.kt`

```kotlin
package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.models.UpdateCategoryRequest
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.utils.exceptions.CategoryNotFoundException
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
class BusinessCategoryServiceTest {

    @Mock
    private lateinit var categoryRepository: BusinessCategoryRepository

    @InjectMocks
    private lateinit var categoryService: BusinessCategoryService

    @Test
    fun `getCategoryById returns category when found`() {
        val category = BusinessCategory(id = 1L, name = "Healthcare", description = "Health services")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))

        val result = categoryService.getCategoryById(1L)

        assertEquals(1L, result.id)
        assertEquals("Healthcare", result.name)
    }

    @Test
    fun `getCategoryById throws CategoryNotFoundException when not found`() {
        `when`(categoryRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<CategoryNotFoundException> {
            categoryService.getCategoryById(99L)
        }
    }

    @Test
    fun `updateCategory updates name and description`() {
        val category = BusinessCategory(id = 1L, name = "Old Name", description = "Old desc")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))
        `when`(categoryRepository.existsByNameAndIdNot("New Name", 1L)).thenReturn(false)
        `when`(categoryRepository.save(any())).thenAnswer { it.arguments[0] }

        val request = UpdateCategoryRequest(name = "New Name", description = "New desc")
        val result = categoryService.updateCategory(1L, request)

        assertEquals("New Name", result.name)
        assertEquals("New desc", result.description)
    }

    @Test
    fun `updateCategory throws when new name already exists`() {
        val category = BusinessCategory(id = 1L, name = "Old Name")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))
        `when`(categoryRepository.existsByNameAndIdNot("Taken Name", 1L)).thenReturn(true)

        val request = UpdateCategoryRequest(name = "Taken Name")

        assertThrows<IllegalArgumentException> {
            categoryService.updateCategory(1L, request)
        }
    }

    @Test
    fun `updateCategory prevents setting parent to self`() {
        val category = BusinessCategory(id = 1L, name = "Cat")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))

        val request = UpdateCategoryRequest(parentId = 1L)

        assertThrows<IllegalArgumentException> {
            categoryService.updateCategory(1L, request)
        }
    }

    @Test
    fun `updateCategory prevents circular reference`() {
        val parent = BusinessCategory(id = 1L, name = "Parent")
        val child = BusinessCategory(id = 2L, name = "Child", parent = parent)
        parent.children = mutableListOf(child)

        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(parent))
        `when`(categoryRepository.findById(2L)).thenReturn(Optional.of(child))

        val request = UpdateCategoryRequest(parentId = 2L)

        assertThrows<IllegalArgumentException> {
            categoryService.updateCategory(1L, request)
        }
    }

    @Test
    fun `updateCategory allows reparenting to valid parent`() {
        val category = BusinessCategory(id = 1L, name = "Cat")
        val newParent = BusinessCategory(id = 3L, name = "New Parent")

        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))
        `when`(categoryRepository.findById(3L)).thenReturn(Optional.of(newParent))
        `when`(categoryRepository.save(any())).thenAnswer { it.arguments[0] }

        val request = UpdateCategoryRequest(parentId = 3L)
        val result = categoryService.updateCategory(1L, request)

        assertEquals(3L, result.parentId)
    }

    @Test
    fun `deleteCategory soft deletes category and cascades to children`() {
        val child1 = BusinessCategory(id = 2L, name = "Child1", isActive = true)
        val child2 = BusinessCategory(id = 3L, name = "Child2", isActive = true)
        val parent = BusinessCategory(id = 1L, name = "Parent", isActive = true)
        parent.children = mutableListOf(child1, child2)
        child1.parent = parent
        child2.parent = parent

        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(parent))

        categoryService.deleteCategory(1L)

        assertFalse(parent.isActive)
        assertFalse(child1.isActive)
        assertFalse(child2.isActive)
        verify(categoryRepository).save(parent)
        verify(categoryRepository).saveAll(listOf(child1, child2))
    }

    @Test
    fun `deleteCategory throws when category not found`() {
        `when`(categoryRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<CategoryNotFoundException> {
            categoryService.deleteCategory(99L)
        }
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=BusinessCategoryServiceTest -Dsurefire.failIfNoTests=false`
Expected: Compilation errors (methods don't exist yet)

**Step 3: Add service methods**

Add these imports and methods to `BusinessCategoryService.kt`:

Add import at top:
```kotlin
import com.mudhut.nudge.businesses.models.UpdateCategoryRequest
import com.mudhut.nudge.utils.exceptions.CategoryNotFoundException
import org.springframework.transaction.annotation.Transactional
```

Add these methods after `getSubcategories`:

```kotlin
fun getCategoryById(id: Long): CategoryResponse {
    val category = categoryRepository.findById(id)
        .orElseThrow { CategoryNotFoundException("Category not found with id: $id") }
    return toResponse(category)
}

@Transactional
fun updateCategory(id: Long, request: UpdateCategoryRequest): CategoryResponse {
    val category = categoryRepository.findById(id)
        .orElseThrow { CategoryNotFoundException("Category not found with id: $id") }

    request.name?.let { newName ->
        if (categoryRepository.existsByNameAndIdNot(newName, id)) {
            throw IllegalArgumentException("Category with name '$newName' already exists")
        }
        category.name = newName
    }

    request.description?.let { category.description = it }

    request.isActive?.let { category.isActive = it }

    if (request.parentId != null) {
        if (request.parentId == id) {
            throw IllegalArgumentException("A category cannot be its own parent")
        }
        val newParent = categoryRepository.findById(request.parentId)
            .orElseThrow { CategoryNotFoundException("Parent category not found with id: ${request.parentId}") }
        if (isDescendant(newParent, category)) {
            throw IllegalArgumentException("Cannot set parent to a descendant category (circular reference)")
        }
        category.parent = newParent
    }

    val saved = categoryRepository.save(category)
    return toResponse(saved)
}

@Transactional
fun deleteCategory(id: Long) {
    val category = categoryRepository.findById(id)
        .orElseThrow { CategoryNotFoundException("Category not found with id: $id") }

    category.isActive = false
    categoryRepository.save(category)

    if (category.children.isNotEmpty()) {
        category.children.forEach { it.isActive = false }
        categoryRepository.saveAll(category.children)
    }
}

private fun isDescendant(potentialDescendant: BusinessCategory, ancestor: BusinessCategory): Boolean {
    var current: BusinessCategory? = potentialDescendant
    while (current != null) {
        if (current.id == ancestor.id) return true
        current = current.parent
    }
    return false
}
```

**Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=BusinessCategoryServiceTest`
Expected: All 8 tests PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/services/BusinessCategoryService.kt src/test/kotlin/com/mudhut/nudge/businesses/services/BusinessCategoryServiceTest.kt
git commit -m "feat: add getCategoryById, updateCategory, deleteCategory service methods with tests"
```

---

### Task 5: Update SecurityConfig for PUT/DELETE authorization

**Files:**
- Modify: `src/main/kotlin/com/mudhut/nudge/config/SecurityConfig.kt`

**Step 1: Add PUT and DELETE matchers**

In `SecurityConfig.kt`, update the `authorizeHttpRequests` block. After the existing POST matcher (line 52-55), add:

```kotlin
.requestMatchers(
    HttpMethod.PUT,
    "/api/v1/categories/{id}"
).hasAnyRole("SUPER_ADMIN", "ADMIN")
.requestMatchers(
    HttpMethod.DELETE,
    "/api/v1/categories/{id}"
).hasAnyRole("SUPER_ADMIN", "ADMIN")
```

The full `authorizeHttpRequests` block should be:

```kotlin
.authorizeHttpRequests { auth ->
    auth
        .requestMatchers("/reset-password").permitAll()
        .requestMatchers("/verify-email").permitAll()
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers(
            HttpMethod.POST,
            "/api/v1/categories"
        ).hasAnyRole("SUPER_ADMIN", "ADMIN")
        .requestMatchers(
            HttpMethod.PUT,
            "/api/v1/categories/{id}"
        ).hasAnyRole("SUPER_ADMIN", "ADMIN")
        .requestMatchers(
            HttpMethod.DELETE,
            "/api/v1/categories/{id}"
        ).hasAnyRole("SUPER_ADMIN", "ADMIN")
        .anyRequest().authenticated()
}
```

**Step 2: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/config/SecurityConfig.kt
git commit -m "feat: add PUT/DELETE category authorization rules to SecurityConfig"
```

---

### Task 6: Add controller endpoints and tests

**Files:**
- Modify: `src/main/kotlin/com/mudhut/nudge/businesses/controllers/BusinessCategoryController.kt`
- Modify: `src/test/kotlin/com/mudhut/nudge/businesses/controllers/BusinessCategoryControllerTest.kt`

**Step 1: Write controller tests**

Add these imports to `BusinessCategoryControllerTest.kt`:

```kotlin
import com.mudhut.nudge.businesses.models.UpdateCategoryRequest
import com.mudhut.nudge.utils.exceptions.CategoryNotFoundException
```

Add these test methods to the test class:

```kotlin
// --- GET by ID tests ---

@Test
@WithMockUser
fun testGetCategoryById_Success() {
    val response = CategoryResponse(1L, "Healthcare", "Health services", null, true, true)

    Mockito.`when`(businessCategoryService.getCategoryById(1L)).thenReturn(response)

    mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories/1"))
        .andExpect(MockMvcResultMatchers.status().isOk)
        .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
        .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Healthcare"))
}

@Test
@WithMockUser
fun testGetCategoryById_NotFound() {
    Mockito.`when`(businessCategoryService.getCategoryById(99L))
        .thenThrow(CategoryNotFoundException("Category not found with id: 99"))

    mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories/99"))
        .andExpect(MockMvcResultMatchers.status().isNotFound)
}

// --- UPDATE tests ---

@Test
@WithMockUser(roles = ["SUPER_ADMIN"])
fun testUpdateCategory_Success() {
    val request = UpdateCategoryRequest(name = "Updated Name", description = "Updated desc")
    val response = CategoryResponse(1L, "Updated Name", "Updated desc", null, true, false)

    Mockito.`when`(businessCategoryService.updateCategory(Mockito.eq(1L), anyObject()))
        .thenReturn(response)

    mockMvc.perform(
        MockMvcRequestBuilders.put("/api/v1/categories/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
    )
        .andExpect(MockMvcResultMatchers.status().isOk)
        .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Updated Name"))
}

@Test
@WithMockUser(roles = ["BASIC_USER"])
fun testUpdateCategory_ForbiddenForBasicUser() {
    val request = UpdateCategoryRequest(name = "Updated Name")

    mockMvc.perform(
        MockMvcRequestBuilders.put("/api/v1/categories/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
    )
        .andExpect(MockMvcResultMatchers.status().isForbidden)
}

@Test
@WithMockUser(roles = ["ADMIN"])
fun testUpdateCategory_NotFound() {
    Mockito.`when`(businessCategoryService.updateCategory(Mockito.eq(99L), anyObject()))
        .thenThrow(CategoryNotFoundException("Category not found with id: 99"))

    val request = UpdateCategoryRequest(name = "New Name")

    mockMvc.perform(
        MockMvcRequestBuilders.put("/api/v1/categories/99")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
    )
        .andExpect(MockMvcResultMatchers.status().isNotFound)
}

// --- DELETE tests ---

@Test
@WithMockUser(roles = ["SUPER_ADMIN"])
fun testDeleteCategory_Success() {
    Mockito.doNothing().`when`(businessCategoryService).deleteCategory(1L)

    mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/categories/1"))
        .andExpect(MockMvcResultMatchers.status().isNoContent)
}

@Test
@WithMockUser(roles = ["BASIC_USER"])
fun testDeleteCategory_ForbiddenForBasicUser() {
    mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/categories/1"))
        .andExpect(MockMvcResultMatchers.status().isForbidden)
}

@Test
@WithMockUser(roles = ["ADMIN"])
fun testDeleteCategory_NotFound() {
    Mockito.doThrow(CategoryNotFoundException("Category not found with id: 99"))
        .`when`(businessCategoryService).deleteCategory(99L)

    mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/categories/99"))
        .andExpect(MockMvcResultMatchers.status().isNotFound)
}
```

**Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=BusinessCategoryControllerTest`
Expected: FAIL (endpoints don't exist yet)

**Step 3: Add controller endpoints**

Add this import to `BusinessCategoryController.kt`:

```kotlin
import com.mudhut.nudge.businesses.models.UpdateCategoryRequest
```

Add these methods to `BusinessCategoryController`:

```kotlin
@GetMapping("/{id}")
fun getCategoryById(@PathVariable id: Long): ResponseEntity<CategoryResponse> {
    return ResponseEntity.ok(categoryService.getCategoryById(id))
}

@PutMapping("/{id}")
fun updateCategory(
    @PathVariable id: Long,
    @Valid @RequestBody request: UpdateCategoryRequest
): ResponseEntity<CategoryResponse> {
    return ResponseEntity.ok(categoryService.updateCategory(id, request))
}

@DeleteMapping("/{id}")
fun deleteCategory(@PathVariable id: Long): ResponseEntity<Void> {
    categoryService.deleteCategory(id)
    return ResponseEntity.noContent().build()
}
```

**Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=BusinessCategoryControllerTest`
Expected: All tests PASS (existing + new)

**Step 5: Commit**

```bash
git add src/main/kotlin/com/mudhut/nudge/businesses/controllers/BusinessCategoryController.kt src/test/kotlin/com/mudhut/nudge/businesses/controllers/BusinessCategoryControllerTest.kt
git commit -m "feat: add get-by-id, update, and soft-delete category endpoints with tests"
```

---

### Task 7: Run full test suite

**Step 1: Run all tests**

Run: `./mvnw test`
Expected: All tests PASS

**Step 2: Fix any failures if needed**

If the existing `testGetSubcategories_Success` test conflicts with the new `GET /{id}` route, verify that Spring correctly differentiates `/api/v1/categories/1` from `/api/v1/categories/1/subcategories` (it should, since the paths are distinct).
