package com.nemogames;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.gms.appstate.AppStateClient;
import com.google.android.gms.appstate.OnStateLoadedListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.OnInvitationReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateListener;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateListener;
import com.nemogames.GameHelper.GameHelperListener;
import com.unity3d.player.UnityPlayer;

public class PlayServicesAgent implements NemoActivityListener, 
	GameHelperListener, OnStateLoadedListener, OnInvitationReceivedListener, RoomUpdateListener, 
	RealTimeMessageReceivedListener, RoomStatusUpdateListener
{
	private String		ListenerGameObject = "";
	private String		ListenerFunction = "";
	private NemoActivity	RootActivity;
	private GameHelper		Helper;
	private int				ClientsToUse = 0;
	private boolean			ExplicitSignOut = false;
	private boolean			AcceptInvitations_SignIn = false;
	private boolean			AcceptInvitations_Gameplay = false;
	private Invitation		CurrentInvitation;
	
	public final static int		REQUEST_ACHIEVEMENTS = 19880101;
	public final static int		REQUEST_LEADERBOARDS = 19880102;
	public final static int		REQUEST_ERRORDIALOG = 19880103;
	public final static int		REQUEST_INVITATIONINBOX = 19880104;
	public final static int		REQUEST_FRIENDINVITE = 19880105;
	
	public static int		GAMESSCOPE_GAMES = 1;
	public static int		GAMESSCOPE_APPSTATE = 2;
	public static int		GAMESSCOPE_PLUSLOGIN = 4;
	
	public enum	PlayServicesEvent
	{
		OnConnectionFailed(1),
		OnConnectionSuccess(2),
		OnStateLoaded(3),
		OnStateConflict(4),
		OnStateLoadErrorWithData(5),
		OnStateLoadFailed(6),
		OnInvitationReceived(11);
		
		int value;
		PlayServicesEvent(int v) { value = v; }
		public int	getValue() { return value; }
	}
	
	//Region =============================================================== Simple
	public void		init(String gameobject, String function, int clients)
	{
		this.ListenerGameObject = gameobject;
		this.ListenerFunction = function;
		RootActivity = NemoActivity.instance();
		ClientsToUse = 0;
		if ((clients & GAMESSCOPE_GAMES) != 0) ClientsToUse |= GameHelper.CLIENT_GAMES;
		if ((clients & GAMESSCOPE_APPSTATE) != 0) ClientsToUse |= GameHelper.CLIENT_APPSTATE;
		if ((clients & GAMESSCOPE_PLUSLOGIN) != 0) ClientsToUse |= GameHelper.CLIENT_PLUS;
		RootActivity.RegisterActivityListener(this);
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
	public boolean	isConnected() { return Helper.getGamesClient().isConnected(); }
	public boolean	isConnecting() { return Helper.getGamesClient().isConnecting(); }
	public void		Connect()
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			@Override
			public void run() 
			{
				Helper.beginUserInitiatedSignIn();
			}
		});
	}
	public void		Dissconnect() 
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			@Override
			public void run() 
			{
				ExplicitSignOut = true;
				Helper.signOut(); 
			}
		});
	}
	public void		UnlockAchievement(String id) { Helper.getGamesClient().unlockAchievement(id); }
	public void		IncrementAchievement(String id, int step) { Helper.getGamesClient().incrementAchievement(id, step); }
	public void		ShowAchievements()
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			@Override
			public void run() 
			{
				RootActivity.startActivityForResult(Helper.getGamesClient().getAchievementsIntent(), REQUEST_ACHIEVEMENTS);
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
				RootActivity.startActivityForResult(Helper.getGamesClient().getAllLeaderboardsIntent(), REQUEST_LEADERBOARDS);
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
				RootActivity.startActivityForResult(Helper.getGamesClient().getLeaderboardIntent(id), REQUEST_LEADERBOARDS);
			}
		});
	}
	public void		SubmitScore(String id, long value) { Helper.getGamesClient().submitScore(id, value); }
	public void		UpdateStateImmediate(final int iid, final int key, final byte[] data)
	{
		Helper.getAppStateClient().updateStateImmediate(new OnStateLoadedListener() 
		{
			@Override
			public void onStateConflict(int arg0, String arg1, byte[] arg2, byte[] arg3) 
			{
			}
			@Override
			public void onStateLoaded(int arg0, int arg1, byte[] arg2) 
			{
			}
		}, key, data);
	}
	public void		UpdateState(final int key, final byte[] data)
	{
		Helper.getAppStateClient().updateState(key, data);
	}
	public void		LoadState(final int iid, final int key)
	{
		Helper.getAppStateClient().loadState(this, key);
	}
	public void		ResolveStateConflict(int stateKey, String resolvedVersion, byte[] resolvedData)
	{

		Helper.getAppStateClient().resolveState(this, stateKey, resolvedVersion, resolvedData);
	}
	//EndRegion Simple

	//Region =============================================================== Multiplayer
	public void		SetAcceptInvitatins(boolean state)
	{
		AcceptInvitations_SignIn = state;
		AcceptInvitations_Gameplay = state;
		if (AcceptInvitations_Gameplay)
			Helper.getGamesClient().registerInvitationListener(this);
		else
			Helper.getGamesClient().unregisterInvitationListener();
	}
	public String	GetInvitationInvitorName() { return this.CurrentInvitation.getInviter().getDisplayName(); }
	public String	GetInvitationInvitorAddress() { return this.CurrentInvitation.getInviter().getClientAddress(); }
	public String	GetInvitationInvitorIconURI() { return this.CurrentInvitation.getInviter().getIconImageUri().getPath(); }
	public String	GetInvitationInvitorHiResImageURI() { return this.CurrentInvitation.getInviter().getHiResImageUri().getPath(); }
	public void		AcceptInvitation()
	{
		this.JoinRoomWithInvitationID(this.CurrentInvitation.getInvitationId());
	}
	public void		ShowInvitationInbox()
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			@Override
			public void run() 
			{
				Intent inbox = Helper.getGamesClient().getInvitationInboxIntent();
				RootActivity.startActivityForResult(inbox, PlayServicesAgent.REQUEST_INVITATIONINBOX);
			}
		});
	}
	public void		InviteFriends(final int minPlayers, final int maxPlayers)
	{
		RootActivity.runOnUiThread(new Runnable()
		{
			@Override
			public void run() 
			{
				Intent intent = Helper.getGamesClient().getSelectPlayersIntent(minPlayers, maxPlayers);
				RootActivity.startActivityForResult(intent, PlayServicesAgent.REQUEST_FRIENDINVITE);
			}
		});
	}
	
	private void	JoinRoomWithInvitationID(String invitationId)
	{
		RoomConfig.Builder room = this.CreateBasicRoom();
		room.setInvitationIdToAccept(invitationId);
		Helper.getGamesClient().joinRoom(room.build());
		RootActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	//EndRegion =============================================================== Multiplayer

	//Region =============================================================== Private
	private void	SendUnity3DServicesEvent(PlayServicesEvent e)
	{
		try
		{
			JSONObject obj = new JSONObject();
			obj.put("eid", e.getValue());
			UnityPlayer.UnitySendMessage(ListenerGameObject, ListenerFunction, obj.toString());
		} catch (JSONException er) { er.printStackTrace(); }
	}
	private void	SendUnity3DStateConflict(int stateKey, String ver, byte[] localData, byte[] serverData)
	{
		try
		{
			JSONObject obj = new JSONObject();
			obj.put("eid", PlayServicesEvent.OnStateConflict.getValue());
			obj.put("key", stateKey);
			obj.put("version", ver);
			obj.put("localData", localData);
			obj.put("serverData", serverData);
			UnityPlayer.UnitySendMessage(ListenerGameObject, ListenerFunction, obj.toString());
		} catch (JSONException er) { er.printStackTrace(); }
	}
	private void	SendUnity3DStateLoaded(int stateKey, byte[] data)
	{
		try
		{
			JSONObject obj = new JSONObject();
			obj.put("eid", PlayServicesEvent.OnStateLoaded.getValue());
			obj.put("key", stateKey);
			obj.put("data", data);
			UnityPlayer.UnitySendMessage(ListenerGameObject, ListenerFunction, obj.toString());
		} catch (JSONException er) { er.printStackTrace(); }
	}
	private void	SendUnity3DCachedState(int stateKey, byte[] data)
	{
		try
		{
			JSONObject obj = new JSONObject();
			obj.put("eid", PlayServicesEvent.OnStateLoadErrorWithData.getValue());
			obj.put("key", stateKey);
			obj.put("data", data);
			UnityPlayer.UnitySendMessage(ListenerGameObject, ListenerFunction, obj.toString());
		} catch (JSONException er) { er.printStackTrace(); }
	}
	private void	SendUnity3DStateLoadError(int statusCode, int stateKey)
	{
		try
		{
			JSONObject obj = new JSONObject();
			obj.put("eid", PlayServicesEvent.OnStateLoadFailed.getValue());
			obj.put("key", stateKey);
			obj.put("error", statusCode);
			UnityPlayer.UnitySendMessage(ListenerGameObject, ListenerFunction, obj.toString());
		} catch (JSONException er) { er.printStackTrace(); }
	}
	//EndRegion =============================================================== Private


	//Region =============================================================== Nemo Activity
	@Override
	public void onRegistered(Bundle savedInstanceState) 
	{
		Helper = new GameHelper(RootActivity);
		Helper.setup(this, this.ClientsToUse);
	}
	@Override
	public void onRestart() { }
	@Override
	public void onStart() 
	{
		Helper.onStart(RootActivity);
		if (!ExplicitSignOut)
		{
			Helper.getGamesClient().connect();
			if ((ClientsToUse & GAMESSCOPE_APPSTATE) != 0) Helper.getAppStateClient().connect();
		}
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
		if (requestCode == PlayServicesAgent.REQUEST_INVITATIONINBOX)
		{
			if (resultCode == Activity.RESULT_OK)
			{
				Bundle extras = data.getExtras();
				Invitation inv = extras.getParcelable(GamesClient.EXTRA_INVITATION);
				this.JoinRoomWithInvitationID(inv.getInvitationId());
			} Log.d("Nemo - PlayServices", "Invitation canceled");
		} else if (requestCode == REQUEST_FRIENDINVITE)
		{
			
		} else
			Helper.onActivityResult(requestCode, resultCode, data);
	}
	@Override
	public void onSaveInstanceState(Bundle outState) { }
	//EndRegion =============================================================== Nemo Activity
	//Region =============================================================== Client Connection
	@Override
	public void onSignInFailed() 
	{
		this.SendUnity3DServicesEvent(PlayServicesEvent.OnConnectionFailed);
	}

	@Override
	public void onSignInSucceeded() 
	{
		this.SendUnity3DServicesEvent(PlayServicesEvent.OnConnectionSuccess);
		if (Helper.getInvitationId() != null && this.AcceptInvitations_SignIn)
		{
			this.JoinRoomWithInvitationID(Helper.getInvitationId());
		}
	}

	private RoomConfig.Builder	CreateBasicRoom()
	{
		return RoomConfig.builder(this).setMessageReceivedListener(this).setRoomStatusUpdateListener(this);
	}
	//EndRegion =============================================================== Client Connection
	//Region =============================================================== Cloud Save
	@Override
	public void onStateConflict(int stateKey, String ver, byte[] localData, byte[] serverData) 
	{
		PlayServicesAgent.this.SendUnity3DStateConflict(stateKey, ver, localData, serverData);
	}
	@Override
	public void onStateLoaded(int statusCode, int stateKey, byte[] data) 
	{
		if (statusCode == AppStateClient.STATUS_OK)
			PlayServicesAgent.this.SendUnity3DStateLoaded(stateKey, data);
		else if (statusCode == AppStateClient.STATUS_NETWORK_ERROR_STALE_DATA)
			PlayServicesAgent.this.SendUnity3DCachedState(stateKey, data);
		else
			PlayServicesAgent.this.SendUnity3DStateLoadError(statusCode, stateKey);
	}
	//EndRegion =============================================================== Cloud Save
	//Region =============================================================== Multiplayer
	@Override
	public void onInvitationReceived(Invitation inv) 
	{
		this.CurrentInvitation = inv;
		this.SendUnity3DServicesEvent(PlayServicesEvent.OnInvitationReceived);
	}
	@Override
	public void onJoinedRoom(int arg0, Room arg1) 
	{
		
	}
	@Override
	public void onLeftRoom(int arg0, String arg1) 
	{
	
	}
	@Override
	public void onRoomConnected(int arg0, Room arg1) 
	{
	
	}
	@Override
	public void onRoomCreated(int arg0, Room arg1) 
	{
		
	}
	@Override
	public void onRealTimeMessageReceived(RealTimeMessage arg0) 
	{
		
	}
	@Override
	public void onConnectedToRoom(Room arg0) 
	{
		
	}
	@Override
	public void onDisconnectedFromRoom(Room arg0) 
	{
		
	}
	@Override
	public void onPeerDeclined(Room arg0, List<String> arg1)
	{
		
	}
	@Override
	public void onPeerInvitedToRoom(Room arg0, List<String> arg1) 
	{
		
	}
	@Override
	public void onPeerJoined(Room arg0, List<String> arg1)
	{
		
	}
	@Override
	public void onPeerLeft(Room arg0, List<String> arg1) 
	{
		
	}
	@Override
	public void onPeersConnected(Room arg0, List<String> arg1) 
	{
		
	}
	@Override
	public void onPeersDisconnected(Room arg0, List<String> arg1) 
	{
		
	}
	@Override
	public void onRoomAutoMatching(Room arg0) 
	{
		
	}
	@Override
	public void onRoomConnecting(Room arg0) 
	{
		
	}
	//EndRegion =============================================================== Multiplayer
}
