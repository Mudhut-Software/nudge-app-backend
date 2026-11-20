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
import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedTag
import java.time.LocalDate
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.SubscriptionService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.beans.factory.annotation.Value
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
 *   • A FREE subscription per seeded user, exactly as registration creates one. The job-quota
 *     period anchors on an account's first-ever subscription, so without this the anchor would
 *     be created lazily on the first quota read and every seeded provider's period would start
 *     from whenever someone happened to open the screen.
 *   • Five businesses across different categories, each owned by its own provider
 *   • Each business has 3–4 ACTIVE services plus one tagged bundle service (contents listed in its description)
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
    private val subscriptionService: SubscriptionService,
    /**
     * When false (the dev default, see application-dev.properties) only the categories are
     * seeded: no demo customer, businesses or catalogue, and so no [AnalyticsDemoSeeder]
     * bookings either (it needs the demo owner). Read only when the database is empty.
     */
    @Value("\${nudge.seed.demo-data:false}") private val seedDemoData: Boolean = false,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (categoryRepo.count() == 0L) {
            val categories = seedCategories()
            if (seedDemoData) {
                log.info("DevDataSeeder: empty database — seeding demo data…")
                seedCustomer()
                seedBusinesses(categories)
            } else {
                log.info("DevDataSeeder: empty database — categories only (nudge.seed.demo-data=false).")
            }
        } else {
            log.info("DevDataSeeder: data already present — skipping demo seed.")
        }

        // Always run (dev-only): guarantee every account is usable for physical testing.
        normalizeForTesting()
        logTestCredentials()
    }

    /**
     * Dev-only. Makes every account physically testable and every business owner-backed:
     *   • all users → active, email-verified, password = [DEMO_PASSWORD]
     *   • every business → an active OWNER [BusinessMember] for its owner
     *
     * Idempotent — safe to run on every boot. This is why both seeded businesses AND any
     * created through the UI can all be logged into with the shared password.
     */
    private fun normalizeForTesting() {
        val encoded = passwordEncoder.encode(DEMO_PASSWORD)
        val users = userRepo.findAll()
        users.forEach { u ->
            u.isActive = true
            u.isEmailVerified = true
            u.password = encoded
        }
        userRepo.saveAll(users)

        var createdMemberships = 0
        businessRepo.findAll().forEach { biz ->
            val owner = biz.owner ?: return@forEach
            val bizId = biz.id ?: return@forEach
            val ownerId = owner.id ?: return@forEach
            val existing = businessMemberRepo.findByBusinessIdAndUserId(bizId, ownerId)
            if (existing.isPresent) {
                val member = existing.get()
                member.role = BusinessRole.OWNER
                member.isActive = true
                businessMemberRepo.save(member)
            } else {
                businessMemberRepo.save(
                    BusinessMember(user = owner, business = biz, role = BusinessRole.OWNER, isActive = true)
                )
                createdMemberships++
            }
        }
        log.info(
            "DevDataSeeder: normalized {} users (active + verified + shared password); created {} missing owner memberships.",
            users.size, createdMemberships,
        )
    }

    /** Prints one login line per business so any business can be tested manually. */
    private fun logTestCredentials() {
        val businesses = businessRepo.findAll()
        log.info("DevDataSeeder: ===== TEST LOGINS (all accounts share password '{}') =====", DEMO_PASSWORD)
        businesses.forEach { b -> log.info("DevDataSeeder:   {} → owner {}", b.name, b.owner?.email) }
        log.info("DevDataSeeder: ===== {} businesses, all owner accounts activated =====", businesses.size)
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
        subscriptionService.openFreeFor(customer)
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
        ).also { subscriptionService.openFreeFor(it) }

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
                    ServiceSpec("Deep House Cleaning", "Top-to-bottom clean for a 3-bed home — 2 cleaners, supplies included.", BigDecimal("250000"), null),
                    ServiceSpec("Carpet Cleaning", "Steam-cleaned carpets and rugs.", BigDecimal("80000"), null),
                    ServiceSpec("Laundry Service", "Wash, dry, fold — picked up and dropped off.", BigDecimal("60000"), null),
                    ServiceSpec("Office Move-out Clean", "Full deep clean for office spaces up to 200m².", BigDecimal("200000"), null),
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
                    ServiceSpec("Corporate Lunch", "Hot buffet lunch delivered to your office.", BigDecimal("15000"), "person"),
                    ServiceSpec("Birthday Party Buffet", "Themed buffet for parties of 20 guests.", BigDecimal("180000"), null),
                    ServiceSpec("Wedding Catering", "Bespoke wedding menu — get a custom quote.", null, null),
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
                    ServiceSpec("Full Body Massage", "60-minute Swedish or deep tissue massage.", BigDecimal("120000"), null),
                    ServiceSpec("Manicure & Pedicure", "Classic mani-pedi with gel options.", BigDecimal("60000"), null),
                    ServiceSpec("Hair Styling", "Wash, blow-dry, and styling.", BigDecimal("80000"), null),
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
                    ServiceSpec("Portrait Session", "90-minute studio portrait session with 20 edits.", BigDecimal("200000"), null),
                    ServiceSpec("Event Photography", "Full-day event coverage with edited gallery.", BigDecimal("500000"), null),
                    ServiceSpec("Drone Aerial Video", "Cinematic drone reel for outdoor venues.", BigDecimal("350000"), null),
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
                    ServiceSpec("Dog Walking", "30-minute neighbourhood walk for one dog.", BigDecimal("25000"), "walk"),
                    ServiceSpec("Pet Grooming", "Bath, brush, and nail trim for dogs and cats.", BigDecimal("70000"), null),
                    ServiceSpec("Home Vet Check", "Routine wellness check at your home.", BigDecimal("90000"), null),
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
            val savedBiz = createBusiness(spec, category, owner)
            val (coverUrl, coverPublicId) = COVER_BY_CATEGORY[spec.category] ?: DEFAULT_COVER

            spec.services.forEach { svc ->
                serviceRepo.save(
                    ServiceOffered(
                        business = savedBiz,
                        title = svc.title,
                        description = svc.description,
                        coverImageUrl = coverUrl,
                        coverImagePublicId = coverPublicId,
                        priceAmount = svc.priceAmount,
                        priceCurrency = if (svc.priceAmount == null) null else DEMO_CURRENCY,
                        quantityEnabled = svc.quantityUnit != null,
                        quantityUnit = svc.quantityUnit,
                        status = ServiceOfferedStatus.ACTIVE,
                    )
                )
            }

            // Former "packages" are now tagged, fixed-price services; what comes with
            // the package goes in its description (there is no "included" group kind).
            // Demonstrates the promo tag + validity window.
            val today = LocalDate.now()
            for (bundle in spec.bundles) {
                val bundleService = ServiceOffered(
                    business = savedBiz,
                    title = bundle.title,
                    description = "Bundle offer. Includes: ${bundle.includedItems.joinToString(", ")}.",
                    coverImageUrl = coverUrl,
                    coverImagePublicId = coverPublicId,
                    priceAmount = bundle.priceAmount,
                    priceCurrency = DEMO_CURRENCY,
                    status = ServiceOfferedStatus.ACTIVE,
                    tag = ServiceOfferedTag.HOLIDAY_OFFER,
                    validFrom = today.minusDays(1),
                    validUntil = today.plusDays(30),
                )
                serviceRepo.save(bundleService)
            }

            log.info("DevDataSeeder: seeded business '{}' (owner {}) with {} services and {} bundle services.",
                spec.name, owner.email, spec.services.size, spec.bundles.size)
        }
        seedCatalogueBusinesses(categories)
        return owners
    }

    private fun createBusiness(spec: BusinessSpec, category: BusinessCategory, owner: User): Business {
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

        return savedBiz
    }

    /**
     * Two businesses that show off the item catalogue: a wedding designer (one package with
     * single- and multiple-choice groups, one unpriced service) and a juice seller (bottles sold
     * alone, a sized drink, and a "choose 3 bottles" pack with quantity steppers).
     * Everything here satisfies the rules OptionGroupService enforces at the API.
     */
    private fun seedCatalogueBusinesses(categories: Map<String, BusinessCategory>) {
        val rustic = seedCatalogueBusiness(
            categories,
            BusinessSpec(
                name = "Rustic Weddings",
                category = "Events & Photography",
                ownerName = "Mercy Namutebi",
                ownerEmail = "mercy@rusticweddings.local",
                ownerPhone = "+256781000006",
                description = "Barn-style and garden wedding design, styling and planning.",
                address = "Muyenga Hill, Kampala",
                latitude = 0.29500, longitude = 32.61500,
                serviceAreas = listOf("Kampala", "Wakiso", "Entebbe"),
                email = "hello@rusticweddings.local",
                phone = "+256700000060",
                services = emptyList(),
                bundles = emptyList(),
            ),
        )
        val wedding = rustic.item(
            "Rustic Wedding", "A complete rustic wedding set-up: choose your style, palette and table layout.",
            price = BigDecimal("3500000"), estimateReason = "Final price depends on venue size and guest count",
        )
        rustic.item("Full Wedding Planning", "Month-by-month planning and day-of coordination. Price agreed after a consultation.")
        val hayBale = rustic.choiceItem("Hay-bale lounge", "Relaxed seating corner of stacked hay bales and throws.", BigDecimal("300000"))
        // Also bookable alone at 450,000; in Optional extras it has no override, so it shows +450,000.
        val canopy = rustic.item("Fairy-light canopy", "Overhead canopy of warm fairy lights.", price = BigDecimal("450000"))
        rustic.group(
            wedding, "Style", GroupSelection.SINGLE, required = true,
            rustic.choiceItem("Classic rustic", "Timber, linen and wildflowers.").choice(),
            rustic.choiceItem("Boho rustic", "Pampas grass, macrame and earthy tones.").choice(),
        )
        rustic.group(
            wedding, "Colour palette", GroupSelection.SINGLE, required = true,
            rustic.choiceItem("Sage & gold", "Soft green with gold accents.").choice(),
            rustic.choiceItem("Terracotta & cream", "Warm clay tones with cream.").choice(),
        )
        rustic.group(
            wedding, "Table arrangement", GroupSelection.SINGLE, required = false,
            rustic.choiceItem("Long banquet tables", "Family-style long tables.").choice(),
            rustic.choiceItem("Round tables of 10", "Classic round tables seating ten.").choice(),
        )
        rustic.group(
            wedding, "Optional extras", GroupSelection.MULTIPLE, required = false,
            hayBale.choice(), canopy.choice(),
        )

        val juice = seedCatalogueBusiness(
            categories,
            BusinessSpec(
                name = "Fresh Press Juice",
                category = "Catering",
                ownerName = "Joan Akello",
                ownerEmail = "joan@freshpress.local",
                ownerPhone = "+256781000007",
                description = "Cold-pressed fruit juice, bottled to order.",
                address = "Ntinda, Kampala",
                latitude = 0.35400, longitude = 32.61700,
                serviceAreas = listOf("Kampala"),
                email = "hello@freshpress.local",
                phone = "+256700000070",
                services = emptyList(),
                bundles = emptyList(),
            ),
        )
        val bottlePrice = BigDecimal("14000")
        val mango = juice.item("Mango 1L", "One litre of cold-pressed mango juice.", price = bottlePrice)
        val passion = juice.item("Passion 1L", "One litre of cold-pressed passion juice.", price = bottlePrice)
        val ginger = juice.item("Pineapple-ginger 1L", "One litre of pineapple juice with fresh ginger.", price = bottlePrice)
        // Priced at zero so the total is driven entirely by the Size pick (no price would read "to be confirmed").
        val fresh = juice.item("Fresh Juice", "Pressed to order: pick a size and the flavours you want.", price = BigDecimal.ZERO)
        juice.group(
            fresh, "Size", GroupSelection.SINGLE, required = true,
            juice.choiceItem("300ml", "A small bottle.", BigDecimal("5000")).choice(),
            juice.choiceItem("1 litre", "A family bottle.", BigDecimal("14000")).choice(),
        )
        juice.group(
            fresh, "Flavour", GroupSelection.MULTIPLE, required = false,
            juice.choiceItem("Mango", "Sweet and thick.").choice(),
            juice.choiceItem("Passion", "Sharp and bright.").choice(),
            juice.choiceItem("Pineapple-ginger", "Tangy with a kick.").choice(),
        )
        val pack = juice.item("Party Pack", "Three one-litre bottles, mixed as you like.", price = BigDecimal("36000"))
        juice.group(
            pack, "Choose 3 bottles", GroupSelection.MULTIPLE, required = true, minPicks = 3, maxPicks = 3,
            listOf(mango, passion, ginger).map { it.choice(priceOverride = BigDecimal.ZERO, quantityEnabled = true, maxQuantity = 3) },
        )
    }

    private fun seedCatalogueBusiness(categories: Map<String, BusinessCategory>, spec: BusinessSpec): CatalogueSeed {
        val category = categories[spec.category]
            ?: error("Seeder: category '${spec.category}' missing for business '${spec.name}'")
        val owner = seedOwner(spec)
        val biz = createBusiness(spec, category, owner)
        val (coverUrl, coverPublicId) = COVER_BY_CATEGORY[spec.category] ?: DEFAULT_COVER
        log.info("DevDataSeeder: seeded catalogue business '{}' (owner {}).", spec.name, owner.email)
        return CatalogueSeed(biz, coverUrl, coverPublicId, serviceRepo)
    }
}

/** Small builder around one seeded business: items, then groups linking them. */
private class CatalogueSeed(
    private val business: Business,
    private val coverUrl: String,
    private val coverPublicId: String,
    private val serviceRepo: ServiceOfferedRepository,
) {
    fun item(
        title: String,
        description: String,
        price: BigDecimal? = null,
        estimateReason: String? = null,
        soldOnItsOwn: Boolean = true,
    ): ServiceOffered = serviceRepo.save(
        ServiceOffered(
            business = business,
            title = title,
            description = description,
            coverImageUrl = coverUrl,
            coverImagePublicId = coverPublicId,
            priceAmount = price,
            priceCurrency = if (price == null) null else DEMO_CURRENCY,
            priceIsEstimate = estimateReason != null,
            estimateReason = estimateReason,
            soldOnItsOwn = soldOnItsOwn,
            status = ServiceOfferedStatus.ACTIVE,
        )
    )

    /** An item that exists only to be picked inside a group, so it is not bookable on its own. */
    fun choiceItem(title: String, description: String, price: BigDecimal? = null): ServiceOffered =
        item(title, description, price, soldOnItsOwn = false)

    fun group(
        parent: ServiceOffered,
        name: String,
        selection: GroupSelection,
        required: Boolean,
        vararg choices: ChoiceSpec,
    ) = group(parent, name, selection, required, null, null, choices.toList())

    fun group(
        parent: ServiceOffered,
        name: String,
        selection: GroupSelection,
        required: Boolean,
        minPicks: Int? = null,
        maxPicks: Int? = null,
        choices: List<ChoiceSpec>,
    ) {
        require(selection == GroupSelection.MULTIPLE || (minPicks == null && maxPicks == null && choices.none { it.quantityEnabled })) {
            "Seeder: single-choice group '$name' cannot set picks limits or quantity choices"
        }
        val group = OptionGroup(
            item = parent, name = name, selection = selection, required = required,
            minPicks = minPicks, maxPicks = maxPicks, position = parent.groups.size,
        )
        choices.forEachIndexed { index, spec ->
            require(spec.item.business?.id == business.id) { "Seeder: '${spec.item.title}' belongs to another business" }
            group.choices.add(
                GroupChoice(
                    group = group, item = spec.item, priceOverride = spec.priceOverride,
                    quantityEnabled = spec.quantityEnabled,
                    maxQuantity = if (spec.quantityEnabled) spec.maxQuantity else null,
                    position = index,
                )
            )
        }
        parent.groups.add(group)
        serviceRepo.save(parent)
    }
}

private class ChoiceSpec(
    val item: ServiceOffered,
    val priceOverride: BigDecimal?,
    val quantityEnabled: Boolean,
    val maxQuantity: Int?,
)

private fun ServiceOffered.choice(
    priceOverride: BigDecimal? = null,
    quantityEnabled: Boolean = false,
    maxQuantity: Int? = null,
) = ChoiceSpec(this, priceOverride, quantityEnabled, maxQuantity)

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
    val priceAmount: BigDecimal?,
    val quantityUnit: String?,
)

private data class BundleSpec(
    val title: String,
    val priceAmount: BigDecimal,
    val includedItems: List<String>,
)
