package app.aaps.plugins.insulin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.setTemplate
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.insulin.databinding.InsulinFragmentBinding
import dagger.android.support.DaggerFragment
import java.text.DecimalFormat
import javax.inject.Inject

class InsulinFragment : DaggerFragment() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var insulinPlugin: InsulinPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var instantiator: Instantiator
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var dateUtil: DateUtil

    private var _binding: InsulinFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val currentInsulin: ICfg
        get() = insulinPlugin.currentInsulin
    private var selectedTemplate = Insulin.InsulinType.OREF_RAPID_ACTING    // Default Insulin (should only be used on new install
    private var minPeak = Insulin.InsulinType.OREF_RAPID_ACTING.peak.toDouble()
    private var maxPeak = Insulin.InsulinType.OREF_RAPID_ACTING.peak.toDouble()

    private val textWatch = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            currentInsulin.insulinLabel = binding.name.text.toString()
            currentInsulin.setPeak(SafeParse.stringToInt(binding.peak.text))
            currentInsulin.setDia(SafeParse.stringToDouble(binding.dia.text))
            doEdit()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InsulinFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.insulinList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (insulinPlugin.isEdited) {
                activity?.let { activity ->
                    OKDialog.showConfirmation(
                        activity, rh.gs(R.string.do_you_want_switch_insulin),
                        {
                            insulinPlugin.currentInsulinIndex = position
                            insulinPlugin.currentInsulin = insulinPlugin.currentInsulin().deepClone()
                            insulinPlugin.isEdited = currentInsulin.insulinTemplate == 0
                            build()
                        }, null
                    )
                }
            } else {
                insulinPlugin.currentInsulinIndex = position
                insulinPlugin.currentInsulin = insulinPlugin.currentInsulin().deepClone()
                insulinPlugin.isEdited = currentInsulin.insulinTemplate == 0
                build()
            }
        }
        if (insulinPlugin.numOfInsulins == 0) {
            insulinPlugin.loadSettings()
        }
        insulinPlugin.setCurrent(insulinPlugin.iCfg)

        binding.insulinTemplate.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            selectedTemplate = insulinFromTemplate(insulinPlugin.insulinTemplateList()[position].toString())
            currentInsulin.setPeak(selectedTemplate.peak)
            currentInsulin.setDia(selectedTemplate.dia)
            insulinPlugin.isEdited = true
            doEdit()
            build()
        }
        binding.insulinAdd.setOnClickListener {
            if (insulinPlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", rh.gs(R.string.save_or_reset_changes_first)) }
            } else {
                selectedTemplate = Insulin.InsulinType.fromInt(preferences.get(IntNonKey.InsulinTemplate))
                insulinPlugin.addNewInsulin(
                    ICfg(
                        insulinLabel = "",                      // Let plugin propose a new unique name from template
                        peak = insulinPlugin.iCfg.getPeak(),    // Current default insulin is default peak for new insulins
                        dia = selectedTemplate.dia,
                        insulinTemplate = 0
                    )
                )
                insulinPlugin.isEdited = true
                build()
            }
        }
        binding.insulinRemove.setOnClickListener {
            if (insulinPlugin.currentInsulinIndex != insulinPlugin.defaultInsulinIndex) {
                insulinPlugin.removeCurrentInsulin(activity)
                insulinPlugin.isEdited = currentInsulin.insulinTemplate == 0
            }
            build()
        }
        binding.reset.setOnClickListener {
            insulinPlugin.currentInsulin = insulinPlugin.currentInsulin().deepClone()
            insulinPlugin.isEdited = currentInsulin.insulinTemplate == 0
            build()
        }
        binding.save.setOnClickListener {
            if (!insulinPlugin.isValidEditState(activity)) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            uel.log(
                action = Action.STORE_INSULIN, source = Sources.Insulin,
                value = ValueWithUnit.SimpleString(insulinPlugin.currentInsulin.insulinLabel)
            )
            currentInsulin.setTemplate(selectedTemplate)
            insulinPlugin.insulins[insulinPlugin.currentInsulinIndex] = currentInsulin
            insulinPlugin.storeSettings()
            build()
        }
        binding.autoName.setOnClickListener {
            binding.name.setText(insulinPlugin.createNewInsulinLabel(currentInsulin, insulinPlugin.currentInsulinIndex, selectedTemplate))
            insulinPlugin.isEdited = true
            build()
        }

        val insulinTemplateList: ArrayList<CharSequence> = insulinPlugin.insulinTemplateList()
        context?.let { context ->
            binding.insulinTemplate.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, insulinTemplateList))
        } ?: return
        binding.insulinTemplate.setText(rh.gs(Insulin.InsulinType.fromPeak(currentInsulin.insulinPeakTime).label), false)
        binding.insulinTemplateText.text = rh.gs(Insulin.InsulinType.fromPeak(currentInsulin.insulinPeakTime).label)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateGUI() {
        if (_binding == null) return
        val isValid = insulinPlugin.isValidEditState(activity)
        val isEdited = insulinPlugin.isEdited
        val insulinList: ArrayList<CharSequence> = insulinPlugin.insulinList()
        context?.let { context ->
            binding.insulinList.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, insulinList))
        } ?: return
        binding.insulinList.setText(insulinPlugin.currentInsulin.insulinLabel, false)
        if (isValid) {
            this.view?.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.okBackgroundColor))
            binding.insulinList.isEnabled = true

            if (isEdited) {
                //edited insulin -> save first
                binding.save.visibility = View.VISIBLE
            } else {
                binding.save.visibility = View.GONE
            }
        } else {
            this.view?.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.errorBackgroundColor))
            binding.insulinList.isEnabled = false
            binding.save.visibility = View.GONE //don't save an invalid profile
        }

        //Show reset button if data was edited
        if (isEdited) {
            binding.reset.visibility = View.VISIBLE
        } else {
            binding.reset.visibility = View.GONE
        }
        //Show Remove button if profileList is empty
        if (insulinPlugin.numOfInsulins > 1)
            binding.insulinRemove.visibility = View.VISIBLE
        else
            binding.insulinRemove.visibility = View.GONE
        binding.graph.show(activePlugin.activeInsulin, currentInsulin)
        binding.insulinTemplateMenu.visibility = (currentInsulin.insulinTemplate==0).toVisibility()
        binding.insulinTemplateFrozen.visibility = (currentInsulin.insulinTemplate!=0).toVisibility()
    }

    fun build() {
        if (currentInsulin.insulinTemplate != 0)
            selectedTemplate = Insulin.InsulinType.fromInt(currentInsulin.insulinTemplate)
        binding.insulinTemplate.setText(rh.gs(selectedTemplate.label), false)
        binding.insulinTemplateText.text = rh.gs(selectedTemplate.label)
        binding.name.removeTextChangedListener(textWatch)
        binding.name.setText(currentInsulin.insulinLabel)
        binding.name.addTextChangedListener(textWatch)
        binding.insulinList.filters = arrayOf()
        binding.insulinList.setText(currentInsulin.insulinLabel)
        when (selectedTemplate) {
            Insulin.InsulinType.OREF_FREE_PEAK -> {
                minPeak = hardLimits.minPeak().toDouble()
                maxPeak = hardLimits.maxPeak().toDouble()
            }

            else                               -> {
                minPeak = currentInsulin.getPeak().toDouble()
                maxPeak = minPeak
            }
        }
        binding.peak.setParams(currentInsulin.getPeak().toDouble(), minPeak, maxPeak, 1.0, DecimalFormat("0"), false, null, textWatch)
        binding.dia.setParams(currentInsulin.getDia(), hardLimits.minDia(), hardLimits.maxDia(), 0.1, DecimalFormat("0.0"), false, null, textWatch)

        updateGUI()
    }

    fun insulinFromTemplate(label: String): Insulin.InsulinType = Insulin.InsulinType.entries.firstOrNull { rh.gs(it.label) == label } ?: Insulin.InsulinType.OREF_FREE_PEAK

    fun doEdit() {
        insulinPlugin.isEdited = true
        updateGUI()
    }
}