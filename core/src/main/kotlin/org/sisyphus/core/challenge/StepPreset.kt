package org.sisyphus.core.challenge

enum class StepPreset(
    val steps: Int,
    val title: String,
) {
    LIGHT(500, "PEBBLE"),
    MEDIUM(1000, "BOULDER"),
    HEAVY(2000, "MOUNTAIN"),
    CUSTOM(-1, "CUSTOM"),
    ;

    companion object {
        fun fromSteps(steps: Int): StepPreset = entries.firstOrNull { it.steps == steps } ?: CUSTOM
    }
}
