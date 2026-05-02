package com.banking.statement.categorization

import kotlin.test.Test
import kotlin.test.assertTrue

class MerchantAliasesTest {

    @Test
    fun aliasMapIsNonEmpty() {
        assertTrue(MerchantAliases.aliases.isNotEmpty(), "MerchantAliases.aliases should not be empty")
    }

    @Test
    fun aliasesAreNormalized() {
        // All keys and values should be lowercase with no special characters,
        // since they're matched against pre-normalized merchant names.
        val specialChars = Regex("[^a-z0-9äöüß \\s]")
        for ((alias, canonical) in MerchantAliases.aliases) {
            assertTrue(alias == alias.lowercase(), "Alias '$alias' should be lowercase")
            assertTrue(canonical == canonical.lowercase(), "Canonical '$canonical' should be lowercase")
            assertTrue(
                !specialChars.containsMatchIn(alias),
                "Alias '$alias' should not contain special characters"
            )
            assertTrue(
                !specialChars.containsMatchIn(canonical),
                "Canonical '$canonical' should not contain special characters"
            )
        }
    }

    @Test
    fun aliasesAreNotIdentityMappings() {
        // No alias should map to itself (would be a no-op)
        for ((alias, canonical) in MerchantAliases.aliases) {
            assertTrue(
                alias != canonical,
                "Alias '$alias' maps to itself — remove or fix"
            )
        }
    }

    @Test
    fun expectedCommonAliasesPresent() {
        // Spot-check some critical aliases that real bank statements use
        val critical = listOf(
            "amazon mktplce",
            "rewe markt",
            "lidl sagt danke",
            "deutsche bahn",
            "starbucks coffee"
        )
        for (alias in critical) {
            assertTrue(
                MerchantAliases.aliases.containsKey(alias),
                "Expected critical alias '$alias' to be defined"
            )
        }
    }
}
