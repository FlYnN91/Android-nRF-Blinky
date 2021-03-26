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

package no.nordicsemi.android.blinky;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import no.nordicsemi.android.ble.livedata.state.ConnectionState;
import no.nordicsemi.android.blinky.adapter.DiscoveredBluetoothDevice;
import no.nordicsemi.android.blinky.viewmodels.BlinkyViewModel;

@SuppressWarnings("ConstantConditions")
public class BlinkyActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";

	private BlinkyViewModel viewModel;

	@BindView(R.id.bat_volt_state) TextView batVoltState;
	@BindView(R.id.mot_volt_state) TextView motVoltState;
	@BindView(R.id.lock_state) TextView lockState;
	@BindView(R.id.window_state) TextView windowState;
	@BindView(R.id.window_sts_tool_bar) MaterialToolbar window_toolbar;
	@BindView(R.id.lock_sts_tool_bar) MaterialToolbar lock_toolbar;
	@BindView(R.id.req_win_state) SwitchMaterial req_win_state;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_blinky);
		ButterKnife.bind(this);

		final Intent intent = getIntent();
		final DiscoveredBluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
		final String deviceName = device.getName();
		final String deviceAddress = device.getAddress();

		final MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(deviceName != null ? deviceName : getString(R.string.unknown_device));
		toolbar.setSubtitle(deviceAddress);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		// Configure the view model.
		viewModel = new ViewModelProvider(this).get(BlinkyViewModel.class);
		viewModel.connect(device);

		// Set up views.
		final MaterialToolbar winToolbar = findViewById(R.id.window_sts_tool_bar);
		final MaterialToolbar lockToolbar = findViewById(R.id.lock_sts_tool_bar);
		final TextView batVoltState = findViewById(R.id.bat_volt_state);
		final TextView lockState = findViewById(R.id.lock_state);
		final TextView windowState = findViewById(R.id.window_state);
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final TextView connectionState = findViewById(R.id.connection_state);
		final View content = findViewById(R.id.device_container);
		final View notSupported = findViewById(R.id.not_supported);

		req_win_state.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.setWindowRequest(isChecked));
		viewModel.getConnectionState().observe(this, state -> {
			switch (state.getState()) {
				case CONNECTING:
					progressContainer.setVisibility(View.VISIBLE);
					notSupported.setVisibility(View.GONE);
					connectionState.setText(R.string.state_connecting);
					break;
				case INITIALIZING:
					connectionState.setText(R.string.state_initializing);
					break;
				case READY:
					progressContainer.setVisibility(View.GONE);
					content.setVisibility(View.VISIBLE);
					onConnectionStateChanged(true);
					break;
				case DISCONNECTED:
					if (state instanceof ConnectionState.Disconnected) {
						final ConnectionState.Disconnected stateWithReason = (ConnectionState.Disconnected) state;
						if (stateWithReason.isNotSupported()) {
							progressContainer.setVisibility(View.GONE);
							notSupported.setVisibility(View.VISIBLE);
						}
					}
					// fallthrough
				case DISCONNECTING:
					onConnectionStateChanged(false);
					break;
			}
		});
		viewModel.getBatVoltageState().observe(this, voltage -> {
			batVoltState.setText(String.valueOf(voltage).concat("V"));
		});
		viewModel.getMotVoltageState().observe(this, voltage -> {
			motVoltState.setText(String.valueOf(voltage).concat("V"));
		});
		viewModel.getLockState().observe(this, lockStateData -> {
			switch(lockStateData)
			{
				case LOCK_IDLE_STATE:
					lockState.setText(R.string.LOCK_IDLE_STATE);
					break;
				case LOCK_ERROR_STATE:
					lockState.setText(R.string.LOCK_ERROR_STATE);
					break;
				case LOCK_LOCKED_STATE:
					lockState.setText(R.string.LOCK_LOCKED_STATE);
					lockToolbar.setLogo(R.drawable.ic_lock_locked);
					break;
				case LOCK_MOVING_STATE:
					lockState.setText(R.string.LOCK_MOVING_STATE);
					break;
				case LOCK_UNLOCKED_STATE:
					lockState.setText(R.string.LOCK_UNLOCKED_STATE);
					lockToolbar.setLogo(R.drawable.ic_lock_unlocked);
					break;
				default:
					lockState.setText(R.string.default_lock_state);
					break;
			}
		});
		viewModel.getWindowState().observe(this, winStateData -> {
			switch(winStateData)
			{
				case WINDOW_CLOSED_STATE:
					windowState.setText(R.string.WINDOW_CLOSED_STATE);
					winToolbar.setLogo(R.drawable.ic_window_locked);
					break;
				case WINDOW_ERROR_STATE:
					windowState.setText(R.string.WINDOW_ERROR_STATE);
					winToolbar.setLogo(R.drawable.ic_window_open);
					break;
				case WINDOW_IDLE_STATE:
					windowState.setText(R.string.WINDOW_IDLE_STATE);
					break;
				case WINDOW_MOVING_STATE:
					windowState.setText(R.string.WINDOW_MOVING_STATE);
					break;
				case WINDOW_OPENED_STATE:
					windowState.setText(R.string.WINDOW_OPENED_STATE);
					winToolbar.setLogo(R.drawable.ic_window_open);
					break;
				default:
					windowState.setText(R.string.default_window_state);
					break;
			}
		});
	}

	@OnClick(R.id.action_clear_cache)
	public void onTryAgainClicked() {
		viewModel.reconnect();
	}

	private void onConnectionStateChanged(final boolean connected) {
		if (!connected) {
			batVoltState.setText(R.string.default_bat_volt);
			motVoltState.setText(R.string.button_unknown);
			lockState.setText(R.string.default_lock_state);
			windowState.setText(R.string.default_window_state);
		}
	}
}
