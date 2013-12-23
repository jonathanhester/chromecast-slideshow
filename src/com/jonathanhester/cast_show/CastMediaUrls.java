package com.jonathanhester.cast_show;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

public class CastMediaUrls implements CastableMedia {
	private String mTitle;
	private ArrayList<String> mImageUrls;

	/**
	 * Creates a new CastMedia object for the media with the given title and
	 * URL.
	 */
	public CastMediaUrls(String title, ArrayList<String> imageUrls) {
		mTitle = title;
		mImageUrls = imageUrls;
	}

	@Override
	public void setMessage(JSONObject message) {
		try {
			message.put("images", new JSONArray(mImageUrls));
		} catch (Exception e) {

		}
	}

	@Override
	public String getTitle() {
		return mTitle;
	}
}
