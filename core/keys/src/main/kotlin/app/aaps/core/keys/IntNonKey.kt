package app.aaps.core.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey

@Suppress("SpellCheckingInspection")
enum class IntNonKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true
) : IntNonPreferenceKey {

    ObjectivesManualEnacts("ObjectivesmanualEnacts", 0),
    InsulinTemplate("insulin_template", 2),
    InsulinOrefPeak("insulin_oref_peak", 75),
    RangeToDisplay("rangetodisplay", 6)
}