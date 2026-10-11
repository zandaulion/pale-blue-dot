package com.globe.app.kids

import com.globe.app.R
/**
 * A rotating "Did you know?" fact for the Today panel. The fact is chosen by the
 * calendar day so it stays the same all day and changes each day, giving kids a
 * reason to come back. Facts are written for ages 9-12 and kept accurate.
 */
object DailyFacts {

    private val facts = listOf(
        R.string.text_the_sun_is_so_huge_that_about_1_3_million_earths_could_fit_inside,
        R.string.text_a_day_on_venus_is_longer_than_a_whole_year_on_venus,
        R.string.text_footprints_on_the_moon_can_last_millions_of_years_because_there_s,
        R.string.text_jupiter_is_so_big_that_all_the_other_planets_could_fit_inside_it,
        R.string.text_sunlight_takes_about_8_minutes_to_travel_all_the_way_to_earth,
        R.string.text_space_is_completely_silent_because_there_s_no_air_to_carry_sound,
        R.string.text_earth_takes_365_and_a_quarter_days_to_orbit_the_sun_that_s_why_we,
        R.string.text_the_tallest_volcano_we_know_of_olympus_mons_on_mars_is_about_thre,
        R.string.text_earth_is_the_only_planet_not_named_after_a_greek_or_roman_god,
        R.string.text_there_are_more_stars_in_the_universe_than_grains_of_sand_on_all_o,
        R.string.text_the_moon_drifts_about_3_8_cm_farther_from_earth_every_year,
        R.string.text_saturn_is_so_light_for_its_size_that_it_would_float_in_a_giant_ba,
        R.string.text_lightning_strikes_somewhere_on_earth_about_100_times_every_second,
        R.string.text_the_space_station_zooms_around_the_whole_earth_about_once_every_9,
        R.string.text_auroras_glow_near_the_poles_when_tiny_particles_from_the_sun_cras,
        R.string.text_about_70_of_earth_s_surface_is_covered_by_ocean,
        R.string.text_venus_is_the_hottest_planet_even_though_mercury_is_closer_to_the,
        R.string.text_the_great_red_spot_on_jupiter_is_a_storm_bigger_than_the_whole_ea,
        R.string.text_earthquakes_happen_when_giant_pieces_of_earth_s_crust_slip_past_e,
        R.string.text_the_sun_is_actually_white_our_air_is_what_makes_it_look_yellow,
        R.string.text_comets_are_giant_balls_of_ice_and_dust_that_grow_glowing_tails_ne,
        R.string.text_if_you_could_drive_a_car_straight_up_you_d_reach_space_in_about_a,
        R.string.text_the_moon_has_no_air_so_its_sky_is_always_black_even_in_the_daytim,
        R.string.text_earth_spins_at_about_1_600_km_h_at_the_equator_but_we_don_t_feel
    )

    fun today(context: android.content.Context, nowMs: Long = System.currentTimeMillis()): String {
        val epochDay = nowMs / 86_400_000L
        return context.getString(facts[Math.floorMod(epochDay, facts.size.toLong()).toInt()])
    }
}
