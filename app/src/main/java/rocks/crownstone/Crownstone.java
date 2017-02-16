/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rocks.crownstone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.strongloop.android.loopback.LocalInstallation;
import com.strongloop.android.loopback.Model;
import com.strongloop.android.loopback.RestAdapter;
import com.strongloop.android.loopback.callbacks.ListCallback;
import com.strongloop.android.loopback.callbacks.ObjectCallback;
import com.strongloop.android.loopback.callbacks.VoidCallback;

import org.apache.http.client.HttpResponseException;

import java.util.HashMap;
import java.util.List;

import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.loopback.models.Device;
import nl.dobots.loopback.loopback.models.User;
import nl.dobots.loopback.loopback.repositories.DeviceRepository;
import nl.dobots.loopback.loopback.repositories.UserRepository;
import rocks.crownstone.cfg.Settings;


/**
 * Main UI for the demo app.
 */
public class Crownstone extends AppCompatActivity {

	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	public static final String CLOUD_URL = "http://10.27.8.155:3000/api";
//	public static final String CLOUD_URL = "https://crownstone-cloud.herokuapp.com/api";

	/**
     * Substitute you own sender ID here. This is the project number you got
     * from the API Console, as described in "Getting Started."
     */
    String SENDER_ID = "testapp-e247c";

    /**
     * Substitute your own application ID here. This is the id of the
     * application you registered in your LoopBack server by calling
     * Application.register().
     */
    String LOOPBACK_APP_ID = "Crownstone";

    /**
     * Tag used on log messages.
     */
    static final String TAG = "GCM Demo";

    TextView mDisplay;
    GoogleCloudMessaging gcm;
    Context context;

    String regid;
    String deviceId;

	private BroadcastReceiver mNotificationBroadcastReceiver;
    private BroadcastReceiver mRegistrationBroadcastReceiver;
    private boolean isReceiverRegistered;
	private LocalInstallation _localInstallation;
	private Settings _settings;
	private RestAdapter _restAdapter;
	private UserRepository _userRepository;
	private boolean isInstallationRegistered;
	private Device _device;
	private DeviceRepository _deviceRepository;


	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);
        mDisplay = (TextView) findViewById(R.id.display);

        context = getApplicationContext();

		_settings = Settings.getInstance(getApplicationContext());

		// 1. Grab the shared RestAdapter instance.
//		final DemoApplication app = (DemoApplication) getApplication();
//		_restAdapter = app.getLoopBackAdapter();
		_restAdapter = CrownstoneRestAPI.getRestAdapter(this, CLOUD_URL);

        mRegistrationBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(context);
                boolean sentToken = sharedPreferences
                        .getBoolean(QuickstartPreferences.SENT_TOKEN_TO_SERVER, false);

				final String token = intent.getStringExtra("token");
				_localInstallation.setDeviceToken(token);

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mDisplay.append("Device registered, registration ID=" + token + "\n\n");
						saveInstallation(_device, _localInstallation);
					}
				});
            }
        };

		mNotificationBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String message = intent.getStringExtra("message");
//				mDisplay.append("Notification received=" + message + "\n\n");
				mDisplay.append(message + "\n\n");
			}
		};

//		String username = _settings.getUsername();
//		String password = _settings.getPassword();
//		if (!(username.isEmpty() && password.isEmpty())) {
//			final UserRepository userRepo = _restAdapter.createRepository(UserRepository.class);
//			userRepo.getCurrentUserId();
//			userRepo.loginUser(username, password, new UserRepository.LoginCallback() {
//
//				@Override
//				public void onSuccess(AccessToken token, User currentUser) {
//					Log.i(TAG, token.getUserId() + ":" + currentUser.getId());
//				}
//
//				@Override
//				public void onError(Throwable t) {
//					Log.i(TAG, "error: ", t);
//					startActivity(new Intent(Crownstone.this, LoginActivity.class));
//					return;
//				}
//			});
//		}
    }

    @Override
    protected void onResume() {
        super.onResume();

		_userRepository = CrownstoneRestAPI.getUserRepository();

		if (!_userRepository.isLoggedIn()) {
			startActivity(new Intent(this, LoginActivity.class));
			return;
		} else {
			// Registering BroadcastReceiver
			registerReceiver();

			if (checkPlayServices()) {
				updateRegistration();
			}
		}
    }

    @Override
    protected void onPause() {
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegistrationBroadcastReceiver);
//        isReceiverRegistered = false;
        super.onPause();
    }

    private void registerReceiver(){
        if(!isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                    new IntentFilter(QuickstartPreferences.REGISTRATION_COMPLETE));
			LocalBroadcastManager.getInstance(this).registerReceiver(mNotificationBroadcastReceiver,
					new IntentFilter(QuickstartPreferences.NOTIFICATION_MESSAGE));
            isReceiverRegistered = true;
        }
    }

    /**
     * Check the _device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the _device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.i(TAG, "This _device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

	private void getCurrentUser(ObjectCallback<User> callback) {
		User user;
		if ((user = _userRepository.getCachedCurrentUser()) != null) {
			callback.onSuccess(user);
		} else {
			_userRepository.findCurrentUser(callback);
		}
	}

	private void getDeviceInstance(final ObjectCallback<Device> callback) {

		BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		final String address = bluetoothAdapter.getAddress();
		final String name = bluetoothAdapter.getName();

		getCurrentUser(new ObjectCallback<User>() {
			@Override
			public void onSuccess(User user) {
				user.findDevice(address, new ListCallback<Device>() {
					@Override
					public void onSuccess(List<Device> objects) {
						if (objects.size() > 1) {
							Log.e(TAG, "WTF");
						}
						if (objects.size() == 0) {
							createNewDeviceInstance(address, name, callback);
						} else {
							_device = objects.get(0);
							callback.onSuccess(_device);
						}
					}

					@Override
					public void onError(Throwable t) {
						callback.onError(t);
					}
				});
			}

			@Override
			public void onError(Throwable t) {

			}
		});

//		_deviceRepository = CrownstoneRestAPI.getDeviceRepository();
//		_deviceRepository.findByAddress(address, new ObjectCallback<Device>() {
//			@Override
//			public void onSuccess(Device object) {
//				if (object == null) {
//					createNewDeviceInstance(address, name, callback);
//				} else {
//					_device = object;
//					callback.onSuccess(_device);
//				}
//			}
//
//			@Override
//			public void onError(Throwable t) {
//				if (((HttpResponseException)t).getStatusCode() == 404) {
//					createNewDeviceInstance(address, name, callback);
//				} else {
//					callback.onError(t);
//				}
//			}
//		});

	}

	private void createNewDeviceInstance(String address, String name, final ObjectCallback<Device> callback) {

		_deviceRepository = CrownstoneRestAPI.getDeviceRepository();
		_device = _deviceRepository.createObject(new HashMap<String, Object>());
//		_device = new Device();
		_device.setAddress(address);
		_device.setOwnerId(_userRepository.getCurrentUserId());
		_device.setName(name);
		_device.save(new VoidCallback() {
			@Override
			public void onSuccess() {
				Log.i(TAG, "woohooo");
				callback.onSuccess(_device);
			}

			@Override
			public void onError(Throwable t) {
				Log.e(TAG, "uuhhhuuhhh");
				callback.onError(t);
			}
		});

	}

	/**
     * Updates the registration for push notifications.
     */
    private void updateRegistration() {
		if (!isInstallationRegistered) {
			getDeviceInstance(new ObjectCallback<Device>() {
				@Override
				public void onSuccess(Device object) {

					gcm = GoogleCloudMessaging.getInstance(Crownstone.this);

					// 2. Create LocalInstallation instance
					_localInstallation = new LocalInstallation(context, _restAdapter);

					// 3. Update Installation properties that were not pre-filled

					// Enter the id of the LoopBack Application
					_localInstallation.setAppId(LOOPBACK_APP_ID);

					// Substitute a real id of the user logged in this application
					_localInstallation.setUserId((String) _userRepository.getCurrentUserId());

					// 4. Check if we have a valid GCM registration id
					if (_localInstallation.getDeviceToken() != null) {
						// 5a. We have a valid GCM token, all we need to do now
						//     is to save the installation to the server
						saveInstallation(_device, _localInstallation);
					} else {
						// 5b. We don't have a valid GCM token. Get one from GCM
						// and save the installation afterwards.
//            			registerInBackground(installation);
						// Start IntentService to register this application with GCM.
						Intent intent = new Intent(Crownstone.this, RegistrationIntentService.class);
						startService(intent);
					}
					isInstallationRegistered = true;
				}

				@Override
				public void onError(Throwable t) {

				}
			});
		}
    }

//    /**
//     * Registers the application with GCM servers asynchronously.
//     * <p>
//     * Stores the registration ID in the provided LocalInstallation
//     */
//    private void registerInBackground(final LocalInstallation installation) {
//        new AsyncTask<Void, Void, String>() {
//            @Override
//            protected String doInBackground(final Void... params) {
//                try {
//                    final String regid = gcm.register(SENDER_ID);
//                    installation.setDeviceToken(regid);
//                    return "Device registered, registration ID=" + regid;
//                } catch (final IOException ex) {
//                    Log.e(TAG, "GCM registration failed.", ex);
//                    return "Cannot register with GCM:" + ex.getMessage();
//                    // If there is an error, don't just keep trying to register.
//                    // Require the user to click a button again, or perform
//                    // exponential back-off.
//                }
//            }
//
//            @Override
//            protected void onPostExecute(final String msg) {
//                mDisplay.append(msg + "\n\n");
//                saveInstallation(installation);
//            }
//        }.execute(null, null, null);
//    }

	void createInstallation(final Device device, final LocalInstallation installation) {
		device.createInstallation(installation, new VoidCallback() {
			@Override
			public void onSuccess() {
				final Object id = installation.getId();
				final String msg = "Installation created with id " + id;
				Log.i(TAG, msg);
				mDisplay.append(msg + "\n\n");
			}

			@Override
			public void onError(Throwable t) {
				Log.e(TAG, "Cannot save Installation", t);

				final String msg = "Cannot save _device registration,"
						+ " will re-try when restarted.\n"
						+ "Reason: " + t.getMessage();
				mDisplay.append(msg + "\n\n");
			}
		});
	}

	void updateInstallation(final Device device, final LocalInstallation installation) {
		device.updateInstallation(installation, new VoidCallback() {
			@Override
			public void onSuccess() {
				final Object id = installation.getId();
				final String msg = "Installation updated with id " + id;
				Log.i(TAG, msg);
				mDisplay.append(msg + "\n\n");
			}

			@Override
			public void onError(Throwable t) {
				Log.e(TAG, "Cannot save Installation", t);

				final String msg = "Cannot save _device registration,"
						+ " will re-try when restarted.\n"
						+ "Reason: " + t.getMessage();
				mDisplay.append(msg + "\n\n");
			}
		});
	}

	/**
	 * Saves the Installation to the LoopBack server and reports the result.
	 * @param installation
	 */
	void saveInstallation(final Device device, final LocalInstallation installation) {
		device.updateInstallation(installation, new VoidCallback() {
			@Override
			public void onSuccess() {
				final Object id = installation.getId();
				final String msg = "Installation saved with id " + id;
				Log.i(TAG, msg);
				mDisplay.append(msg + "\n\n");
			}

			@Override
			public void onError(Throwable t) {
				device.createInstallation(installation, new VoidCallback() {
					@Override
					public void onSuccess() {
						final Object id = installation.getId();
						final String msg = "Installation saved with id " + id;
						Log.i(TAG, msg);
						mDisplay.append(msg + "\n\n");
					}

					@Override
					public void onError(Throwable t) {
						Log.e(TAG, "Cannot save Installation", t);

						final String msg = "Cannot save _device registration,"
								+ " will re-try when restarted.\n"
								+ "Reason: " + t.getMessage();
						mDisplay.append(msg + "\n\n");
					}
				});
			}
		});
	}

//    /**
//     * Saves the Installation to the LoopBack server and reports the result.
//     * @param installation
//     */
//    void saveInstallation(final LocalInstallation installation) {
//        installation.save(new Model.Callback() {
//
//            @Override
//            public void onSuccess() {
//                final Object id = installation.getId();
//                final String msg = "Installation saved with id " + id;
//                Log.i(TAG, msg);
//                mDisplay.append(msg + "\n\n");
//
//				_device.setInstallationId(id);
//				_device.save(new VoidCallback() {
//					@Override
//					public void onSuccess() {
//
//					}
//
//					@Override
//					public void onError(Throwable t) {
//
//					}
//				});
//            }
//
//            @Override
//            public void onError(final Throwable t) {
//                Log.e(TAG, "Cannot save Installation", t);
//
//                final String msg = "Cannot save _device registration,"
//                        + " will re-try when restarted.\n"
//                        + "Reason: " + t.getMessage();
//                mDisplay.append(msg + "\n\n");
//            }
//        });
//    }

    // Send an upstream message.
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
