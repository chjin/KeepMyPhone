package com.hackfair.keepmyphone;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class HomeActivity extends AppCompatActivity
                          implements NodeApi.NodeListener,
                                    GoogleApiClient.ConnectionCallbacks,
                                    GoogleApiClient.OnConnectionFailedListener {

    TextView mStatusMsg;
    ImageView mStatusImg;

    private GoogleApiClient mGoogleApiClient;
    private String TAG = this.getClass().getSimpleName();

    DevicePolicyManager mDPM;
    private ComponentName mPolicyAdmin;
    TextView mTextview;
    Button mButton;

    private static final int REQ_ACTIVATE_DEVICE_ADMIN = 10;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mTextview = (TextView) findViewById(R.id.textView);
        mButton = (Button) findViewById(R.id.button);
        mStatusImg = (ImageView) findViewById(R.id.statusImg);
        mStatusMsg = (TextView) findViewById(R.id.statusMsg);

        // get a handle to the device manager
        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mPolicyAdmin = new ComponentName(this, DeviceAdmin.class);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        // in case it was set to invisible before
        mTextview.setVisibility(View.VISIBLE);
        mButton.setVisibility(View.VISIBLE);

        boolean granted = mDPM.isAdminActive(mPolicyAdmin);

        // if not granted, just show the text and button to ask for the grant
        if (granted) {
            // should we tell user to set password
            // first check if lock enabled
            try {
                int currentquality = mDPM.getPasswordQuality(mPolicyAdmin);
                mDPM.setPasswordQuality(mPolicyAdmin, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
                if (mDPM.isActivePasswordSufficient()) {  // already have password enabled

                    // now check if the length is long enough
                    ContentResolver mResolver = this.getContentResolver();
//            Settings.Secure.putInt(mResolver, "lock_screen_lock_after_timeout", 1800000);
                    long timeout = Settings.Secure.getInt(mResolver,
                            "lock_screen_lock_after_timeout",
                            0);
                    if (timeout >= 600000) {  // more than 10 min, no need to show anything
                        mTextview.setVisibility(View.INVISIBLE);
                        mButton.setVisibility(View.INVISIBLE);
                    } else {   // warn to make the timeout longer
                        mTextview.setText("Your phone auto locks after " + Double.toString(timeout / 60000.0) + " minutes. " + getResources().getString(R.string.warn_longer_timeout));
                        mButton.setText(getResources().getString(R.string.change_setting));
                    }
                } else {  // warn to set password
                    mTextview.setText(getResources().getString(R.string.warn_set_password));
                    mButton.setText(getResources().getString(R.string.change_setting));
                }
                // restore original quality policy
                mDPM.setPasswordQuality(mPolicyAdmin, currentquality);
            } catch (Exception e) {
                Log.d(TAG, "Exception: " + e);
            }
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void markPhoneStatus(boolean visible) {
        if (visible) {
            mStatusMsg.setText(getResources().getString(R.string.watch_inrange));
            mStatusImg.setImageResource(R.drawable.inrange);
        } else {
            mStatusMsg.setText(getResources().getString(R.string.watch_outofrange));
            mStatusImg.setImageResource(R.drawable.outofrange);
        }
    }
    //node API callback
    @Override
    public void onPeerConnected(Node node) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                markPhoneStatus(true);
            }
        });
    }

    //node API callback
    @Override
    public void onPeerDisconnected(Node node) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                markPhoneStatus(false);
            }
        });
    }


    // Google API callbacks
    @Override
    public void onConnected(Bundle bundle) {
        // check if already there first
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        if (getConnectedNodesResult.getNodes().size() == 0) {  // no nodes connected,
                            markPhoneStatus(false);
                        } else
                            markPhoneStatus(true);
                    }
                }
        );

        Wearable.NodeApi.addListener(mGoogleApiClient, this);
    }

    // Google API callbacks
    @Override
    public void onConnectionSuspended(int i) {
        Wearable.NodeApi.removeListener(mGoogleApiClient, this);
    }

    //oogleApiClient.OnConnectionFailedListener callbacks
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Failed to connect to Google Api Client with error code "
                + connectionResult.getErrorCode());
    }

    @Override
    protected void onDestroy() {
        // clean up messaging API
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

        super.onDestroy();
    }

    public void onGrant(View v) {

        // check if user has granted us permission
        if (mDPM.isAdminActive(mPolicyAdmin)) { // already granted, must for opening the settings page
            // Launch the activity to have the user enable our admin.
            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            startActivity(intent);
        } else {

            // Launch the activity to have the user enable our admin.
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mPolicyAdmin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getResources().getString(R.string.policy_request_desc));
            //       startActivityForResult(intent, REQ_ACTIVATE_DEVICE_ADMIN);
            startActivity(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_ACTIVATE_DEVICE_ADMIN && resultCode == RESULT_OK) {
            // User just activated the application as a device administrator.
            // setScreenContent(mCurrentScreenId);
            Log.i("haha", "we are here");
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


}





























