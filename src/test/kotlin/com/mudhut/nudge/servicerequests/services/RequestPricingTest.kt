package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.PricingState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RequestPricingTest {

    private fun bd(v: String) = BigDecimal(v)

    private fun item(
        id: Long? = 1,
        price: String? = "5000",
        estimate: Boolean = false,
        qty: Int = 1,
        currency: String? = null,
        picks: List<PricingInputPick> = emptyList(),
    ) = PricingInputItem(id, "Item $id", price?.let(::bd), currency, estimate, qty, picks)

    private fun pick(price: String? = "1000", qty: Int = 1) =
        PricingInputPick("Fruit", "Mango", price?.let(::bd), qty)

    @Test
    fun `all priced and firm is FIRM`() {
        assertThat(RequestPricing.state(listOf(item()))).isEqualTo(PricingState.FIRM)
    }

    @Test
    fun `estimate is ESTIMATED`() {
        assertThat(RequestPricing.state(listOf(item(estimate = true)))).isEqualTo(PricingState.ESTIMATED)
    }

    @Test
    fun `estimate plus unpriced is TO_BE_CONFIRMED`() {
        val items = listOf(item(1, estimate = true), item(2, price = null))
        assertThat(RequestPricing.state(items)).isEqualTo(PricingState.TO_BE_CONFIRMED)
    }

    @Test
    fun `total is null when TO_BE_CONFIRMED`() {
        assertThat(RequestPricing.total(listOf(item(price = null)))).isNull()
    }

    @Test
    fun `total multiplies item quantity and pick quantity`() {
        val items = listOf(item(qty = 2, picks = listOf(pick("1000", 3))))
        assertThat(RequestPricing.total(items)).isEqualByComparingTo("13000")
    }

    @Test
    fun `null pick price counts as zero`() {
        val items = listOf(item(picks = listOf(pick(null, 2))))
        assertThat(RequestPricing.total(items)).isEqualByComparingTo("5000")
    }

    @Test
    fun `itemised lines list items then picks`() {
        val items = listOf(item(qty = 2, picks = listOf(pick("1000", 3))))
        val lines = RequestPricing.lines(items, null, null)
        assertThat(lines).hasSize(2)
        assertThat(lines[0].description).isEqualTo("Item 1")
        assertThat(lines[0].quantity).isEqualTo(2)
        assertThat(lines[1].description).isEqualTo("+ Fruit: Mango")
        assertThat(lines[1].unitAmount).isEqualByComparingTo("1000")
        assertThat(lines[1].quantity).isEqualTo(3)
    }

    @Test
    fun `unpriced item line is zero`() {
        val lines = RequestPricing.lines(listOf(item(price = null)), null, null)
        assertThat(lines.single().unitAmount).isEqualByComparingTo("0")
    }

    @Test
    fun `agreed price replaces itemised lines with one line`() {
        val lines = RequestPricing.lines(listOf(item(), item(2)), bd("9000"), null)
        assertThat(lines).hasSize(1)
        assertThat(lines[0].description).isEqualTo("Agreed price")
        assertThat(lines[0].unitAmount).isEqualByComparingTo("9000")
        assertThat(lines[0].quantity).isEqualTo(1)
    }

    @Test
    fun `agreed price line carries the note`() {
        val lines = RequestPricing.lines(listOf(item()), bd("9000"), "after site visit")
        assertThat(lines.single().description).isEqualTo("Agreed price: after site visit")
    }

    @Test
    fun `work without agreed is each item total`() {
        val items = listOf(
            item(1, qty = 2, picks = listOf(pick("1000", 3))),
            item(2, price = "700"),
        )
        val work = RequestPricing.workByItem(items, null)
        assertThat(work[1]).isEqualByComparingTo("13000")
        assertThat(work[2]).isEqualByComparingTo("700")
    }

    @Test
    fun `agreed split evenly across two estimated items with remainder on first`() {
        val items = listOf(item(1, estimate = true), item(2, estimate = true))
        val work = RequestPricing.workByItem(items, bd("100.01"))
        assertThat(work[1]).isEqualByComparingTo("50.01")
        assertThat(work[2]).isEqualByComparingTo("50.00")
    }

    @Test
    fun `priced item gets zero of the agreed split`() {
        val items = listOf(item(1), item(2, estimate = true))
        val work = RequestPricing.workByItem(items, bd("100"))
        assertThat(work[1]).isEqualByComparingTo("0")
        assertThat(work[2]).isEqualByComparingTo("100")
    }

    @Test
    fun `unpriced item takes the agreed split`() {
        val items = listOf(item(1), item(2, price = null))
        val work = RequestPricing.workByItem(items, bd("100"))
        assertThat(work[1]).isEqualByComparingTo("0")
        assertThat(work[2]).isEqualByComparingTo("100")
    }

    @Test
    fun `agreed with no estimated or unpriced item splits across all items`() {
        val items = listOf(item(1), item(2))
        val work = RequestPricing.workByItem(items, bd("100.01"))
        assertThat(work[1]).isEqualByComparingTo("50.01")
        assertThat(work[2]).isEqualByComparingTo("50.00")
        assertThat(work.values.reduce(BigDecimal::add)).isEqualByComparingTo("100.01")
    }

    @Test
    fun `currency prefers agreed then first item currency then USD`() {
        val items = listOf(item(1, currency = null), item(2, currency = "UGX"))
        assertThat(RequestPricing.currency(items, "EUR")).isEqualTo("EUR")
        assertThat(RequestPricing.currency(items, null)).isEqualTo("UGX")
        assertThat(RequestPricing.currency(listOf(item()), null)).isEqualTo("USD")
    }
}
