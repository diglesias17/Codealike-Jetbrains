package com.codealike.client.core.internal.services;

import java.security.KeyManagementException;
import java.util.Observable;

/*import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;*/

import com.codealike.client.core.api.ApiClient;
import com.codealike.client.core.api.ApiResponse;
import com.codealike.client.core.internal.dto.ProfileInfo;
import com.codealike.client.core.internal.dto.UserConfigurationInfo;
import com.codealike.client.core.internal.model.Profile;
import com.codealike.client.core.internal.model.TrackActivity;
import com.codealike.client.core.internal.startup.PluginContext;
import com.codealike.client.core.internal.utils.LogManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
//import com.codealike.client.core.internal.utils.WorkbenchUtils;

public class IdentityService extends Observable {
	
	private static IdentityService _instance;
	private boolean isAuthenticated;
	private String identity;
	private String token;
	private Profile profile;
	private boolean credentialsStored;
	private TrackActivity trackActivities;
	
	public static IdentityService getInstance() {
		if (_instance == null) {
			_instance = new IdentityService();
		}
		
		return _instance;
	}
	
	public IdentityService() {
		this.identity = "";
		this.isAuthenticated = false;
		this.credentialsStored = false;
		this.token = "";
	}

	public boolean isAuthenticated() {
		return isAuthenticated;
	}

	public boolean login(String identity, String token, boolean storeCredentials, boolean rememberMe) {
		Notification note = new Notification("CodealikeApplicationComponent.Notifications",
				"Codealike",
				"Codealike  is connecting...",
				NotificationType.INFORMATION);
		Notifications.Bus.notify(note);

		if (this.isAuthenticated) {
			setChanged();
			notifyObservers();
			return true;
		}
		try {
			ApiClient apiClient = ApiClient.tryCreateNew(identity, token);
			ApiResponse<Void> response = apiClient.tokenAuthenticate();
			
			if (response.success()) {

				this.identity = identity;
				this.token = token;
				if (storeCredentials) {
					if (rememberMe) {
						storeCredentials(identity, token);
					}
					else {
						removeStoredCredentials();
					}
				}
				
				ApiResponse<ProfileInfo> profileResponse = apiClient.getProfile(identity);
				if (profileResponse.success())
				{
					ProfileInfo profile = profileResponse.getObject();
					this.profile = new Profile(this.identity, profile.getFullName(), profile.getDisplayName(), 
								profile.getAddress(), profile.getState(), profile.getCountry(), profile.getAvatarUri(), profile.getEmail());
				}
				
				ApiResponse<UserConfigurationInfo> configResponse = apiClient.getUserConfiguration(identity);
				if (configResponse.success())
				{
					UserConfigurationInfo config = configResponse.getObject();
					this.trackActivities = config.getTrackActivities();
				}
				this.isAuthenticated = true;
				setChanged();
				notifyObservers();
				return true;
			}
			
		}
		catch (KeyManagementException e){
			LogManager.INSTANCE.logError(e, "Could not log in. There was a problem with SSL configuration.");
		}
		return false;
	}

	private void storeCredentials(String identity, String token) {
		// TODO: check a way to do this in a secure encrypted way
		PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
		propertiesComponent.setValue("codealike.identity", identity);
		propertiesComponent.setValue("codealike.token", token);

        /*ISecurePreferences secureStorage = SecurePreferencesFactory
                .getDefault();
        ISecurePreferences node = secureStorage.node("codealike");
        try {
          node.put("identity", identity, true);
          node.put("token", token, true);
          this.credentialsStored = true;
        } catch (StorageException e) {
        	LogManager.INSTANCE.logError(e, "Could not store credentials.");
        }*/
	}
	
	private void removeStoredCredentials() {
		// TODO: check a way to do this in a secure encrypted way
		PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
		propertiesComponent.unsetValue("codealike.identity");
		propertiesComponent.unsetValue("codealike.token");

        /*ISecurePreferences secureStorage = SecurePreferencesFactory
                .getDefault();
        if (secureStorage.nodeExists("codealike")) {
        	ISecurePreferences node = secureStorage.node("codealike");
        	node.remove("identity");
        	node.remove("token");
        }
        this.credentialsStored = false;*/
	}

	public boolean tryLoginWithStoredCredentials() {
		PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
		String identity = propertiesComponent.getValue("codealike.identity", "");
		String token = propertiesComponent.getValue("codealike.token", "");

		if (identity != "" && token != "")
			return login(identity, token, false, false);

        /*ISecurePreferences secureStorage = SecurePreferencesFactory
                .getDefault();
        if (secureStorage.nodeExists("codealike")) {
            ISecurePreferences node = secureStorage.node("codealike");
            try {
              String identity = node.get("identity", "");
              String token = node.get("token", "");
              return login(identity, token, false, false);
            } catch (StorageException e) {
            	LogManager.INSTANCE.logError(e, "Could not load stored credentials.");
              return false;
            }
        }*/
        return false;
	}

	public String getIdentity() {
		return identity;
	}

	public String getToken() {
		return token;
	}

	public Profile getProfile() {
		return profile;
	}

	public TrackActivity getTrackActivity() {
		return trackActivities;
	}

	public boolean isCredentialsStored() {
		return credentialsStored;
	}

	public void logOff() {
		Notification note = new Notification("CodealikeApplicationComponent.Notifications",
				"Codealike",
				"Codealike  is disconnecting...",
				NotificationType.INFORMATION);
		Notifications.Bus.notify(note);

		PluginContext.getInstance().getTrackingService().flushRecorder(this.identity, this.token);
		
		this.isAuthenticated = false;
		this.identity = null;
		this.token = null;
		removeStoredCredentials();
		
		setChanged();
		notifyObservers();
	}
}
