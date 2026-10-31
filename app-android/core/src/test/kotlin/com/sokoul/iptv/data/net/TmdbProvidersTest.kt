package com.sokoul.iptv.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** « Aussi sur » : plateformes d'abonnement françaises lues dans `watch/providers`. */
class TmdbProvidersTest {
    @Test fun `abonnements de France seulement, par priorite`() {
        val o = Json.parseToJsonElement(
            """{"results":{
              "FR":{"flatrate":[{"provider_name":"Netflix","display_priority":3},{"provider_name":"Disney Plus","display_priority":1}],
                    "rent":[{"provider_name":"Apple TV","display_priority":2}]},
              "US":{"flatrate":[{"provider_name":"Hulu","display_priority":1}]}}}""",
        ) as JsonObject
        assertEquals(listOf("Disney Plus", "Netflix"), TmdbClient.parseProviders(o, "FR"))
        assertEquals(emptyList<String>(), TmdbClient.parseProviders(o, "BE"))
        assertEquals(emptyList<String>(), TmdbClient.parseProviders(null, "FR"))
    }
}
