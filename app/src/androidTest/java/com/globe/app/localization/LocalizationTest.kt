package com.globe.app.localization

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.globe.app.AppLanguage
import com.globe.app.R
import com.globe.app.explore.JourneyContent
import com.globe.app.kids.MoonPhase
import com.globe.app.kids.NarrationText
import com.globe.app.places.CityCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private fun context(tag: String): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return base.createConfigurationContext(config)
    }

    @Test fun requestedLocalesResolveLabelsAndArabicDirection() {
        val labels = listOf("Today", "Heute", "Hoy", "Aujourd’hui", "اليوم", "आज", "今日", "오늘",
            "Hoje", "Astăzi", "Сьогодні", "今天")
        AppLanguage.tags.zip(labels).forEach { (tag, expected) ->
            assertEquals(tag, expected, context(tag).getString(R.string.companion_today))
        }
        assertEquals(View.LAYOUT_DIRECTION_RTL, context("ar").resources.configuration.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, context("ja-JP").resources.configuration.layoutDirection)
        assertEquals(java.util.Locale.US, AppLanguage.locale(context("it-IT")))
        assertEquals("de-DE", AppLanguage.locale(context("it-IT,de-DE")).toLanguageTag())
    }

    @Test fun lessonCacheChangesLanguageWithoutChangingSceneOrProgressIds() {
        val english = JourneyContent.all(context("en-US"))
        val japanese = JourneyContent.all(context("ja-JP"))
        assertNotEquals(english.first().title, japanese.first().title)
        assertNotEquals(english.first().steps.first().instruction, japanese.first().steps.first().instruction)
        assertEquals(english.map { it.id }, japanese.map { it.id })
        english.zip(japanese).forEach { (a, b) ->
            assertEquals(a.steps.map { it.id }, b.steps.map { it.id })
            assertEquals(a.steps.map { it.correct }, b.steps.map { it.correct })
            assertEquals(a.steps.map { it.lat to it.lon }, b.steps.map { it.lat to it.lon })
        }
        assertEquals(english, JourneyContent.all(context("en-US")))
    }

    @Test fun citySearchAcceptsLocalizedAndOriginalNamesWithoutMutatingCatalog() {
        val original = CityCatalog.cities.first { it.id == "city:bucharest" }
        val ctx = context("ro")
        val localized = CityCatalog.search("", ctx).first { it.id == original.id }
        assertNotEquals(original.name, localized.name)
        assertTrue(CityCatalog.search("București", ctx).any { it.id == original.id })
        assertTrue(CityCatalog.search("Bucharest", ctx).any { it.id == original.id })
        assertEquals(original, CityCatalog.cities.first { it.id == original.id })
        assertEquals(original.zoneId, localized.zoneId)
    }

    @Test fun narrationKeepsHindiAndArabicMarksAndMoonUsesCurrentLocale() {
        assertEquals("पृथ्वी की यात्रा", NarrationText.clean("🌍 पृथ्वी की यात्रा"))
        assertEquals("القَمَرُ جميل", NarrationText.clean("🌙 القَمَرُ جميل"))
        val phase = MoonPhase.current(947182440000L)
        assertNotEquals(phase.name(context("en-US")), phase.name(context("ar")))
    }
}
