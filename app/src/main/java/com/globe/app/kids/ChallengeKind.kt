package com.globe.app.kids

import com.globe.app.R
/**
 * A mini-challenge that asks the child to find something on the globe by
 * reasoning about what they see (which side faces the Sun, where a marker is).
 * The prompt is shown in a banner; MainActivity evaluates the tapped point.
 */
enum class ChallengeKind(val promptRes: Int) {
    DAYTIME(R.string.text_tap_a_place_where_it_s_daytime),
    NIGHT(R.string.text_tap_a_place_where_it_s_night_time),
    FIND_EARTHQUAKE(R.string.text_find_an_earthquake),
    FIND_VOLCANO(R.string.text_find_a_volcano),
    FIND_WILDFIRE(R.string.text_find_a_wildfire),
    FIND_STORM(R.string.text_find_a_storm);
    fun prompt(context: android.content.Context): String = context.getString(promptRes)
}
