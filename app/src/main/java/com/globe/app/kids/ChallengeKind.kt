package com.globe.app.kids

/**
 * A mini-challenge that asks the child to find something on the globe by
 * reasoning about what they see (which side faces the Sun, where a marker is).
 * The prompt is shown in a banner; MainActivity evaluates the tapped point.
 */
enum class ChallengeKind(val prompt: String) {
    DAYTIME("Tap a place where it's DAYTIME ☀️"),
    NIGHT("Tap a place where it's NIGHT-TIME 🌙"),
    FIND_EARTHQUAKE("Find an earthquake 🔶"),
    FIND_VOLCANO("Find a volcano 🌋"),
    FIND_WILDFIRE("Find a wildfire 🔥"),
    FIND_STORM("Find a storm 🌀"),
}
