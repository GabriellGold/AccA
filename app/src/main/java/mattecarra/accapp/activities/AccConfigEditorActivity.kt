package mattecarra.accapp.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import kotlinx.android.synthetic.main.content_acc_config_editor.*
import mattecarra.accapp.utils.AccUtils
import mattecarra.accapp.R
import mattecarra.accapp.data.AccConfig
import mattecarra.accapp.data.Cooldown
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import android.widget.LinearLayout
import android.view.LayoutInflater
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import it.sephiroth.android.library.xtooltip.ClosePolicy
import it.sephiroth.android.library.xtooltip.Tooltip
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread


class AccConfigEditorActivity : AppCompatActivity(), NumberPicker.OnValueChangeListener, CompoundButton.OnCheckedChangeListener {
    private var unsavedChanges = false
    private lateinit var config: AccConfig

    private fun returnResults() {
        val returnIntent = Intent()
        returnIntent.putExtra("data", intent.getBundleExtra("data"))
        returnIntent.putExtra("hasChanges", unsavedChanges)
        returnIntent.putExtra("config", config)
        setResult(Activity.RESULT_OK, returnIntent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putParcelable("config", config)
        outState?.putBoolean("unsavedChanges", unsavedChanges)

        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_acc_config_editor)

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.title = intent?.getStringExtra("title") ?: getString(R.string.acc_config_editor)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        unsavedChanges = savedInstanceState?.getBoolean("hasChanges", false) ?: false

        if(savedInstanceState?.containsKey("config") == true) {
            this.config = savedInstanceState.getParcelable("config")!!
        } else if(intent.hasExtra("config")) {
            this.config = intent.getParcelableExtra("config")
        } else {
            try {
                this.config = AccUtils.readConfig()
            } catch (ex: Exception) {
                ex.printStackTrace()
                showConfigReadError()
                this.config = AccUtils.defaultConfig //if config is null I use default config values.
            }
        }

        initUi()
    }

    private fun showConfigReadError() {
        MaterialDialog(this).show {
            title(R.string.config_error_title)
            message(R.string.config_error_dialog)
            positiveButton(android.R.string.ok)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.acc_config_editor_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_save -> {
                returnResults()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if(unsavedChanges) {
            MaterialDialog(this)
                .show {
                    title(R.string.unsaved_changes)
                    message(R.string.unsaved_changes_message)
                    positiveButton(R.string.save) {
                        returnResults()
                    }
                    negativeButton(R.string.close_without_saving) {
                        finish()
                    }
                    neutralButton(android.R.string.cancel)
                }
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Function for On Boot ImageView OnClick.
     * Opens the dialog to edit the On Boot config parameter.
     */
    fun editOnBootOnClick(view: View) {
        MaterialDialog(this@AccConfigEditorActivity).show {
                            title(R.string.edit_on_boot)
                message(R.string.edit_on_boot_dialog_message)
                input(prefill = this@AccConfigEditorActivity.config.onBoot ?: "", allowEmpty = true, hintRes = R.string.edit_on_boot_dialog_hint) { _, text ->
                    this@AccConfigEditorActivity.config.onBoot = text.toString()
                    this@AccConfigEditorActivity.tv_config_on_boot.text = if(text.isBlank()) getString(R.string.not_set) else text

                    unsavedChanges = true
                }
                positiveButton(R.string.save)
                negativeButton(android.R.string.cancel)
            }
    }

    fun editOnPluggedOnClick(v: View) {
        MaterialDialog(this@AccConfigEditorActivity).show {
            title(R.string.edit_on_plugged)
            message(R.string.edit_on_plugged_dialog_message)
            input(prefill = this@AccConfigEditorActivity.config.onPlugged ?: "", allowEmpty = true, hintRes = R.string.edit_on_boot_dialog_hint) { _, text ->
                this@AccConfigEditorActivity.config.onPlugged = text.toString()
                this@AccConfigEditorActivity.config_on_plugged_textview.text = if(text.isBlank()) getString(R.string.not_set) else text

                unsavedChanges = true
            }
            positiveButton(R.string.save)
            negativeButton(android.R.string.cancel)
        }
    }

    fun editChargingSwitchOnClick(v: View) {
        val automaticString = getString(R.string.automatic)
        val chargingSwitches = listOf(automaticString, *AccUtils.listChargingSwitches().toTypedArray())
        val initialSwitch = config.chargingSwitch
        var currentIndex = chargingSwitches.indexOf(initialSwitch ?: automaticString)

        MaterialDialog(this).show {
            title(R.string.edit_charging_switch)
            noAutoDismiss()

            setActionButtonEnabled(WhichButton.POSITIVE, currentIndex != -1)
            setActionButtonEnabled(WhichButton.NEUTRAL, currentIndex != -1)

            listItemsSingleChoice(items = chargingSwitches, initialSelection = currentIndex, waitForPositiveButton = false)  { _, index, text ->
                currentIndex = index

                setActionButtonEnabled(WhichButton.POSITIVE, index != -1)
                setActionButtonEnabled(WhichButton.NEUTRAL, index != -1)
            }

            positiveButton(R.string.save) {
                val index = currentIndex
                val switch = chargingSwitches[index]

                doAsync {
                    this@AccConfigEditorActivity.config.chargingSwitch = if(index == 0) null else switch
                    this@AccConfigEditorActivity.charging_switch_textview.text = this@AccConfigEditorActivity.config.chargingSwitch ?: getString(R.string.automatic)
                }

                this@AccConfigEditorActivity.unsavedChanges = true

                dismiss()
            }

            neutralButton(R.string.test_switch) {
                val switch = if(currentIndex == 0) null else chargingSwitches[currentIndex]

                Toast.makeText(this@AccConfigEditorActivity, R.string.wait, Toast.LENGTH_LONG).show()
                doAsync {
                    val description =
                        when(AccUtils.testChargingSwitch(switch)) {
                            0 -> R.string.charging_switch_works
                            1 -> R.string.charging_switch_does_not_work
                            2 -> R.string.plug_battery_to_test
                            else -> R.string.error_occurred
                        }

                    uiThread {
                        MaterialDialog(this@AccConfigEditorActivity).show {
                            title(R.string.test_switch)
                            message(description)
                            positiveButton(android.R.string.ok)
                        }
                    }
                }
            }

            negativeButton(android.R.string.cancel) {
                dismiss()
            }
        }
    }

    fun onInfoClick(v: View) {
        when(v.id) {
            R.id.capacity_control_info -> R.string.capacity_control_info
            R.id.voltage_control_info -> R.string.voltage_control_info
            R.id.temperature_control_info -> R.string.temperature_control_info
            R.id.exit_on_boot_info -> R.string.description_exit_on_boot
            R.id.cooldown_info -> R.string.cooldown_info
            R.id.on_plugged_info -> R.string.on_plugged_info
            else -> null
        }?.let {
            Tooltip.Builder(this)
                .anchor(v, 0, 0, false)
                .text(it)
                .arrow(true)
                .closePolicy(ClosePolicy.TOUCH_ANYWHERE_CONSUME)
                .showDuration(-1)
                .overlay(false)
                .maxWidth((resources.displayMetrics.widthPixels / 1.3).toInt())
                .create()
                .show(v, Tooltip.Gravity.LEFT, true)
        }

    }

    private fun initUi() {
        tv_config_on_boot.text = config.onBoot?.let { if(it.isBlank()) getString(R.string.not_set) else it } ?: getString(R.string.not_set)
        exit_on_boot_switch.isChecked = config.onBootExit
        exit_on_boot_switch.setOnCheckedChangeListener { _, isChecked ->
            config.onBootExit = isChecked
            unsavedChanges = true
        }

        config_on_plugged_textview.text = config.onPlugged?.let { if(it.isBlank()) getString(R.string.not_set) else it } ?: getString(R.string.not_set)

        charging_switch_textview.text = config.chargingSwitch ?: getString(R.string.automatic)

        shutdown_capacity_picker.minValue = 0
        shutdown_capacity_picker.maxValue = 20
        shutdown_capacity_picker.value = config.capacity.shutdownCapacity
        shutdown_capacity_picker.setOnValueChangedListener(this)

        resume_capacity_picker.minValue = config.capacity.shutdownCapacity
        resume_capacity_picker.maxValue = config.capacity.pauseCapacity - 1
        resume_capacity_picker.value = config.capacity.resumeCapacity
        resume_capacity_picker.setOnValueChangedListener(this)

        pause_capacity_picker.minValue = config.capacity.resumeCapacity + 1
        pause_capacity_picker.maxValue = 100
        pause_capacity_picker.value = config.capacity.pauseCapacity
        pause_capacity_picker.setOnValueChangedListener(this)

        //temps
        if(config.temp.coolDownTemp >= 90 && config.temp.pauseChargingTemp >= 95) {
            temp_switch.isChecked = false
            cooldown_temp_picker.isEnabled = false
            pause_temp_picker.isEnabled = false
            pause_seconds_picker.isEnabled = false
        }
        temp_switch.setOnCheckedChangeListener(this)

        cooldown_temp_picker.minValue = 20
        cooldown_temp_picker.maxValue = 90
        cooldown_temp_picker.value = config.temp.coolDownTemp
        cooldown_temp_picker.setOnValueChangedListener(this)

        pause_temp_picker.minValue = 20
        pause_temp_picker.maxValue = 95
        pause_temp_picker.value = config.temp.pauseChargingTemp
        pause_temp_picker.setOnValueChangedListener(this)

        pause_seconds_picker.minValue = 10
        pause_seconds_picker.maxValue = 120
        pause_seconds_picker.value = config.temp.waitSeconds
        pause_seconds_picker.setOnValueChangedListener(this)

        //cooldown
        if(config.cooldown == null || config.capacity.coolDownCapacity > 100) {
            cooldown_switch.isChecked = false
            cooldown_percentage_picker.isEnabled = false
            charge_ratio_picker.isEnabled = false
            pause_ratio_picker.isEnabled = false
        }
        cooldown_switch.setOnCheckedChangeListener(this)

        cooldown_percentage_picker.minValue = config.capacity.shutdownCapacity
        cooldown_percentage_picker.maxValue = 101 //if someone wants to disable it should use the switch but I'm gonna leave it there
        cooldown_percentage_picker.value = config.capacity.coolDownCapacity
        cooldown_percentage_picker.setOnValueChangedListener(this)

        charge_ratio_picker.minValue = 1
        charge_ratio_picker.maxValue = 120 //no reason behind this value
        charge_ratio_picker.value = config.cooldown?.charge ?: 50
        charge_ratio_picker.setOnValueChangedListener(this)

        pause_ratio_picker.minValue = 1
        pause_ratio_picker.maxValue = 120 //no reason behind this value
        pause_ratio_picker.value = config.cooldown?.pause ?: 10
        pause_ratio_picker.setOnValueChangedListener(this)

        //voltage control
        voltage_control_file.text = config.voltControl.voltFile ?: "Not supported"
        voltage_max.text = config.voltControl.voltMax?.let { "$it mV" } ?: getString(R.string.disabled)

        //Edit voltage dialog
        edit_voltage_limit.setOnClickListener {
            val dialog = MaterialDialog(this@AccConfigEditorActivity).show {
                customView(R.layout.voltage_control_editor_dialog)
                positiveButton(android.R.string.ok) { dialog ->
                    val view = dialog.getCustomView()
                    val voltageControl = view.findViewById<Spinner>(R.id.voltage_control_file)
                    val voltageMax = view.findViewById<EditText>(R.id.voltage_max)
                    val checkBox = dialog.findViewById<CheckBox>(R.id.enable_voltage_max)

                    val voltageMaxInt = voltageMax.text.toString().toIntOrNull()
                    if(checkBox.isChecked && voltageMaxInt != null) {
                        this@AccConfigEditorActivity.config.voltControl.voltMax = voltageMaxInt
                        this@AccConfigEditorActivity.config.voltControl.voltFile = voltageControl.selectedItem as String

                        this@AccConfigEditorActivity.voltage_control_file.text = voltageControl.selectedItem as String
                        this@AccConfigEditorActivity.voltage_max.text = "$voltageMaxInt mV"
                    } else {
                        this@AccConfigEditorActivity.config.voltControl.voltMax = null

                        this@AccConfigEditorActivity.voltage_max.text = getString(R.string.disabled)
                    }

                    unsavedChanges = true
                }
                negativeButton(android.R.string.cancel)
            }

            //initialize dialog custom view:
            val view = dialog.getCustomView()
            val voltageMax = view.findViewById<EditText>(R.id.voltage_max)
            val checkBox = dialog.findViewById<CheckBox>(R.id.enable_voltage_max)
            val voltageControl = view.findViewById<Spinner>(R.id.voltage_control_file)

            voltageMax.setText(config.voltControl.voltMax?.toString() ?: "", TextView.BufferType.EDITABLE)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                voltageMax.isEnabled = isChecked

                val voltageMaxVal = voltageMax.text?.toString()?.toIntOrNull()
                val isValid = voltageMaxVal != null && voltageMaxVal >= 3920 && voltageMaxVal < 4200
                voltageMax.error = if (isValid) null else getString(R.string.invalid_voltage_max)
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid  && voltageControl.selectedItemPosition != -1)
            }
            checkBox.isChecked = config.voltControl.voltMax != null
            voltageMax.isEnabled = checkBox.isChecked
            voltageMax.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {}

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val voltageMaxVal = s?.toString()?.toIntOrNull()
                    val isValid = voltageMaxVal != null && voltageMaxVal >= 3920 && voltageMaxVal < 4200
                    voltageMax.error = if(isValid) null else getString(R.string.invalid_voltage_max)
                    dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid  && voltageControl.selectedItemPosition != -1)
                }
            })

            val supportedVoltageControlFiles = ArrayList(AccUtils.listVoltageSupportedControlFiles())
            val currentVoltageFile = config.voltControl.voltFile?.let { currentVoltFile ->
                val currentVoltFileRegex = currentVoltFile.replace("/", """\/""").replace(".", """\.""").replace("?", ".").toRegex()
                val match = supportedVoltageControlFiles.find { currentVoltFileRegex.matches(it) }
                if(match == null) {
                    supportedVoltageControlFiles.add(currentVoltFile)
                    currentVoltFile
                } else {
                    match
                }
            }
            val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, supportedVoltageControlFiles)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            voltageControl.adapter = adapter
            currentVoltageFile?.let {
                voltageControl.setSelection(supportedVoltageControlFiles.indexOf(currentVoltageFile))
            }
            if(voltageControl.selectedItemPosition == -1) {
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
            }
            voltageControl.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val voltageMaxVal = voltageMax.text?.toString()?.toIntOrNull()
                    val isValid = voltageMaxVal != null && voltageMaxVal >= 3920 && voltageMaxVal < 4200
                    dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid && position != -1)
                }
            }
        }
    }

    //Listener to enable/disable temp control and cool down
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        if(buttonView == null) return
        when(buttonView.id) {
            R.id.temp_switch -> {
                cooldown_temp_picker.isEnabled = isChecked
                pause_temp_picker.isEnabled = isChecked
                pause_seconds_picker.isEnabled = isChecked

                if(isChecked) {
                    cooldown_temp_picker.value = 40
                    pause_temp_picker.value = 45

                    config.temp.coolDownTemp = 40
                    config.temp.pauseChargingTemp = 45
                    unsavedChanges = true
                } else {
                    config.temp.coolDownTemp = 90
                    config.temp.pauseChargingTemp = 95
                    unsavedChanges = true
                }
            }

            R.id.cooldown_switch -> {
                cooldown_percentage_picker.isEnabled = isChecked
                charge_ratio_picker.isEnabled = isChecked
                pause_ratio_picker.isEnabled = isChecked

                if(isChecked) {
                    cooldown_percentage_picker.value = 60
                    config.capacity.coolDownCapacity = 60
                    unsavedChanges = true
                } else {
                    cooldown_percentage_picker.value = 101
                    config.capacity.coolDownCapacity = 101
                    unsavedChanges = true
                }
            }

            else -> {}
        }
    }

    override fun onValueChange(picker: NumberPicker?, oldVal: Int, newVal: Int) {
        if(picker == null) return

        when(picker.id) {
            //capacity
            R.id.shutdown_capacity_picker -> {
                config.capacity.shutdownCapacity = newVal

                resume_capacity_picker.minValue = config.capacity.shutdownCapacity
                cooldown_percentage_picker.minValue = config.capacity.shutdownCapacity
            }

            R.id.resume_capacity_picker -> {
                config.capacity.resumeCapacity = newVal

                pause_capacity_picker.minValue = config.capacity.resumeCapacity + 1
            }

            R.id.pause_capacity_picker -> {
                config.capacity.pauseCapacity = newVal

                resume_capacity_picker.maxValue = config.capacity.pauseCapacity - 1
                resume_capacity_picker.maxValue = config.capacity.pauseCapacity - 1
            }

            //temp
            R.id.cooldown_temp_picker ->
                config.temp.coolDownTemp = newVal

            R.id.pause_temp_picker ->
                config.temp.pauseChargingTemp = newVal

            R.id.pause_seconds_picker ->
                config.temp.waitSeconds = newVal

            //coolDown
            R.id.cooldown_percentage_picker ->
                config.capacity.coolDownCapacity = newVal

            R.id.charge_ratio_picker -> {
                if(config.cooldown == null) {
                    config.cooldown = Cooldown(newVal, 10)
                }
                config.cooldown?.charge = newVal
            }

            R.id.pause_ratio_picker -> {
                if(config.cooldown == null) {
                    config.cooldown = Cooldown(50, newVal)
                }
                config.cooldown?.pause = newVal
            }

            else -> {
                return //This allows to skip unsavedChanges = true
            }
        }

        unsavedChanges = true
    }
}
