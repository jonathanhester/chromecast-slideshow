/*
 * Copyright (C) 2013 Google Inc. All Rights Reserved. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package com.jonathanhester.cast_show;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.MediaRouteButton;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.google.cast.ApplicationChannel;
import com.google.cast.ApplicationMetadata;
import com.google.cast.ApplicationSession;
import com.google.cast.CastContext;
import com.google.cast.CastDevice;
import com.google.cast.ContentMetadata;
import com.google.cast.MediaProtocolCommand;
import com.google.cast.MediaRouteAdapter;
import com.google.cast.MediaRouteHelper;
import com.google.cast.MediaRouteStateChangeListener;
import com.google.cast.MessageStream;
import com.google.cast.SessionError;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

/***
 * An activity that plays a chosen sample video on a Cast device and exposes
 * playback and volume controls in the UI.
 */
public class CastShowActivity extends FragmentActivity implements
		MediaRouteAdapter {

	private static final String TAG = CastShowActivity.class.getSimpleName();

	public static final boolean ENABLE_LOGV = true;

	protected static final double MAX_VOLUME_LEVEL = 20;
	private static final double VOLUME_INCREMENT = 0.05;
	private static final int SEEK_FORWARD = 1;
	private static final int SEEK_BACK = 2;
	private static final int SEEK_INCREMENT = 10;

	private boolean mPlayButtonShowsPlay = false;
	private boolean mVideoIsStopped = false;

	private CastContext mCastContext = null;
	private CastDevice mSelectedDevice;
	private CastMedia mMedia;
	private ContentMetadata mMetaData;
	private ApplicationSession mSession;
	private CastShowMessageStream mCommandsMessageStream;
	private MediaRouteButton mMediaRouteButton;
	private MediaRouter mMediaRouter;
	private MediaRouteSelector mMediaRouteSelector;
	private MediaRouter.Callback mMediaRouterCallback;
	private MediaSelectionDialog mMediaSelectionDialog;
	private MediaProtocolCommand mStatus;

	private ImageButton mPlayPauseButton;
	private ImageButton mPreviousButton;
	private ImageButton mNextButton;
	private TextView mStatusText;
	private TextView mCurrentlyPlaying;
	private String mCurrentItemId;
	private RouteInfo mCurrentRoute;
	private NumberPicker mNumSeconds;

	private SampleMediaRouteDialogFactory mDialogFactory;

	/**
	 * Initializes MediaRouter information and prepares for Cast device
	 * detection upon creating this activity.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		logVIfEnabled(TAG, "onCreate called");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_cast_sample);

		mCastContext = new CastContext(getApplicationContext());
		mMedia = new CastMedia(null, null);
		mMetaData = new ContentMetadata();

		mDialogFactory = new SampleMediaRouteDialogFactory();

		MediaRouteHelper.registerMinimalMediaRouteProvider(mCastContext, this);
		mMediaRouter = MediaRouter.getInstance(getApplicationContext());
		mMediaRouteSelector = MediaRouteHelper.buildMediaRouteSelector(
				MediaRouteHelper.CATEGORY_CAST,
				getResources().getString(R.string.app_id), null);

		mMediaRouteButton = (MediaRouteButton) findViewById(R.id.media_route_button);
		mMediaRouteButton.setRouteSelector(mMediaRouteSelector);
		mMediaRouteButton.setDialogFactory(mDialogFactory);
		mMediaRouterCallback = new MyMediaRouterCallback();

		mStatusText = (TextView) findViewById(R.id.play_status_text);
		mCurrentlyPlaying = (TextView) findViewById(R.id.currently_playing);
		mCurrentlyPlaying.setText(getString(R.string.tap_to_select));
		mMediaSelectionDialog = new MediaSelectionDialog(this);

		mPlayPauseButton = (ImageButton) findViewById(R.id.play_pause_button);
		mPreviousButton = (ImageButton) findViewById(R.id.previous_button);
		mNextButton = (ImageButton) findViewById(R.id.next_button);
		initButtons();

		mNumSeconds = (NumberPicker) findViewById(R.id.number_picker);
		mNumSeconds.setMaxValue(10);
		mNumSeconds.setMinValue(1);
		String[] nums = new String[10];
		for (int i = 0; i < nums.length; i++)
			nums[i] = Integer.toString((i + 1) * 30);
		mNumSeconds.setDisplayedValues(nums);

		loadShows();

		Thread myThread = null;
		Runnable runnable = new StatusRunner();
		myThread = new Thread(runnable);
		logVIfEnabled(TAG, "Starting statusRunner thread");
		myThread.start();
	}

	/**
	 * Initializes all buttons by adding user controls and listeners.
	 */
	public void initButtons() {
		mPlayPauseButton.setEnabled(false);
		mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onPlayClicked(!mPlayButtonShowsPlay);
			}
		});
		mPreviousButton.setEnabled(false);
		mPreviousButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onPreviousClicked();
			}
		});
		mNextButton.setEnabled(false);
		mNextButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onNextClicked();
			}
		});
		mCurrentlyPlaying.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				logVIfEnabled(TAG, "Selecting Media");
				mMediaSelectionDialog.setTitle(getResources().getString(
						R.string.medial_dialog_title));
				loadShows();
				mMediaSelectionDialog.show();
			}
		});
	}

	public void onPreviousClicked() {
		if (mCommandsMessageStream != null) {
			mCommandsMessageStream.previous();
		} else {
			Log.e(TAG, "onStopClicked - mMPMS==null");
		}
	}

	public void onNextClicked() {
		if (mCommandsMessageStream != null) {
			mCommandsMessageStream.next();
		} else {
			Log.e(TAG, "onStopClicked - mMPMS==null");
		}
	}

	/**
	 * Plays or pauses the currently loaded media, depending on the current
	 * state of the <code>
	 * mPlayPauseButton</code>.
	 * 
	 * @param playState
	 *            indicates that Play was clicked if true, and Pause was clicked
	 *            if false
	 */
	public void onPlayClicked(boolean playState) {
		if (playState) {
			if (mCommandsMessageStream != null) {
				mCommandsMessageStream.play();
				mPlayButtonShowsPlay = true;
			} else {
				Log.e(TAG, "onClick-Play - mMPMS==null");
			}
			mPlayPauseButton
					.setImageResource(android.R.drawable.ic_media_pause);

		} else {
			if (mCommandsMessageStream != null) {
				mCommandsMessageStream.pause();
				mPlayButtonShowsPlay = false;
			} else {
				Log.e(TAG, "onClick-Play - mMPMS==null");
			}
			mPlayPauseButton.setImageResource(android.R.drawable.ic_media_play);

		}
	}

	@Override
	public void onDeviceAvailable(CastDevice device, String myString,
			MediaRouteStateChangeListener listener) {
		mSelectedDevice = device;
		logVIfEnabled(TAG, "Available device found: " + myString);
		openSession();
	}

	@Override
	public void onSetVolume(double volume) {
	}

	@Override
	public void onUpdateVolume(double volumeChange) {
		try {
			if ((mCurrentItemId != null) && (mCurrentRoute != null)) {
				mCurrentRoute
						.requestUpdateVolume((int) (volumeChange * MAX_VOLUME_LEVEL));
			}
		} catch (IllegalStateException e) {
			Log.e(TAG, "Problem sending Update Volume", e);
		}
	}

	/**
	 * Processes volume up and volume down actions upon receiving them as key
	 * events.
	 */
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int action = event.getAction();
		int keyCode = event.getKeyCode();
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (action == KeyEvent.ACTION_DOWN) {
				double currentVolume;
			}

			return true;
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			if (action == KeyEvent.ACTION_DOWN) {
				double currentVolume;
			}
			return true;
		default:
			return super.dispatchKeyEvent(event);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
				MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
		logVIfEnabled(TAG, "onStart called and callback added");
	}

	/**
	 * Closes a running session upon destruction of this Activity.
	 */
	@Override
	protected void onStop() {
		mMediaRouter.removeCallback(mMediaRouterCallback);
		super.onStop();
		logVIfEnabled(TAG, "onStop called and callback removed");
	}

	@Override
	protected void onDestroy() {
		logVIfEnabled(TAG, "onDestroy called, ending session if session exists");
		if (mSession != null) {
			try {
				if (!mSession.hasStopped()) {
					mSession.endSession();
				}
			} catch (IOException e) {
				Log.e(TAG, "Failed to end session.");
			}
		}
		mSession = null;
		super.onDestroy();
	}

	private void loadShows() {
		AsyncHttpClient client = new AsyncHttpClient();
		client.get("http://jonathanhester.com/shows.json",
				new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(JSONObject response) {
						ArrayList<CastMedia> shows = new ArrayList<CastMedia>();
						try {
							JSONArray arr = response.getJSONArray("shows");
							JSONObject show;

							for (int i = 0; i < arr.length(); i++) {
								show = arr.getJSONObject(i);
								JSONArray imageArr = show
										.getJSONArray("images");
								ArrayList<String> images = new ArrayList<String>();
								for (int j = 0; j < imageArr.length(); j++) {
									images.add(imageArr.getString(j));
								}
								shows.add(new CastMedia(show.getString("name"),
										images));
							}
						} catch (Exception e) {
							Log.d("Exception", e.getMessage());
						}

						mMediaSelectionDialog.addShows(shows);
					}

					@Override
					public void onFailure(int statusCode, Header[] headers,
							String responseBody, Throwable e) {
						// TODO Auto-generated method stub
						super.onFailure(statusCode, headers, responseBody, e);
					}
				});
	}

	/**
	 * A callback class which listens for route select or unselect events and
	 * processes devices and sessions accordingly.
	 */
	private class MyMediaRouterCallback extends MediaRouter.Callback {
		@Override
		public void onRouteSelected(MediaRouter router, RouteInfo route) {
			MediaRouteHelper.requestCastDeviceForRoute(route);
		}

		@Override
		public void onRouteUnselected(MediaRouter router, RouteInfo route) {
			try {
				if (mSession != null) {
					logVIfEnabled(TAG,
							"Ending session and stopping application");
					mSession.setStopApplicationWhenEnding(true);
					mSession.endSession();
				} else {
					Log.e(TAG, "onRouteUnselected: mSession is null");
				}
			} catch (IllegalStateException e) {
				Log.e(TAG, "onRouteUnselected:");
				e.printStackTrace();
			} catch (IOException e) {
				Log.e(TAG, "onRouteUnselected:");
				e.printStackTrace();
			}
			mCommandsMessageStream = null;
			mSelectedDevice = null;
		}
	}

	/**
	 * Starts a new video playback session with the current CastContext and
	 * selected device.
	 */
	private void openSession() {
		mSession = new ApplicationSession(mCastContext, mSelectedDevice);

		// TODO: The below lines allow you to specify either that your
		// application uses the default
		// implementations of the Notification and Lock Screens, or that you
		// will be using your own.
		int flags = 0;

		// Comment out the below line if you are not writing your own
		// Notification Screen.
		// flags |= ApplicationSession.FLAG_DISABLE_NOTIFICATION;

		// Comment out the below line if you are not writing your own Lock
		// Screen.
		// flags |= ApplicationSession.FLAG_DISABLE_LOCK_SCREEN_REMOTE_CONTROL;
		mSession.setApplicationOptions(flags);

		logVIfEnabled(TAG, "Beginning session with context: " + mCastContext);
		logVIfEnabled(TAG, "The session to begin: " + mSession);
		mSession.setListener(new com.google.cast.ApplicationSession.Listener() {

			@Override
			public void onSessionStarted(ApplicationMetadata appMetadata) {
				logVIfEnabled(TAG, "Getting channel after session start");
				ApplicationChannel channel = mSession.getChannel();
				if (channel == null) {
					Log.e(TAG, "channel = null");
					return;
				}
				logVIfEnabled(TAG, "Creating and attaching Message Stream");
				mCommandsMessageStream = new CastShowMessageStream();
				channel.attachMessageStream(mCommandsMessageStream);
			}

			@Override
			public void onSessionStartFailed(SessionError error) {
				Log.e(TAG, "onStartFailed " + error);
			}

			@Override
			public void onSessionEnded(SessionError error) {
				Log.i(TAG, "onEnded " + error);
			}
		});

		mPlayPauseButton.setEnabled(true);
		mNextButton.setEnabled(true);
		mPreviousButton.setEnabled(true);
		try {
			logVIfEnabled(TAG, "Starting session with app name "
					+ getString(R.string.app_name));

			// TODO: To run your own copy of the receiver, you will need to set
			// app_name in
			// /res/strings.xml to your own appID, and then upload the provided
			// receiver
			// to the url that you whitelisted for your app.
			// The current value of app_name is "YOUR_APP_ID_HERE".
			mSession.startSession(getString(R.string.app_id));
		} catch (IOException e) {
			Log.e(TAG, "Failed to open session", e);
		}
	}

	/**
	 * Loads the stored media object and casts it to the currently selected
	 * device.
	 */
	protected void loadMedia() {
		mMetaData.setTitle(mMedia.getTitle());
		int delay = mNumSeconds.getValue();
		mCommandsMessageStream.sendPlaySlideshow(mMedia.getUrls(), delay);
	}

	/**
	 * Stores and attempts to load the passed piece of media.
	 */
	protected void mediaSelected(CastMedia media) {
		this.mMedia = media;
		updateCurrentlyPlaying();
		if (mCommandsMessageStream != null) {
			loadMedia();
		}
	}

	/**
	 * Sets the Cast Device Selection button to visible or not, depending on the
	 * availability of devices.
	 */
	protected final void setMediaRouteButtonVisible() {
		mMediaRouteButton.setVisibility(mMediaRouter.isRouteAvailable(
				mMediaRouteSelector, 0) ? View.VISIBLE : View.GONE);
	}

	/**
	 * Updates the status of the currently playing video in the dedicated
	 * message view.
	 */
	public void updateStatus() {
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					setMediaRouteButtonVisible();
					updateCurrentlyPlaying();
				} catch (Exception e) {
					Log.e(TAG, "Status request failed: " + e);
				}
			}
		});
	}

	/**
	 * A Runnable class that updates a view to display status for the currently
	 * playing media.
	 */
	private class StatusRunner implements Runnable {
		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					updateStatus();
					Thread.sleep(1500);
				} catch (Exception e) {
					Log.e(TAG, "Thread interrupted: " + e);
				}
			}
		}
	}

	/**
	 * Updates a view with the title of the currently playing media.
	 */
	protected void updateCurrentlyPlaying() {
		String playing = "";
		if (mMedia.getTitle() != null) {
			playing = "Media Selected: " + mMedia.getTitle();
			if (mCommandsMessageStream != null) {
				String colorString = "<br><font color=#0066FF>";
				colorString += "Casting to "
						+ mSelectedDevice.getFriendlyName();
				colorString += "</font>";
				playing += colorString;
			}
			mCurrentlyPlaying.setText(Html.fromHtml(playing));
		} else {
			String castString = "<font color=#FF0000>";
			castString += getResources().getString(R.string.tap_to_select);
			castString += "</font>";
			mCurrentlyPlaying.setText(Html.fromHtml(castString));
		}
	}

	/**
	 * Logs in verbose mode with the given tag and message, if the LOCAL_LOGV
	 * tag is set.
	 */
	private void logVIfEnabled(String tag, String message) {
		if (ENABLE_LOGV) {
			Log.v(tag, message);
		}
	}

	private class CastShowMessageStream extends MessageStream {
		CastShowMessageStream() {
			super("HelloWorld");
		}

		@Override
		public void onMessageReceived(JSONObject arg0) {
			// mStatus = mMessageStream.requestStatus();
			//
			// String currentStatus = "Player State: "
			// + mMessageStream.getPlayerState() + "\n";
			// currentStatus += "Device "
			// + mSelectedDevice.getFriendlyName() + "\n";
			// currentStatus += "Title " + mMessageStream.getTitle()
			// + "\n";
			// currentStatus += "Current Position: "
			// + mMessageStream.getStreamPosition() + "\n";
			// currentStatus += "Duration: "
			// + mMessageStream.getStreamDuration() + "\n";
			// currentStatus += "Volume set at: "
			// + (mMessageStream.getVolume() * 100) + "%\n";
			// currentStatus += "requestStatus: " + mStatus.getType()
			// + "\n";
			// mStatusText.setText(currentStatus);

		}

		public void sendPlaySlideshow(ArrayList<String> images, int delay) {
			JSONObject message = new JSONObject();
			try {
				message.put("type", "queue");
				message.put("delay", delay);
				message.put("images", new JSONArray(images));
				sendMessage(message);
			} catch (Exception e) {

			}
		}

		public void pause() {
			JSONObject message = new JSONObject();
			try {
				message.put("type", "pause");
				sendMessage(message);
			} catch (Exception e) {

			}

		}

		public void play() {
			JSONObject message = new JSONObject();
			try {
				message.put("type", "play");
				sendMessage(message);
			} catch (Exception e) {

			}
		}

		public void previous() {
			JSONObject message = new JSONObject();
			try {
				message.put("type", "previous");
				sendMessage(message);
			} catch (Exception e) {

			}
		}

		public void next() {
			JSONObject message = new JSONObject();
			try {
				message.put("type", "next");
				sendMessage(message);
			} catch (Exception e) {

			}
		}
	}
}
