package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputPercent
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.skyscreamer.jsonassert.JSONAssert

class ActionProfileSwitchPercentTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitchPercent
    @Mock lateinit var activeInsulin: Insulin

    @BeforeEach
    fun setup() {

        `when`(rh.gs(R.string.startprofileforever)).thenReturn("Start profile %d%%")
        `when`(rh.gs(app.aaps.core.ui.R.string.startprofile)).thenReturn("Start profile %d%% for %d min")

        sut = ActionProfileSwitchPercent(injector)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(R.string.profilepercentage)
    }

    @Test fun shortDescriptionTest() {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        assertThat(sut.shortDescription()).isEqualTo("Start profile 100% for 30 min")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(app.aaps.core.ui.R.drawable.ic_actions_profileswitch_24dp)
    }

    @Test fun doActionTest() {
        `when`(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(true)
        `when`(activePlugin.activeInsulin).thenReturn(activeInsulin)
        `when`(activePlugin.activeInsulin.iCfg).thenReturn(ICfg("Test",45,7.0))
        sut.pct = InputPercent(110.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        Mockito.verify(profileFunction, Mockito.times(1)).createProfileSwitch(any(), eq(30), eq(110), eq(0), any(), any(), any(), any())
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        JSONAssert.assertEquals("""{"data":{"percentage":100,"durationInMinutes":30},"type":"ActionProfileSwitchPercent"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("""{"percentage":100,"durationInMinutes":30}""")
        assertThat(sut.pct.value).isWithin(0.001).of(100.0)
        assertThat(sut.duration.getMinutes().toDouble()).isWithin(0.001).of(30.0)
    }
}
