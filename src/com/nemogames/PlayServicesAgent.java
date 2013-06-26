package com.nemogames;

import java.util.Vector;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;

import com.google.android.gms.appstate.AppStateClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.games.GamesClient;
import com.unity3d.player.UnityPlayer;

public class PlayServicesAgent implements 
			NemoActivityListener,
			GooglePlayServicesClient.ConnectionCallbacks,
			GooglePlayServicesClient.OnConnectionFailedListener
{
	private String		ListenerGameObject = "";
	private String		ListenerFunction = "";
	private NemoActivity	RootActivity;
	private int				RequestCode = 0;
	private boolean			ShowResolution = false;
	private GameHelper		Helper;
	
	public static int		REQUEST_ACHIEVEMENTS = 19880101;
	public static int		REQUEST_LEADERBOARDS = 19880102;
	public static int		REQUEST_ERRORDIALOG = 19880103;
	public static int		REQUEST_RESOLUTIONFORRESULT = 19880104;
	
	public static int		GAMESSCOPE_GAMES = 1;
	public static int		GAMESSCOPE_APPSTATE = 2;
	public static int		GAMESSCOPE_PLUSLOGIN = 4;
	public static int		GAMESSCOPE_PLUSPROFILE = 8;
	
	public enum	PlayServicesEvent
	{
		OnConnectionFailed(1),
		OnConnectionSuccess(2),
		OnDisconnected(3);
		
		int value;
		PlayServicesEvent(int v) { value = v; }
		public int	getValue() { return value; }
	}
	
	public void		init(String gameobject, String function, int clients)
	{
		this.ListenerGameObject = gameobject;
		this.ListenerFunction = function;
		RootActivity = NemoActivity.instance();
		RootActivity.RegisterActivityListener(this);
		
		Vector<String> scopesVector = new Vector<String>();
		if ((clients & GAMESSCOPE_GAMES) != 0) scopesVector.add(Scopes.GAMES);
		if ((clients & GAMESSCOPE_APPSTATE) != 0) scopesVector.add(Scopes.APP_STATE);
		if ((clients & GAMESSCOPE_PLUSLOGIN) != 0) scopesVector.add(Scopes.PLUS_LOGIN);
		if ((clients & GAMESSCOPE_PLUSPROFILE) != 0) scopesVector.add(Scopes.PLUS_PROFILE);
		
		String[] scopes = new String[scopesVector.size()];
        scopesVector.copyInto(scopes);
		
        /*
		Client = new GamesClient.Builder(RootActivity, this, this).
				setScopes(scopes).setGravityForPopups(Gravity.TOP | Gravity.CENTER_HORIZONTAL).create();*/
	}
	
	public boolean		isPlayServicesAvailable(boolean show_error_dialog)
	{
		int result_code = GooglePlayServicesUtil.isGooglePlayServicesAvailable(RootActivity);
		if (result_code == ConnectionResult.SUCCESS)
			return true;
		else if (show_error_dialog)
			GooglePlayServicesUtil.getErrorDialog(result_code, RootActivity, REQUEST_ERRORDIALOG).show();
		return false;
	}

	public boolean	isConnected() { return Client.isConnected(); }
	public boolean	isConnecting() { return Client.isConnecting(); }
	public void		Connect() { ShowResolution = true; Client.connect(); }
	public void		Dissconnect() { Client.signOut(); }
	public void		UnlockAchievement(String id) { Client.unlockAchievement(id); }
	public void		IncrementAchievement(String id, int step) { Client.incrementAchievement(id, step); }
	public void		ShowAchievements()
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			@Override
			public void run() 
			{
				RootActivity.startActivityForResult(Client.getAchievementsIntent(), REQUEST_ACHIEVEMENTS);
			}
		});
	}
	public void		ShowLeaderboards()
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			@Override
			public void run() 
			{
				RootActivity.startActivityForResult(Client.getAllLeaderboardsIntent(), REQUEST_LEADERBOARDS);
			}
		});
	}
	public void		ShowLeaderbord(final String id)
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			@Override
			public void run() 
			{
				RootActivity.startActivityForResult(Client.getLeaderboardIntent(id), REQUEST_LEADERBOARDS);
			}
		});
	}
	public void		SubmitScore(String id, long value) { Client.submitScore(id, value); }
	
	
	//---------- internal
	private void	SendUnity3DServicesEvent(PlayServicesEvent e)
	{
		try
		{
			JSONObject obj = new JSONObject();
			obj.put("eid", e.getValue());
			UnityPlayer.UnitySendMessage(ListenerGameObject, ListenerFunction, obj.toString());
		} catch (JSONException er) { er.printStackTrace(); }
	}
	
	//---------- activity
	@Override
	public void onRegistered(Bundle savedInstanceState) 
	{
		
	}

	@Override
	public void onRestart() { }

	@Override
	public void onStart() 
	{
		ShowResolution = false;
		Client.connect();
	}

	@Override
	public void onStop() { }

	@Override
	public void onPause() { }

	@Override
	public void onResume() { }

	@Override
	public void onDestroy() { }

	@Override
	public void onBackPressed() { }

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		if (requestCode == REQUEST_ERRORDIALOG)
		{
			Log.d("Nemo - PlayServices", "Result code for request Error Dialog: " + resultCode);
		} else if (requestCode == REQUEST_LEADERBOARDS)
		{
			Log.d("Nemo - PlayServices", "Result code for leaderboards: " + resultCode);
		} else if (requestCode == REQUEST_ACHIEVEMENTS)
		{
			Log.d("Nemo - PlayServices", "Result code for achievements: " + resultCode);
		} else if (requestCode == REQUEST_RESOLUTIONFORRESULT)
		{
			Log.d("Nemo - PlayServices", "Result code for request Resolution: " + resultCode);
			if (resultCode == Activity.RESULT_OK)
			{
				this.ShowResolution = false;
				Client.connect();
			} else
			{
				this.SendUnity3DServicesEvent(PlayServicesEvent.OnConnectionFailed);
				Log.e("Nemo - PlayServices", "Error connecting to Play Services: ");
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) { }

	//------------------ client connect
	
	@Override
	public void onConnectionFailed(ConnectionResult result) 
	{
		Log.d("Nemo - PlayServices", "Connection Failed");
		try 
		{
			if (ShowResolution && 
				(result.getErrorCode() == ConnectionResult.SIGN_IN_REQUIRED ||
				 result.getErrorCode() == ConnectionResult.RESOLUTION_REQUIRED))
			{
				ShowResolution = false;
				result.startResolutionForResult(RootActivity, REQUEST_RESOLUTIONFORRESULT);
			} else this.SendUnity3DServicesEvent(PlayServicesEvent.OnConnectionFailed);
		} catch (SendIntentException e) { e.printStackTrace(); }
	}

	@Override
	public void onConnected(Bundle arg0) 
	{
		this.SendUnity3DServicesEvent(PlayServicesEvent.OnConnectionSuccess);
	}

	@Override
	public void onDisconnected() 
	{
		this.SendUnity3DServicesEvent(PlayServicesEvent.OnDisconnected);
	}	
}
