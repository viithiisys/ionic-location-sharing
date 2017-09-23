package plugin.google.maps;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.MapsInitializer;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@SuppressWarnings("deprecation")
public class CordovaGoogleMaps extends CordovaPlugin implements ViewTreeObserver.OnScrollChangedListener{
  private final String TAG = "GoogleMapsPlugin";
  private HashMap<String, Bundle> bufferForLocationDialog = new HashMap<String, Bundle>();

  private final int ACTIVITY_LOCATION_DIALOG = 0x7f999900; // Invite the location dialog using Google Play Services
  private final int ACTIVITY_LOCATION_PAGE = 0x7f999901;   // Open the location settings page

  private Activity activity;
  public ViewGroup root;
  public MyPluginLayout mPluginLayout = null;
  private GoogleApiClient googleApiClient = null;
  public boolean initialized = false;
  public PluginManager pluginManager;
  public static String CURRENT_URL;
  public static final HashMap<String, String> semaphore = new HashMap<String, String>();

  @SuppressLint("NewApi") @Override
  public void initialize(final CordovaInterface cordova, final CordovaWebView webView) {
    super.initialize(cordova, webView);
    if (root != null) {
      return;
    }
    LOG.setLogLevel(LOG.ERROR);

    activity = cordova.getActivity();
    final View view = webView.getView();
    view.getViewTreeObserver().addOnScrollChangedListener(CordovaGoogleMaps.this);
    root = (ViewGroup) view.getParent();

    pluginManager = webView.getPluginManager();

    cordova.getActivity().runOnUiThread(new Runnable() {
      @SuppressLint("NewApi")
      public void run() {
        CURRENT_URL = webView.getUrl();

        // Enable this, webView makes draw cache on the Android action bar issue.
        //View view = webView.getView();
        //if (Build.VERSION.SDK_INT >= 21 || "org.xwalk.core.XWalkView".equals(view.getClass().getName())){
        //  view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        //  Log.d("Layout", "--> view =" + view.isHardwareAccelerated()); //always false
        //}


        // ------------------------------
        // Check of Google Play Services
        // ------------------------------
        int checkGooglePlayServices = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);

        Log.d(TAG, "----> checkGooglePlayServices = " + (ConnectionResult.SUCCESS == checkGooglePlayServices));

        if (checkGooglePlayServices != ConnectionResult.SUCCESS) {
          // google play services is missing!!!!
          /*
           * Returns status code indicating whether there was an error. Can be one
           * of following in ConnectionResult: SUCCESS, SERVICE_MISSING,
           * SERVICE_VERSION_UPDATE_REQUIRED, SERVICE_DISABLED, SERVICE_INVALID.
           */
          Log.e(TAG, "---Google Play Services is not available: " + GooglePlayServicesUtil.getErrorString(checkGooglePlayServices));

          boolean isNeedToUpdate = false;

          String errorMsg = "Google Maps Android API v2 is not available for some reason on this device. Do you install the latest Google Play Services from Google Play Store?";
          switch (checkGooglePlayServices) {
            case ConnectionResult.DEVELOPER_ERROR:
              errorMsg = "The application is misconfigured. This error is not recoverable and will be treated as fatal. The developer should look at the logs after this to determine more actionable information.";
              break;
            case ConnectionResult.INTERNAL_ERROR:
              errorMsg = "An internal error of Google Play Services occurred. Please retry, and it should resolve the problem.";
              break;
            case ConnectionResult.INVALID_ACCOUNT:
              errorMsg = "You attempted to connect to the service with an invalid account name specified.";
              break;
            case ConnectionResult.LICENSE_CHECK_FAILED:
              errorMsg = "The application is not licensed to the user. This error is not recoverable and will be treated as fatal.";
              break;
            case ConnectionResult.NETWORK_ERROR:
              errorMsg = "A network error occurred. Please retry, and it should resolve the problem.";
              break;
            case ConnectionResult.SERVICE_DISABLED:
              errorMsg = "The installed version of Google Play services has been disabled on this device. Please turn on Google Play Services.";
              break;
            case ConnectionResult.SERVICE_INVALID:
              errorMsg = "The version of the Google Play services installed on this device is not authentic. Please update the Google Play Services from Google Play Store.";
              isNeedToUpdate = true;
              break;
            case ConnectionResult.SERVICE_MISSING:
              errorMsg = "Google Play services is missing on this device. Please install the Google Play Services.";
              isNeedToUpdate = true;
              break;
            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
              errorMsg = "The installed version of Google Play services is out of date. Please update the Google Play Services from Google Play Store.";
              isNeedToUpdate = true;
              break;
            case ConnectionResult.SIGN_IN_REQUIRED:
              errorMsg = "You attempted to connect to the service but you are not signed in. Please check the Google Play Services configuration";
              break;
            default:
              isNeedToUpdate = true;
              break;
          }

          final boolean finalIsNeedToUpdate = isNeedToUpdate;
          AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
          alertDialogBuilder
              .setMessage(errorMsg)
              .setCancelable(false)
              .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                  dialog.dismiss();
                  if (finalIsNeedToUpdate) {
                    try {
                      activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms")));
                    } catch (android.content.ActivityNotFoundException anfe) {
                      activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=appPackageName")));
                    }
                  }
                }
              });
          AlertDialog alertDialog = alertDialogBuilder.create();

          // show it
          alertDialog.show();

          Log.e(TAG, "Google Play Services is not available.");
          return;
        }

        webView.getView().setBackgroundColor(Color.TRANSPARENT);
        webView.getView().setOverScrollMode(View.OVER_SCROLL_NEVER);
        mPluginLayout = new MyPluginLayout(webView, activity);
        mPluginLayout.isSuspended = true;


        // Check the API key
        ApplicationInfo appliInfo = null;
        try {
          appliInfo = activity.getPackageManager().getApplicationInfo(activity.getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {}

        String API_KEY = appliInfo.metaData.getString("com.google.android.maps.v2.API_KEY");
        if ("API_KEY_FOR_ANDROID".equals(API_KEY)) {

          AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

          alertDialogBuilder
              .setMessage("Please replace 'API_KEY_FOR_ANDROID' in the platforms/android/AndroidManifest.xml with your API Key!")
              .setCancelable(false)
              .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                  dialog.dismiss();
                }
              });
          AlertDialog alertDialog = alertDialogBuilder.create();

          // show it
          alertDialog.show();
        }

        CURRENT_URL = webView.getUrl();


        //------------------------------
        // Initialize Google Maps SDK
        //------------------------------
        if (!initialized) {
          try {
            MapsInitializer.initialize(cordova.getActivity());
            initialized = true;
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

      }
    });


  }

  @Override
  public boolean onOverrideUrlLoading(String url) {
    mPluginLayout.isSuspended = true;
    /*
    this.activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        webView.loadUrl("javascript:if(window.cordova){cordova.fireDocumentEvent('plugin_url_changed', {});}");
      }
    });
    */
    CURRENT_URL = url;
    return false;
  }


  @Override
  public void onScrollChanged() {
    if (mPluginLayout == null) {
      return;
    }
    View view = webView.getView();
    int scrollX = view.getScrollX();
    int scrollY = view.getScrollY();
    mPluginLayout.scrollTo(scrollX, scrollY);

  }

  @Override
  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

    cordova.getThreadPool().submit(new Runnable() {
      @Override
      public void run() {
        try {
          if (action.equals("putHtmlElements")) {
            CordovaGoogleMaps.this.putHtmlElements(args, callbackContext);
          } else if ("clearHtmlElements".equals(action)) {
            CordovaGoogleMaps.this.clearHtmlElements(args, callbackContext);
          } else if ("pause".equals(action)) {
            CordovaGoogleMaps.this.pause(args, callbackContext);
          } else if ("resume".equals(action)) {
            CordovaGoogleMaps.this.resume(args, callbackContext);
          } else if ("getMyLocation".equals(action)) {
            CordovaGoogleMaps.this.getMyLocation(args, callbackContext);
          } else if ("getMap".equals(action)) {
            CordovaGoogleMaps.this.getMap(args, callbackContext);
          } else if ("removeMap".equals(action)) {
            CordovaGoogleMaps.this.removeMap(args, callbackContext);
          } else if ("backHistory".equals(action)) {
            CordovaGoogleMaps.this.backHistory(args, callbackContext);
          } else if ("resumeResizeTimer".equals(action)) {
            CordovaGoogleMaps.this.resumeResizeTimer(args, callbackContext);
          } else if ("pauseResizeTimer".equals(action)) {
            CordovaGoogleMaps.this.pauseResizeTimer(args, callbackContext);
          }

        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    });
    return true;

  }

  public void resumeResizeTimer(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (mPluginLayout.isWaiting) {
      mPluginLayout.pauseResize = false;
      synchronized (mPluginLayout.timerLock) {
        mPluginLayout.timerLock.notify();
      }
    }
  }
  public void pauseResizeTimer(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    mPluginLayout.pauseResize = true;
  }
  public void backHistory(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!webView.backHistory()) {
          // If no more history back, exit the app
          cordova.getActivity().finish();
        }
      }
    });
  }


  public void onRequestPermissionResult(int requestCode, String[] permissions,
                                        int[] grantResults) throws JSONException {
    synchronized (CordovaGoogleMaps.semaphore) {
      semaphore.notify();
    }
  }

  public void pause(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (mPluginLayout == null) {
      callbackContext.success();
      return;
    }
    mPluginLayout.isSuspended = true;
    callbackContext.success();
  }
  public void resume(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (mPluginLayout == null) {
      callbackContext.success();
      return;
    }
    if (mPluginLayout.isSuspended) {
      mPluginLayout.isSuspended = false;
      synchronized (mPluginLayout.timerLock) {
        mPluginLayout.timerLock.notify();
      }
    }
    callbackContext.success();
  }
  public void clearHtmlElements(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (mPluginLayout == null) {
      callbackContext.success();
      return;
    }
    mPluginLayout.clearHtmlElements();
    callbackContext.success();
  }
  public void putHtmlElements(final JSONArray args, final CallbackContext callbackContext) throws JSONException {

      final JSONObject elements = args.getJSONObject(0);
      if (mPluginLayout == null) {
          callbackContext.success();
          return;
      }

      if (!mPluginLayout.stopFlag || mPluginLayout.needUpdatePosition) {
          mPluginLayout.putHTMLElements(elements);
      }

      callbackContext.success();
  }

  @SuppressWarnings("unused")
  public void getMyLocation(final JSONArray args, final CallbackContext callbackContext) throws JSONException {

    // enableHighAccuracy = true -> PRIORITY_HIGH_ACCURACY
    // enableHighAccuracy = false -> PRIORITY_BALANCED_POWER_ACCURACY

    JSONObject params = args.getJSONObject(0);
    boolean isHighLocal = false;
    if (params.has("enableHighAccuracy")) {
      isHighLocal = params.getBoolean("enableHighAccuracy");
    }
    final boolean isHigh = isHighLocal;

    // Request geolocation permission.
    boolean locationPermission = cordova.hasPermission("android.permission.ACCESS_COARSE_LOCATION");

    if (!locationPermission) {
      //_saveArgs = args;
      //_saveCallbackContext = callbackContext;
      synchronized (semaphore) {
        cordova.requestPermissions(this, callbackContext.hashCode(), new String[]{"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"});
        try {
          semaphore.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      locationPermission = cordova.hasPermission("android.permission.ACCESS_COARSE_LOCATION");

      if (!locationPermission) {
        callbackContext.error("Geolocation permission request was denied.");
        return;
      }
    }

    if (googleApiClient == null) {
      googleApiClient = new GoogleApiClient.Builder(activity)
        .addApi(LocationServices.API)
        .addConnectionCallbacks(new com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks() {

          @Override
          public void onConnected(Bundle connectionHint) {
            Log.e(TAG, "===> onConnected");
            CordovaGoogleMaps.this.sendNoResult(callbackContext);

            _checkLocationSettings(isHigh, callbackContext);
          }

          @Override
          public void onConnectionSuspended(int cause) {
            Log.e(TAG, "===> onConnectionSuspended");
          }

        })
        .addOnConnectionFailedListener(new com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener() {

          @Override
          public void onConnectionFailed(@NonNull ConnectionResult result) {
            Log.e(TAG, "===> onConnectionFailed");

            PluginResult tmpResult = new PluginResult(PluginResult.Status.ERROR, result.toString());
            tmpResult.setKeepCallback(false);
            callbackContext.sendPluginResult(tmpResult);

            googleApiClient.disconnect();
          }

        })
        .build();
      googleApiClient.connect();
    } else if (googleApiClient.isConnected()) {
      _checkLocationSettings(isHigh, callbackContext);
    }
  }

  private void _checkLocationSettings(final boolean enableHighAccuracy, final CallbackContext callbackContext) {

    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().setAlwaysShow(true);

    LocationRequest locationRequest;
    locationRequest = LocationRequest.create()
        .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    builder.addLocationRequest(locationRequest);

    if (enableHighAccuracy) {
      locationRequest = LocationRequest.create()
          .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
      builder.addLocationRequest(locationRequest);
    }

    PendingResult<LocationSettingsResult> locationSettingsResult =
        LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());

    locationSettingsResult.setResultCallback(new ResultCallback<LocationSettingsResult>() {

      @Override
      public void onResult(@NonNull LocationSettingsResult result) {
        final Status status = result.getStatus();
        switch (status.getStatusCode()) {
          case LocationSettingsStatusCodes.SUCCESS:
            _requestLocationUpdate(false, enableHighAccuracy, callbackContext);
            break;

          case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
            // Location settings are not satisfied. But could be fixed by showing the user
            // a dialog.
            try {
              //Keep the callback id
              Bundle bundle = new Bundle();
              bundle.putInt("type", ACTIVITY_LOCATION_DIALOG);
              bundle.putString("callbackId", callbackContext.getCallbackId());
              bundle.putBoolean("enableHighAccuracy", enableHighAccuracy);
              int hashCode = bundle.hashCode();

              bufferForLocationDialog.put("bundle_" + hashCode, bundle);
              CordovaGoogleMaps.this.sendNoResult(callbackContext);

              // Show the dialog by calling startResolutionForResult(),
              // and check the result in onActivityResult().
              cordova.setActivityResultCallback(CordovaGoogleMaps.this);
              status.startResolutionForResult(cordova.getActivity(), hashCode);
            } catch (SendIntentException e) {
              // Show the dialog that is original version of this plugin.
              _showLocationSettingsPage(enableHighAccuracy, callbackContext);
            }
            break;

          case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
            // Location settings are not satisfied. However, we have no way to fix the
            // settings so we won't show the dialog.

            JSONObject jsResult = new JSONObject();
            try {
              jsResult.put("status", false);
              jsResult.put("error_code", "service_not_available");
              jsResult.put("error_message", "This app has been rejected to use Location Services.");
            } catch (JSONException e) {
              e.printStackTrace();
            }
            callbackContext.error(jsResult);
            break;
        }
      }

    });
  }

  private void _showLocationSettingsPage(final boolean enableHighAccuracy, final CallbackContext callbackContext) {
    //Ask the user to turn on the location services.
    AlertDialog.Builder builder = new AlertDialog.Builder(this.activity);
    builder.setTitle("Improve location accuracy");
    builder.setMessage("To enhance your Maps experience:\n\n" +
        " - Enable Google apps location access\n\n" +
        " - Turn on GPS and mobile network location");
    builder.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          //Keep the callback id
          Bundle bundle = new Bundle();
          bundle.putInt("type", ACTIVITY_LOCATION_PAGE);
          bundle.putString("callbackId", callbackContext.getCallbackId());
          bundle.putBoolean("enableHighAccuracy", enableHighAccuracy);
          int hashCode = bundle.hashCode();

          bufferForLocationDialog.put("bundle_" + hashCode, bundle);
          CordovaGoogleMaps.this.sendNoResult(callbackContext);

          //Launch settings, allowing user to make a change
          cordova.setActivityResultCallback(CordovaGoogleMaps.this);
          Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
          activity.startActivityForResult(intent, hashCode);
        }
    });
    builder.setNegativeButton("Skip", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          //No location service, no Activity
          dialog.dismiss();

          JSONObject result = new JSONObject();
          try {
            result.put("status", false);
            result.put("error_code", "service_denied");
            result.put("error_message", "This app has been rejected to use Location Services.");
          } catch (JSONException e) {
            e.printStackTrace();
          }
          callbackContext.error(result);
        }
    });
    builder.create().show();
  }

  @SuppressWarnings("MissingPermission")
  private void _requestLocationUpdate(final boolean isRetry, final boolean enableHighAccuracy, final CallbackContext callbackContext) {

    int priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
    if (enableHighAccuracy) {
      priority = LocationRequest.PRIORITY_HIGH_ACCURACY;
    }

    LocationRequest locationRequest= LocationRequest.create()
        .setExpirationTime(5000)
        .setNumUpdates(2)
        .setSmallestDisplacement(0)
        .setPriority(priority)
        .setInterval(5000);


    final PendingResult<Status> result =  LocationServices.FusedLocationApi.requestLocationUpdates(
        googleApiClient, locationRequest, new LocationListener() {

          @Override
          public void onLocationChanged(Location location) {
            /*
            if (callbackContext.isFinished()) {
              return;
            }
            */
            JSONObject result;
            try {
              result = PluginUtil.location2Json(location);
              result.put("status", true);
              callbackContext.success(result);
            } catch (JSONException e) {
              e.printStackTrace();
            }

            googleApiClient.disconnect();
          }

        });

    result.setResultCallback(new ResultCallback<Status>() {

      public void onResult(Status status) {
        if (!status.isSuccess()) {
          String errorMsg = status.getStatusMessage();
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, errorMsg);
          callbackContext.sendPluginResult(result);
        } else {
          // no update location
          Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
          if (location != null) {
            try {
              JSONObject result = PluginUtil.location2Json(location);
              result.put("status", true);
              callbackContext.success(result);
            } catch (JSONException e) {
              e.printStackTrace();
            }
          } else {
            if (!isRetry) {
              Toast.makeText(activity, "Waiting for location...", Toast.LENGTH_SHORT).show();

              CordovaGoogleMaps.this.sendNoResult(callbackContext);

              // Retry
              Handler handler = new Handler();
              handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                  _requestLocationUpdate(true, enableHighAccuracy, callbackContext);
                }
              }, 3000);
            } else {
              // Send back the error result
              JSONObject result = new JSONObject();
              try {
                result.put("status", false);
                result.put("error_code", "cannot_detect");
                result.put("error_message", "Can not detect your location. Try again.");
              } catch (JSONException e) {
                e.printStackTrace();
              }
              callbackContext.error(result);
            }
          }
        }
      }
    });
  }

  @Override
  public void onReset() {
    super.onReset();
    if (mPluginLayout == null || mPluginLayout.pluginMaps == null) {
      return;
    }

    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        CURRENT_URL = webView.getUrl();

        mPluginLayout.setBackgroundColor(Color.WHITE);

        Set<String> mapIds = mPluginLayout.pluginMaps.keySet();
        PluginMap pluginMap;

        // prevent the ConcurrentModificationException error.
        String[] mapIdArray= mapIds.toArray(new String[mapIds.size()]);
        for (String mapId : mapIdArray) {
          if (mPluginLayout.pluginMaps.containsKey(mapId)) {
            pluginMap = mPluginLayout.removePluginMap(mapId);
            pluginMap.remove(null, null);
            pluginMap.onDestroy();
            mPluginLayout.HTMLNodes.remove(mapId);
          }
        }
        mPluginLayout.HTMLNodes.clear();
        mPluginLayout.pluginMaps.clear();

        System.gc();
        Runtime.getRuntime().gc();
      }
    });

  }

  public void removeMap(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String mapId = args.getString(0);
    if (mPluginLayout.pluginMaps.containsKey(mapId)) {
      PluginMap pluginMap = mPluginLayout.removePluginMap(mapId);
      if (pluginMap != null) {
        pluginMap.remove(null, null);
        pluginMap.onDestroy();
        pluginMap.objects.clear();
        mPluginLayout.HTMLNodes.remove(mapId);
        pluginMap = null;
      }

      try {
        Field pluginMapField = pluginManager.getClass().getDeclaredField("pluginMap");
        pluginMapField.setAccessible(true);
        LinkedHashMap<String, CordovaPlugin> pluginMapInstance = (LinkedHashMap<String, CordovaPlugin>) pluginMapField.get(pluginManager);
        pluginMapInstance.remove(mapId);
        Field entryMapField = pluginManager.getClass().getDeclaredField("entryMap");
        entryMapField.setAccessible(true);
        LinkedHashMap<String, PluginEntry> entryMapInstance = (LinkedHashMap<String, PluginEntry>) entryMapField.get(pluginManager);
        entryMapInstance.remove(mapId);
      } catch (Exception e) {
        e.printStackTrace();
      }


    }



    System.gc();
    Runtime.getRuntime().gc();
    callbackContext.success();
  }
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public void getMap(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    //------------------------------------------
    // Create an instance of PluginMap class.
    //------------------------------------------
    String mapId = args.getString(0);
    PluginMap pluginMap = new PluginMap();
    pluginMap.privateInitialize(mapId, cordova, webView, null);
    pluginMap.initialize(cordova, webView);
    pluginMap.mapCtrl = CordovaGoogleMaps.this;
    pluginMap.self = pluginMap;
    ((MyPlugin)pluginMap).CURRENT_PAGE_URL = CURRENT_URL;

    PluginEntry pluginEntry = new PluginEntry(mapId, pluginMap);
    pluginManager.addService(pluginEntry);

    if (mPluginLayout.isSuspended) {
      mPluginLayout.isSuspended = false;
      synchronized (mPluginLayout.timerLock) {
        mPluginLayout.timerLock.notify();
      }
    }

    pluginMap.getMap(args, callbackContext);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (!bufferForLocationDialog.containsKey("bundle_" + requestCode)) {
      Log.e(TAG, "no key");
      return;
    }
    Bundle query = bufferForLocationDialog.get("bundle_" + requestCode);
    Log.d(TAG, "====> onActivityResult (" + resultCode + ")");

    switch (query.getInt("type")) {
      case ACTIVITY_LOCATION_DIALOG:
        // User was asked to enable the location setting.
        switch (resultCode) {
          case Activity.RESULT_OK:
            // All required changes were successfully made
            _inviteLocationUpdateAfterActivityResult(query);
            break;
          case Activity.RESULT_CANCELED:
              // The user was asked to change settings, but chose not to
            _userRefusedToUseLocationAfterActivityResult(query);
            break;
          default:
            break;
        }
        break;
      case ACTIVITY_LOCATION_PAGE:
        _onActivityResultLocationPage(query);
        break;
    }
  }
  private void _onActivityResultLocationPage(Bundle bundle) {
    String callbackId = bundle.getString("callbackId");
    CallbackContext callbackContext = new CallbackContext(callbackId, this.webView);

    LocationManager locationManager = (LocationManager) this.activity.getSystemService(Context.LOCATION_SERVICE);
    List<String> providers = locationManager.getAllProviders();
    int availableProviders = 0;
    if (mPluginLayout != null && mPluginLayout.isDebug) {
      Log.d(TAG, "---debug at getMyLocation(available providers)--");
    }
    Iterator<String> iterator = providers.iterator();
    String provider;
    boolean isAvailable;
    while(iterator.hasNext()) {
      provider = iterator.next();
      isAvailable = locationManager.isProviderEnabled(provider);
      if (isAvailable) {
        availableProviders++;
      }
      if (mPluginLayout != null && mPluginLayout.isDebug) {
        Log.d(TAG, "   " + provider + " = " + (isAvailable ? "" : "not ") + "available");
      }
    }
    if (availableProviders == 0) {
      JSONObject result = new JSONObject();
      try {
        result.put("status", false);
        result.put("error_code", "not_available");
        result.put("error_message", "Since this device does not have any location provider, this app can not detect your location.");
      } catch (JSONException e) {
        e.printStackTrace();
      }
      callbackContext.error(result);
      return;
    }

    _inviteLocationUpdateAfterActivityResult(bundle);
  }

  private void _inviteLocationUpdateAfterActivityResult(Bundle bundle) {
    boolean enableHighAccuracy = bundle.getBoolean("enableHighAccuracy");
    String callbackId = bundle.getString("callbackId");
    CallbackContext callbackContext = new CallbackContext(callbackId, this.webView);
    this._requestLocationUpdate(false, enableHighAccuracy, callbackContext);
  }

  private void _userRefusedToUseLocationAfterActivityResult(Bundle bundle) {
    String callbackId = bundle.getString("callbackId");
    CallbackContext callbackContext = new CallbackContext(callbackId, this.webView);
    JSONObject result = new JSONObject();
    try {
      result.put("status", false);
      result.put("error_code", "service_denied");
      result.put("error_message", "This app has been rejected to use Location Services.");
    } catch (JSONException e) {
      e.printStackTrace();
    }
    callbackContext.error(result);
  }

  @Override
  public void onPause(boolean multitasking) {
    super.onPause(multitasking);
    cordova.getThreadPool().submit(new Runnable() {
      @Override
      public void run() {

        Set<String> mapIds = mPluginLayout.pluginMaps.keySet();
        PluginMap pluginMap;

        // prevent the ConcurrentModificationException error.
        String[] mapIdArray= mapIds.toArray(new String[mapIds.size()]);
        for (String mapId : mapIdArray) {
          if (mPluginLayout.pluginMaps.containsKey(mapId)) {
            pluginMap = mPluginLayout.pluginMaps.get(mapId);
            pluginMap.mapView.onPause();
          }
        }
      }
    });
  }

  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);
    if (mPluginLayout != null) {
      mPluginLayout.isSuspended = false;

      if (mPluginLayout.pluginMaps.size() > 0) {
        this.activity.runOnUiThread(new Runnable() {
          @Override
          public void run() {
            CURRENT_URL = webView.getUrl();
            webView.loadUrl("javascript:if(window.cordova){cordova.fireDocumentEvent('plugin_touch', {});}");
          }
        });
      }
    }

    cordova.getThreadPool().submit(new Runnable() {
      @Override
      public void run() {

        Set<String> mapIds = mPluginLayout.pluginMaps.keySet();
        PluginMap pluginMap;

        // prevent the ConcurrentModificationException error.
        String[] mapIdArray= mapIds.toArray(new String[mapIds.size()]);
        for (String mapId : mapIdArray) {
          if (mPluginLayout.pluginMaps.containsKey(mapId)) {
            pluginMap = mPluginLayout.pluginMaps.get(mapId);
            pluginMap.mapView.onResume();
          }
        }
      }
    });
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    cordova.getThreadPool().submit(new Runnable() {
      @Override
      public void run() {

        Set<String> mapIds = mPluginLayout.pluginMaps.keySet();
        PluginMap pluginMap;

        // prevent the ConcurrentModificationException error.
        String[] mapIdArray= mapIds.toArray(new String[mapIds.size()]);
        for (String mapId : mapIdArray) {
          if (mPluginLayout.pluginMaps.containsKey(mapId)) {
            pluginMap = mPluginLayout.pluginMaps.get(mapId);
            pluginMap.mapView.onDestroy();
          }
        }
      }
    });
  }

  protected void sendNoResult(CallbackContext callbackContext) {
    PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
    pluginResult.setKeepCallback(true);
    callbackContext.sendPluginResult(pluginResult);
  }

 /**
   * Called by the system when the device configuration changes while your activity is running.
   *
   * @param newConfig		The new device configuration
   */
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        PluginMap pluginMap;
        Collection<PluginEntry> collection =  pluginManager.getPluginEntries();
        for (PluginEntry entry: collection) {
          if ("plugin.google.maps.PluginMap".equals(entry.pluginClass) && entry.plugin != null) {
            pluginMap = (PluginMap)entry.plugin;
            if (pluginMap.map != null) {

              // Trigger the CAMERA_MOVE_END mandatory
              pluginMap.onCameraIdle();
            }
          }
        }
      }
    }, 500);

    /*
    // Checks the orientation of the screen
    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      Toast.makeText(activity, "landscape", Toast.LENGTH_SHORT).show();
    } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
      Toast.makeText(activity, "portrait", Toast.LENGTH_SHORT).show();
    }
    */
  }

}
