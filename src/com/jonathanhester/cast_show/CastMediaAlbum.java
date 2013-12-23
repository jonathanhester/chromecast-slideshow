package com.jonathanhester.cast_show;

import org.json.JSONObject;

public class CastMediaAlbum implements CastableMedia {
	private String mTitle;
	private String mPicasaAlbum;

	/**
	 * Creates a new CastMedia object for the media with the given title and
	 * URL.
	 */
	public CastMediaAlbum(String title, String picasaAlbum) {
		mTitle = title;
		mPicasaAlbum = picasaAlbum;
	}

	@Override
	public void setMessage(JSONObject message) {
		try {
			message.put("picasa", mPicasaAlbum);
		} catch (Exception e) {

		}
	}

	@Override
	public String getTitle() {
		return mTitle;
	}
}
