package com.globe.app.kids

/**
 * A rotating "Did you know?" fact for the Today panel. The fact is chosen by the
 * calendar day so it stays the same all day and changes each day, giving kids a
 * reason to come back. Facts are written for ages 9-12 and kept accurate.
 */
object DailyFacts {

    private val facts = listOf(
        "The Sun is so huge that about 1.3 million Earths could fit inside it.",
        "A day on Venus is longer than a whole year on Venus!",
        "Footprints on the Moon can last millions of years, because there's no wind to blow them away.",
        "Jupiter is so big that all the other planets could fit inside it.",
        "Sunlight takes about 8 minutes to travel all the way to Earth.",
        "Space is completely silent, because there's no air to carry sound.",
        "Earth takes 365 and a quarter days to orbit the Sun — that's why we add a leap day every 4 years.",
        "The tallest volcano we know of, Olympus Mons on Mars, is about three times taller than Mount Everest.",
        "Earth is the only planet not named after a Greek or Roman god.",
        "There are more stars in the universe than grains of sand on all of Earth's beaches.",
        "The Moon drifts about 3.8 cm farther from Earth every year.",
        "Saturn is so light for its size that it would float in a giant bathtub of water.",
        "Lightning strikes somewhere on Earth about 100 times every second.",
        "The Space Station zooms around the whole Earth about once every 90 minutes.",
        "Auroras glow near the poles when tiny particles from the Sun crash into our air.",
        "About 70% of Earth's surface is covered by ocean.",
        "Venus is the hottest planet, even though Mercury is closer to the Sun.",
        "The Great Red Spot on Jupiter is a storm bigger than the whole Earth.",
        "Earthquakes happen when giant pieces of Earth's crust slip past each other.",
        "The Sun is actually white — our air is what makes it look yellow.",
        "Comets are giant balls of ice and dust that grow glowing tails near the Sun.",
        "If you could drive a car straight up, you'd reach space in about an hour.",
        "The Moon has no air, so its sky is always black, even in the daytime.",
        "Earth spins at about 1,600 km/h at the equator — but we don't feel it because we spin along with it."
    )

    fun today(nowMs: Long = System.currentTimeMillis()): String {
        val epochDay = nowMs / 86_400_000L
        return facts[(epochDay % facts.size).toInt()]
    }
}
