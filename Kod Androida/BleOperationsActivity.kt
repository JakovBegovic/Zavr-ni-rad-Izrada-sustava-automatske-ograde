/*
 * Copyright 2024 Punch Through Design LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.punchthrough.blestarterappandroid

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.punchthrough.blestarterappandroid.ble.ConnectionEventListener
import com.punchthrough.blestarterappandroid.ble.ConnectionManager
import com.punchthrough.blestarterappandroid.ble.ConnectionManager.parcelableExtraCompat
import com.punchthrough.blestarterappandroid.ble.isIndicatable
import com.punchthrough.blestarterappandroid.ble.isNotifiable
import com.punchthrough.blestarterappandroid.ble.isReadable
import com.punchthrough.blestarterappandroid.ble.isWritable
import com.punchthrough.blestarterappandroid.ble.isWritableWithoutResponse
import com.punchthrough.blestarterappandroid.ble.toAsciiString
import com.punchthrough.blestarterappandroid.ble.toHexString
import com.punchthrough.blestarterappandroid.databinding.ActivityBleOperationsBinding
import timber.log.Timber
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID

class BleOperationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBleOperationsBinding
    private val device: BluetoothDevice by lazy {
        intent.parcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")
    }
    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics
        } ?: listOf()
    }
    private val characteristicProperties by lazy {
        characteristics.associateWith { characteristic ->
            mutableListOf<CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }
    }
    private val characteristicAdapter: CharacteristicAdapter by lazy {
        CharacteristicAdapter(characteristics) { characteristic ->
            showCharacteristicOptions(characteristic)
        }
    }
    private val notifyingCharacteristics = mutableListOf<UUID>()

    private val readValues = ArrayList<String>()
    private var amountRead = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectionManager.registerListener(connectionEventListener)

        binding = ActivityBleOperationsBinding.inflate(layoutInflater)

        setContentView(binding.root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.ble_playground)
        }
        setupRecyclerView()
        binding.requestMtuButton.setOnClickListener {
            val userInput = binding.mtuField.text
            if (userInput.isNotEmpty() && userInput.isNotBlank()) {
                userInput.toString().toIntOrNull()?.let { mtu ->
                    log("Zahtijevam MTU vrijednost: $mtu")
                    ConnectionManager.requestMtu(device, mtu)
                } ?: log("Invalidna MTU vrijednost: $userInput")
            } else {
                log("Molimo specificirajte numeričku vrijednost za željeni ATT MTU (23-517)")
            }
            hideKeyboard()
        }
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun filterCharacteristics ( ca: CharacteristicAdapter ): CharacteristicAdapter {
        val filteredCA: CharacteristicAdapter = ca

        Timber.i("Filtering characteristics")

        filteredCA.items = ca.items.filter {
            it.uuid == UUID.fromString("c5374e87-a035-4d2b-a41a-104c3c8198a2")
            || it.uuid == UUID.fromString("c5374e86-a035-4d2b-a41a-104c3c8198a2") }

        return filteredCA;
    }

    private fun setupRecyclerView() {

        binding.characteristicsRecyclerView.apply {
            // adapter = characteristicAdapter;
            adapter = filterCharacteristics(characteristicAdapter);
            layoutManager = LinearLayoutManager(
                this@BleOperationsActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false

            itemAnimator.let {
                if (it is SimpleItemAnimator) {
                    it.supportsChangeAnimations = false
                }
            }
        }
    }


    @SuppressLint("SetTextI18n")
    private fun shortLog(message: String) {
        runOnUiThread {
            val uiText = binding.logTextView.text
            val currentLogText = uiText
            binding.logTextView.text = "$currentLogText\n$message"
            binding.logScrollView.post { binding.logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        val formattedMessage = "${dateFormatter.format(Date())}: $message"
        runOnUiThread {
            val uiText = binding.logTextView.text
            val currentLogText = uiText
            binding.logTextView.text = "$currentLogText\n$formattedMessage"
            binding.logScrollView.post { binding.logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun showCharacteristicOptions(
        characteristic: BluetoothGattCharacteristic
    ) = runOnUiThread {
        characteristicProperties[characteristic]?.let { properties ->
            AlertDialog.Builder(this)
                .setTitle("Odaberite akciju")
                .setItems(properties.map { it.action }.toTypedArray()) { _, i ->
                    when (properties[i]) {
                        CharacteristicProperty.Readable -> {
                            log("Datum i vrijeme zadnjih 10 pokretanja motora:")

                            // Reset values
                            amountRead = 0
                            readValues.clear()

                            ConnectionManager.readCharacteristic(device, characteristic)

                        }
                        CharacteristicProperty.Writable, CharacteristicProperty.WritableWithoutResponse -> {
                            showWritePayloadDialog(characteristic)
                        }
                        CharacteristicProperty.Notifiable, CharacteristicProperty.Indicatable -> {
                            if (notifyingCharacteristics.contains(characteristic.uuid)) {
                                log("Disabling notifications on ${characteristic.uuid}")
                                ConnectionManager.disableNotifications(device, characteristic)
                            } else {
                                log("Enabling notifications on ${characteristic.uuid}")
                                ConnectionManager.enableNotifications(device, characteristic)
                            }
                        }
                    }
                }
                .show()
        }
    }

    @SuppressLint("InflateParams")
    private fun showWritePayloadDialog(characteristic: BluetoothGattCharacteristic) {
        val decimalField = layoutInflater.inflate(R.layout.edittext_decimal_payload, null) as EditText
        AlertDialog.Builder(this)
            .setView(decimalField)
            .setPositiveButton("Pošalji") { _, _ ->
                with(decimalField.text.toString()) {
                    if (isNotBlank() && isNotEmpty() && isInRange(decimalField.text.toString())) {

                        // val bytes = hexToBytes()

                        val bytes = decimalToBytes()

                        // log("Šaljem na ${characteristic.uuid}: ${bytes.toHexString()}")
                        log("Određujem brzinu ${decimalField.text.toString()}")
                        ConnectionManager.writeCharacteristic(device, characteristic, bytes)
                    } else {
                        log("Molimo unesite decimalni broj između -100 i 100 za određivanje brzine")
                    }
                }
            }
            .setNegativeButton("Odustani", null)
            .create()
            .apply {
                window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
                decimalField.showKeyboard()
                show()
            }
    }

    private fun isInRange( text: String): Boolean {
        return text.toInt() > -101 && text.toInt() < 101
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    AlertDialog.Builder(this@BleOperationsActivity)
                        .setTitle("Veza prekinuta")
                        .setMessage("Veza sa uređajem je prekinuta.")
                        .setPositiveButton("OK") { _, _ -> onBackPressed() }
                        .show()
                }
            }

            onCharacteristicRead = { _, characteristic, value ->
                readValues.add(value.toAsciiString())

                if(amountRead < 10){
                    ConnectionManager.readCharacteristic(device, characteristic)
                    amountRead += 1
                } else {
                    amountRead = 0

                    val referenceMillis = readValues[ readValues.size - 1 ].toLong()
                    val now = Instant.now()

                    for(ind in 0..(readValues.size - 2)){
                        val millisDiff = referenceMillis - readValues[ind].toLong()

                        // Subtract the specified number of milliseconds
                        val earlierTime = now.minusMillis(millisDiff)
                        // 10
                        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(
                            ZoneId.systemDefault())
                        shortLog(formatter.format(earlierTime).toString());
                        Timber.i(formatter.format(earlierTime).toString());
                        //shortLog(formatter.format(earlierTime).toString());
                    }
                }
            }


            onCharacteristicWrite = { _, characteristic ->
                log("Poslao na ${characteristic.uuid}")
            }

            onMtuChanged = { _, mtu ->
                log("MTU promijenjen na $mtu")
            }

            onCharacteristicChanged = { _, characteristic, value ->
                log("Vrijednost promijenjena na ${characteristic.uuid}: ${value.toHexString()}")
            }

            onNotificationsEnabled = { _, characteristic ->
                log("Enabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                log("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Primi podatke"
                Writable -> "Odredi brzinu"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun EditText.showKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requestFocus()
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    // private fun String.hexToBytes() =
    //     this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()

    private fun String.decimalToBytes() = this.toInt().toByteArray()

    private fun Int.toByteArray(): ByteArray {
        return byteArrayOf( this.toByte() )
    }


}
