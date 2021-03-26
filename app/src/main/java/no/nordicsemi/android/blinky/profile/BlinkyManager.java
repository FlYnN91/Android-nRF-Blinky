/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.blinky.profile;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.UUID;

import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.livedata.ObservableBleManager;
import no.nordicsemi.android.blinky.profile.callback.BlinkyButtonDataCallback;
import no.nordicsemi.android.blinky.profile.callback.BatteryVoltageDataCallback;
import no.nordicsemi.android.blinky.profile.callback.MotorVoltageDataCallback;
import no.nordicsemi.android.blinky.profile.data.BlinkyLED;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.LogSession;
import no.nordicsemi.android.log.Logger;

public class BlinkyManager extends ObservableBleManager {
	/** Nordic Blinky Service UUID. */
	public final static UUID LBS_UUID_SERVICE = UUID.fromString("00005300-e91e-4df7-a812-0c16593976e5");
	/** BUTTON characteristic UUID. */
	private final static UUID LBS_UUID_BUTTON_CHAR = UUID.fromString("00005301-e91e-4df7-a812-0c16593976e5");
	/** Battery Voltage characteristic UUID. */
	private final static UUID LBS_UUID_BAT_VOLT_CHAR = UUID.fromString("00005302-e91e-4df7-a812-0c16593976e5");
	/** Motor Voltage characteristic UUID */
	private final static UUID LBS_UUID_MOT_VOLT_CHAR = UUID.fromString("00005303-e91e-4df7-a812-0c16593976e5");
	/** Window Status characteristic UUID */
	private final static UUID LBS_UUID_WINDOW_CHAR = UUID.fromString("00005304-e91e-4df7-a812-0c16593976e5");
	/** Window Command characteristic UUID */
	private final static UUID LBS_UUID_WINDOW_CMD_CHAR = UUID.fromString("00005305-e91e-4df7-a812-0c16593976e5");
	/** Limit Switch Status characteristic UUID */
	private final static UUID LBS_UUID_LIM_SW_STS_CHAR = UUID.fromString("00005306-e91e-4df7-a812-0c16593976e5");

	private final MutableLiveData<Float> batVoltState = new MutableLiveData<>();
	private final MutableLiveData<Float> motVoltState = new MutableLiveData<>();
	private final MutableLiveData<Boolean> buttonState = new MutableLiveData<>();

	private BluetoothGattCharacteristic buttonCharacteristic, batVoltCharacteristic,motVoltCharacteristic;
	private LogSession logSession;
	private boolean supported;
	private float batVoltActual;
	private float motVoltActual;

	public BlinkyManager(@NonNull final Context context) {
		super(context);
	}

	public final LiveData<Float> getbatVoltState() {
		return batVoltState;
	}

	public final LiveData<Float> getMotVoltState() {
		return motVoltState;
	}

	public final LiveData<Boolean> getButtonState() {
		return buttonState;
	}

	@NonNull
	@Override
	protected BleManagerGattCallback getGattCallback() {
		return new BlinkyBleManagerGattCallback();
	}

	/**
	 * Sets the log session to be used for low level logging.
	 * @param session the session, or null, if nRF Logger is not installed.
	 */
	public void setLogger(@Nullable final LogSession session) {
		logSession = session;
	}

	@Override
	public void log(final int priority, @NonNull final String message) {
		// The priority is a Log.X constant, while the Logger accepts it's log levels.
		Logger.log(logSession, LogContract.Log.Level.fromPriority(priority), message);
	}

	@Override
	protected boolean shouldClearCacheWhenDisconnected() {
		return !supported;
	}

	/**
	 * The Button callback will be notified when a notification from Button characteristic
	 * has been received, or its data was read.
	 * <p>
	 * If the data received are valid (single byte equal to 0x00 or 0x01), the
	 * {@link BlinkyButtonDataCallback#onButtonStateChanged} will be called.
	 * Otherwise, the {@link BlinkyButtonDataCallback#onInvalidDataReceived(BluetoothDevice, Data)}
	 * will be called with the data received.
	 */
	private	final BlinkyButtonDataCallback buttonCallback = new BlinkyButtonDataCallback() {
		@Override
		public void onButtonStateChanged(@NonNull final BluetoothDevice device,
										 final boolean pressed) {
			log(LogContract.Log.Level.APPLICATION, "Button " + (pressed ? "pressed" : "released"));
			buttonState.setValue(pressed);
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	/**
	 * The LED callback will be notified when the LED state was read or sent to the target device.
	 * <p>
	 * This callback implements both {@link no.nordicsemi.android.ble.callback.DataReceivedCallback}
	 * and {@link no.nordicsemi.android.ble.callback.DataSentCallback} and calls the same
	 * method on success.
	 * <p>
	 * If the data received were invalid, the
	 * {@link BatteryVoltageDataCallback#onInvalidDataReceived(BluetoothDevice, Data)} will be
	 * called.
	 */
	private final BatteryVoltageDataCallback batVoltCallback = new BatteryVoltageDataCallback() {
		@Override
		public void onBatVoltStateChanged(@NonNull final BluetoothDevice device,
									  final Integer raw_data) {
			batVoltActual = (float)raw_data/100.0f;
			log(LogContract.Log.Level.APPLICATION, "Battery Voltage RAW " + raw_data);
			batVoltState.setValue(batVoltActual);
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			// Data can only invalid if we read them. We assume the app always sends correct data.
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	private final MotorVoltageDataCallback motVoltCallback = new MotorVoltageDataCallback() {
		@Override
		public void onMotVoltStateChanged(@NonNull final BluetoothDevice device,
									  final Integer raw_data) {
			motVoltActual = (float)raw_data/100.0f;
			log(LogContract.Log.Level.APPLICATION, "Motor Voltage RAW " + raw_data);
			motVoltState.setValue(motVoltActual);
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			// Data can only invalid if we read them. We assume the app always sends correct data.
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	/**
	 * BluetoothGatt callbacks object.
	 */
	private class BlinkyBleManagerGattCallback extends BleManagerGattCallback {
		@Override
		protected void initialize() {
			setNotificationCallback(buttonCharacteristic).with(buttonCallback);
			readCharacteristic(batVoltCharacteristic).with(batVoltCallback).enqueue();
			readCharacteristic(motVoltCharacteristic).with(motVoltCallback).enqueue();
			readCharacteristic(buttonCharacteristic).with(buttonCallback).enqueue();
			enableNotifications(buttonCharacteristic).enqueue();
		}

		@Override
		public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
			final BluetoothGattService service = gatt.getService(LBS_UUID_SERVICE);
			if (service != null) {
				buttonCharacteristic = service.getCharacteristic(LBS_UUID_BUTTON_CHAR);
				batVoltCharacteristic = service.getCharacteristic(LBS_UUID_BAT_VOLT_CHAR);
				motVoltCharacteristic = service.getCharacteristic(LBS_UUID_MOT_VOLT_CHAR);
			}

			boolean batVoltNotifyRequest = false;
			if (batVoltCharacteristic != null) {
				final int rxProperties = batVoltCharacteristic.getProperties();
				batVoltNotifyRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
			}

			boolean motVoltNotifyRequest = false;
			if (motVoltCharacteristic != null) {
				final int rxProperties = motVoltCharacteristic.getProperties();
				motVoltNotifyRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
			}

			supported = buttonCharacteristic != null &&
					(batVoltCharacteristic != null && batVoltNotifyRequest) &&
					(motVoltCharacteristic != null && motVoltNotifyRequest);
			return supported;
		}

		@Override
		protected void onDeviceDisconnected() {
			buttonCharacteristic = null;
			batVoltCharacteristic = null;
			motVoltCharacteristic = null;
		}
	}
}
