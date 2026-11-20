package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Real Postgres (TEST_DB_URL): the locking finders must issue SELECT ... FOR UPDATE. Proven from
 * the outside: while a transaction holds the row through the finder, a second connection's
 * `FOR UPDATE NOWAIT` on the same row fails with 55P03 (lock_not_available). The plain finder is
 * the control: it leaves the row free. Not @Transactional: the rows must be committed for the
 * second connection to see them.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ServiceRequestLockingFinderTest {

    @Autowired private lateinit var requestRepo: ServiceRequestRepository
    @Autowired private lateinit var userRepo: UserRepository
    @Autowired private lateinit var businessRepo: BusinessRepository
    @Autowired private lateinit var categoryRepo: BusinessCategoryRepository
    @Autowired private lateinit var txManager: PlatformTransactionManager
    @Autowired private lateinit var dataSource: DataSource

    private val tx by lazy { TransactionTemplate(txManager) }
    private val cleanup = mutableListOf<() -> Unit>()

    @AfterEach
    fun tearDown() {
        tx.executeWithoutResult { cleanup.asReversed().forEach { it() } }
    }

    private fun committedRequest(): ServiceRequest = tx.execute {
        val category = categoryRepo.save(BusinessCategory(name = "cat-lock-finder"))
        val owner = userRepo.save(User(email = "lock-owner@example.com", username = "lock-owner", password = "password123"))
        val customer = userRepo.save(User(email = "lock-customer@example.com", username = "lock-customer", password = "password123"))
        val biz = businessRepo.save(Business(name = "lock-biz", owner = owner, category = category))
        val request = requestRepo.save(
            ServiceRequest(customer = customer, business = biz, status = ServiceRequestStatus.CONFIRMED),
        )
        cleanup += { categoryRepo.deleteById(category.id!!) }
        cleanup += { userRepo.deleteById(owner.id!!); userRepo.deleteById(customer.id!!) }
        cleanup += { businessRepo.deleteById(biz.id!!) }
        cleanup += { requestRepo.deleteById(request.id!!) }
        request
    }

    /** Tries to take the row from a second, independent connection without waiting. */
    private fun rowIsFreeFromAnotherConnection(id: Long): Boolean =
        dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                c.prepareStatement("select id from service_requests where id = ? for update nowait").use { ps ->
                    ps.setLong(1, id)
                    ps.executeQuery().use { it.next() }
                }
                true
            } catch (e: SQLException) {
                if (e.sqlState == "55P03") false else throw e
            } finally {
                c.rollback()
            }
        }

    @Test
    fun `findWithLockById holds the row against other writers until commit`() {
        val id = committedRequest().id!!

        tx.executeWithoutResult {
            assertThat(requestRepo.findWithLockById(id)?.id).isEqualTo(id)
            assertThat(rowIsFreeFromAnotherConnection(id)).isFalse()
        }
        assertThat(rowIsFreeFromAnotherConnection(id)).isTrue()
    }

    @Test
    fun `the plain finder leaves the row free - the control`() {
        val id = committedRequest().id!!

        tx.executeWithoutResult {
            assertThat(requestRepo.findById(id)).isPresent
            assertThat(rowIsFreeFromAnotherConnection(id)).isTrue()
        }
    }

    @Test
    fun `business closure's finder locks every live request it returns`() {
        val request = committedRequest()
        val businessId = request.business!!.id!!

        tx.executeWithoutResult {
            val live = requestRepo.findAllWithLockByBusinessIdAndStatusIn(businessId, listOf(ServiceRequestStatus.CONFIRMED))
            assertThat(live.map { it.id }).containsExactly(request.id)
            assertThat(rowIsFreeFromAnotherConnection(request.id!!)).isFalse()
        }
    }

    @Test
    fun `findWithLockById returns null for an unknown id`() {
        tx.executeWithoutResult { assertThat(requestRepo.findWithLockById(-1L)).isNull() }
    }
}
