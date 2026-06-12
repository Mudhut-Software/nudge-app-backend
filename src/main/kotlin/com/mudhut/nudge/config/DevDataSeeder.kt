package com.mudhut.nudge.config

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicesoffered.entities.ServiceAddon
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedTag
import java.time.LocalDate
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigDecimal

private const val DEMO_PASSWORD = "user#password#123"
private const val DEMO_CURRENCY = "UGX"

// Unsplash placeholder covers per business category. ServiceOffered's
// cover_image_url + cover_image_public_id columns are NOT NULL, so the
// seeder always supplies a category-themed image so the demo looks decent.
private val COVER_BY_CATEGORY: Map<String, Pair<String, String>> = mapOf(
    "Cleaning" to ("https://images.unsplash.com/photo-1581578731548-c64695cc6952?w=800&q=80" to "seed/cleaning"),
    "Catering" to ("https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=800&q=80" to "seed/catering"),
    "Beauty & Wellness" to ("https://images.unsplash.com/photo-1560066984-138dadb4c035?w=800&q=80" to "seed/beauty"),
    "Events & Photography" to ("https://images.unsplash.com/photo-1452587925148-ce544e77e70d?w=800&q=80" to "seed/photography"),
    "Pet Care" to ("https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?w=800&q=80" to "seed/petcare"),
)
private val DEFAULT_COVER =
    "https://images.unsplash.com/photo-1556745757-8d76bdb6984b?w=800&q=80" to "seed/default"

/**
 * Idempotent dev-only seeder. Runs on application start when the `dev` profile is active
 * AND no categories exist yet. Populates a small but diverse demo dataset:
 *   • 12 top-level service categories
 *   • A demo customer plus one distinct owner per business (all `password123`)
 *   • Five businesses across different categories, each owned by its own provider
 *   • Each business has 3–4 ACTIVE services plus one tagged bundle service (with addons)
 *
 * Skipped silently in other profiles or when data already exists, so re-running the app
 * never duplicates rows.
 */
@Component
@Profile("dev")
class DevDataSeeder(
    private val categoryRepo: BusinessCategoryRepository,
    private val userRepo: UserRepository,
    private val businessRepo: BusinessRepository,
    private val businessMemberRepo: BusinessMemberRepository,
    private val serviceRepo: ServiceOfferedRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    @Transactional
    override fun run(args: ApplicationArguments?) {
        if (categoryRepo.count() > 0L) {
            log.info("DevDataSeeder: categories already present — skipping seed.")
            return
        }
        log.info("DevDataSeeder: empty database — seeding demo data…")

        val categories = seedCategories()
        val customer = seedCustomer()
        val owners = seedBusinesses(categories)

        log.info("DevDataSeeder: done. Customer = {} / {}.", customer.email, DEMO_PASSWORD)
        log.info("DevDataSeeder: {} business owners (all / {}): {}",
            owners.size, DEMO_PASSWORD, owners.joinToString(", ") { it.email!! })
    }

    private fun seedCategories(): Map<String, BusinessCategory> {
        val names = listOf(
            "Beauty & Wellness", "Catering", "Cleaning", "Delivery & Errands",
            "Events & Photography", "Home Repairs", "Landscaping", "Laundry",
            "Moving", "Pet Care", "Tutoring", "Vehicle Services",
        )
        val saved = names.map { name -> categoryRepo.save(BusinessCategory(name = name, isActive = true)) }
        log.info("DevDataSeeder: seeded {} categories.", saved.size)
        return saved.associateBy { it.name!! }
    }

    private fun seedCustomer(): User {
        val customer = userRepo.save(
            User(
                username = "Demo Customer",
                email = "customer@nudge.local",
                phoneNumber = "+256700000001",
                password = passwordEncoder.encode(DEMO_PASSWORD),
                role = UserRole.BASIC_USER,
                isActive = true,
                isEmailVerified = true,
                isPhoneVerified = false,
            )
        )
        log.info("DevDataSeeder: seeded demo customer.")
        return customer
    }

    /** Creates the per-business owner user from the spec's owner fields. */
    private fun seedOwner(spec: BusinessSpec): User =
        userRepo.save(
            User(
                username = spec.ownerName,
                email = spec.ownerEmail,
                phoneNumber = spec.ownerPhone,
                password = passwordEncoder.encode(DEMO_PASSWORD),
                role = UserRole.BASIC_USER,
                isActive = true,
                isEmailVerified = true,
                isPhoneVerified = false,
            )
        )

    private fun seedBusinesses(categories: Map<String, BusinessCategory>): List<User> {
        val specs: List<BusinessSpec> = listOf(
            BusinessSpec(
                name = "SparkleClean Pro",
                category = "Cleaning",
                ownerName = "Sarah Nakato",
                ownerEmail = "sarah@sparkleclean.local",
                ownerPhone = "+256781000001",
                description = "Trusted home and office cleaning teams across Kampala.",
                address = "Plot 24, Kololo Hill Drive, Kampala",
                latitude = 0.34780, longitude = 32.59010,
                serviceAreas = listOf("Kampala", "Wakiso"),
                email = "hello@sparkleclean.local",
                phone = "+256700000010",
                services = listOf(
                    ServiceSpec("Deep House Cleaning", "Top-to-bottom clean for a 3-bed home — 2 cleaners, supplies included.", PriceMode.FIXED, BigDecimal("250000"), null),
                    ServiceSpec("Carpet Cleaning", "Steam-cleaned carpets and rugs.", PriceMode.FIXED, BigDecimal("80000"), null),
                    ServiceSpec("Laundry Service", "Wash, dry, fold — picked up and dropped off.", PriceMode.FIXED, BigDecimal("60000"), null),
                    ServiceSpec("Office Move-out Clean", "Full deep clean for office spaces up to 200m².", PriceMode.FIXED, BigDecimal("200000"), null),
                ),
                bundles = listOf(
                    BundleSpec("Sparkle Home Bundle", BigDecimal("350000"), listOf("Deep House Cleaning", "Carpet Cleaning", "Laundry Service")),
                ),
            ),
            BusinessSpec(
                name = "Kampala Catering Co.",
                category = "Catering",
                ownerName = "David Okello",
                ownerEmail = "david@kampalacatering.local",
                ownerPhone = "+256781000002",
                description = "Boutique catering for weddings, corporate lunches, and birthdays.",
                address = "Nakawa Business Park, Kampala",
                latitude = 0.32700, longitude = 32.58000,
                serviceAreas = listOf("Kampala", "Entebbe", "Wakiso"),
                email = "events@kampalacatering.local",
                phone = "+256700000020",
                services = listOf(
                    ServiceSpec("Corporate Lunch", "Hot buffet lunch delivered to your office.", PriceMode.PER_UNIT, BigDecimal("15000"), "person"),
                    ServiceSpec("Birthday Party Buffet", "Themed buffet for parties of 20 guests.", PriceMode.FIXED, BigDecimal("180000"), null),
                    ServiceSpec("Wedding Catering", "Bespoke wedding menu — get a custom quote.", PriceMode.QUOTE, null, null),
                ),
                bundles = listOf(
                    BundleSpec("Birthday All-In Package", BigDecimal("320000"), listOf("Birthday Party Buffet", "Corporate Lunch")),
                ),
            ),
            BusinessSpec(
                name = "Acacia Glow Spa",
                category = "Beauty & Wellness",
                ownerName = "Grace Atim",
                ownerEmail = "grace@acaciaglow.local",
                ownerPhone = "+256781000003",
                description = "Calm, plant-filled spa for facials, massage, and nail care.",
                address = "Bugolobi Mall, Kampala",
                latitude = 0.34900, longitude = 32.57500,
                serviceAreas = listOf("Kampala"),
                email = "hello@acaciaglow.local",
                phone = "+256700000030",
                services = listOf(
                    ServiceSpec("Full Body Massage", "60-minute Swedish or deep tissue massage.", PriceMode.FIXED, BigDecimal("120000"), null),
                    ServiceSpec("Manicure & Pedicure", "Classic mani-pedi with gel options.", PriceMode.FIXED, BigDecimal("60000"), null),
                    ServiceSpec("Hair Styling", "Wash, blow-dry, and styling.", PriceMode.FIXED, BigDecimal("80000"), null),
                ),
                bundles = listOf(
                    BundleSpec("Bridal Glow Day", BigDecimal("220000"), listOf("Full Body Massage", "Manicure & Pedicure", "Hair Styling")),
                ),
            ),
            BusinessSpec(
                name = "Highland Photo Studio",
                category = "Events & Photography",
                ownerName = "Brian Mugisha",
                ownerEmail = "brian@highlandphoto.local",
                ownerPhone = "+256781000004",
                description = "Editorial-style photo + video for weddings and family portraits.",
                address = "Kira Road, Kampala",
                latitude = 0.36000, longitude = 32.61000,
                serviceAreas = listOf("Kampala", "Jinja"),
                email = "book@highlandphoto.local",
                phone = "+256700000040",
                services = listOf(
                    ServiceSpec("Portrait Session", "90-minute studio portrait session with 20 edits.", PriceMode.FIXED, BigDecimal("200000"), null),
                    ServiceSpec("Event Photography", "Full-day event coverage with edited gallery.", PriceMode.FIXED, BigDecimal("500000"), null),
                    ServiceSpec("Drone Aerial Video", "Cinematic drone reel for outdoor venues.", PriceMode.FIXED, BigDecimal("350000"), null),
                ),
                bundles = listOf(
                    BundleSpec("Wedding Coverage Pack", BigDecimal("900000"), listOf("Event Photography", "Drone Aerial Video", "Portrait Session")),
                ),
            ),
            BusinessSpec(
                name = "PawPals Kampala",
                category = "Pet Care",
                ownerName = "Linda Auma",
                ownerEmail = "linda@pawpals.local",
                ownerPhone = "+256781000005",
                description = "Daily walks, grooming, and at-home vet visits for your pets.",
                address = "Mengo, Kampala",
                latitude = 0.33500, longitude = 32.55000,
                serviceAreas = listOf("Kampala"),
                email = "woof@pawpals.local",
                phone = "+256700000050",
                services = listOf(
                    ServiceSpec("Dog Walking", "30-minute neighbourhood walk for one dog.", PriceMode.PER_UNIT, BigDecimal("25000"), "walk"),
                    ServiceSpec("Pet Grooming", "Bath, brush, and nail trim for dogs and cats.", PriceMode.FIXED, BigDecimal("70000"), null),
                    ServiceSpec("Home Vet Check", "Routine wellness check at your home.", PriceMode.FIXED, BigDecimal("90000"), null),
                ),
                bundles = listOf(
                    BundleSpec("Premium Pet Care Bundle", BigDecimal("160000"), listOf("Pet Grooming", "Home Vet Check", "Dog Walking")),
                ),
            ),
        )

        val owners = mutableListOf<User>()
        for (spec in specs) {
            val category = categories[spec.category]
                ?: error("Seeder: category '${spec.category}' missing for business '${spec.name}'")

            val owner = seedOwner(spec)
            owners.add(owner)

            val biz = Business(
                name = spec.name,
                description = spec.description,
                owner = owner,
                category = category,
                email = spec.email,
                address = spec.address,
                latitude = spec.latitude,
                longitude = spec.longitude,
                serviceAreas = spec.serviceAreas.toMutableList(),
                status = BusinessStatus.ACTIVE,
            ).also { b ->
                b.phoneNumbers = mutableListOf(
                    BusinessPhoneNumber(phoneNumber = spec.phone, business = b),
                )
            }
            val savedBiz = businessRepo.save(biz)

            businessMemberRepo.save(
                BusinessMember(
                    user = owner,
                    business = savedBiz,
                    role = BusinessRole.OWNER,
                    isActive = true,
                )
            )

            val (coverUrl, coverPublicId) = COVER_BY_CATEGORY[spec.category] ?: DEFAULT_COVER
            spec.services.forEach { svc ->
                serviceRepo.save(
                    ServiceOffered(
                        business = savedBiz,
                        title = svc.title,
                        description = svc.description,
                        coverImageUrl = coverUrl,
                        coverImagePublicId = coverPublicId,
                        priceMode = svc.priceMode,
                        priceAmount = svc.priceAmount,
                        priceCurrency = if (svc.priceMode == PriceMode.QUOTE) null else DEMO_CURRENCY,
                        priceUnit = svc.priceUnit,
                        status = ServiceOfferedStatus.ACTIVE,
                    )
                )
            }

            // Former "packages" are now tagged, FIXED-price services whose included
            // items are default-selected addons (priceDelta 0). Demonstrates the
            // promo tag + validity window and the addon model in one shot.
            val today = LocalDate.now()
            for (bundle in spec.bundles) {
                val bundleService = ServiceOffered(
                    business = savedBiz,
                    title = bundle.title,
                    description = "Bundle offer — included items below, plus optional add-ons.",
                    coverImageUrl = coverUrl,
                    coverImagePublicId = coverPublicId,
                    priceMode = PriceMode.FIXED,
                    priceAmount = bundle.priceAmount,
                    priceCurrency = DEMO_CURRENCY,
                    priceUnit = null,
                    status = ServiceOfferedStatus.ACTIVE,
                    tag = ServiceOfferedTag.HOLIDAY_OFFER,
                    validFrom = today.minusDays(1),
                    validUntil = today.plusDays(30),
                )
                bundle.includedItems.forEachIndexed { idx, itemTitle ->
                    bundleService.addons.add(
                        ServiceAddon(
                            service = bundleService,
                            title = itemTitle,
                            priceDelta = BigDecimal.ZERO,
                            defaultSelected = true,
                            position = idx,
                        )
                    )
                }
                serviceRepo.save(bundleService)
            }

            log.info("DevDataSeeder: seeded business '{}' (owner {}) with {} services and {} bundle services.",
                spec.name, owner.email, spec.services.size, spec.bundles.size)
        }
        return owners
    }
}

private data class BusinessSpec(
    val name: String,
    val category: String,
    val ownerName: String,
    val ownerEmail: String,
    val ownerPhone: String,
    val description: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val serviceAreas: List<String>,
    val email: String,
    val phone: String,
    val services: List<ServiceSpec>,
    val bundles: List<BundleSpec>,
)

private data class ServiceSpec(
    val title: String,
    val description: String,
    val priceMode: PriceMode,
    val priceAmount: BigDecimal?,
    val priceUnit: String?,
)

private data class BundleSpec(
    val title: String,
    val priceAmount: BigDecimal,
    val includedItems: List<String>,
)
