package com.shwethasp.googleappengine;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.util.Vector;

public class MainActivity extends AppCompatActivity {

    private AlertDialog accountDialog;
    private static final int ACCOUNT_RESULT = 2;
    private AccountManager accountManager;
    private Account[] accounts;
    private int mAccountPosition;
    private DefaultHttpClient http_client = new DefaultHttpClient();
    private TextView mTextViewResponse;
    private boolean isAuthToken = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextViewResponse = (TextView) findViewById(R.id.text);
        showSelectAccountDialog();
    }

    /**
     * Dialog to display the list of Google accounts from the device
     */
    private void showSelectAccountDialog() {

        LinearLayout LL = new LinearLayout(this);
        Button selectAccBtn = new Button(this);

        selectAccBtn.setText("Select Account");
        LinearLayout.LayoutParams llParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llParams.setMargins(10, 5, 10, 10);
        LL.addView(selectAccBtn, llParams);
        LL.setOrientation(LinearLayout.VERTICAL);

        final AlertDialog.Builder ssidDialogBuilder = new AlertDialog.Builder(this);
        ssidDialogBuilder.setTitle("Google Account").setCancelable(false).setView(LL);

        selectAccBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accountManager = AccountManager.get(getApplicationContext());

                // To get only the google accounts
                accounts = accountManager.getAccountsByType("com.google");

                Vector<String> accountVec = new Vector<String>();
                for (int i = 0; i < accounts.length; i++) {
                    accountVec.add(accounts[i].name.toString());
                }

                if (accountVec.size() != 0) {
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                    alertDialog.setTitle("Accounts");
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            MainActivity.this, android.R.layout.simple_list_item_1,
                            accountVec);

                    alertDialog.setAdapter(adapter,
                            new DialogInterface.OnClickListener() {

                                @SuppressWarnings("deprecation")
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    mAccountPosition = which;

                                    // Get the AuthToken for the selected account
                                    accountManager.getAuthToken(accounts[mAccountPosition], "ah", false, new GetAuthTokenCallback(), null);
                                    if (accountDialog != null)
                                        if (accountDialog.isShowing())
                                            accountDialog.dismiss();

                                }
                            });

                    alertDialog.show();
                } else {
                    Toast.makeText(getApplicationContext(), "No Google accounts found. Please add an account in your phone", Toast.LENGTH_SHORT).show();
                }
            }
        });

        accountDialog = ssidDialogBuilder.create();
        accountDialog.show();
    }

    /**
     * Asynctask class to get authorization token for google account
     */
    private class GetAuthTokenCallback implements AccountManagerCallback<Bundle> {
        public void run(AccountManagerFuture<Bundle> result) {

            try {
                Bundle bundle = result.getResult();
                Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);
                if (intent != null) {
                    // User input required
                    intent.setFlags(0);
                    startActivityForResult(intent, ACCOUNT_RESULT);
                } else {
                    onGetAuthToken(bundle);
                }
            } catch (Exception e) {
                Log.e("GetAuthTokenCallback Exception", e.toString());
            }
        }
    }


    /**
     * Method to run asyntask to redirect to website using Auth Token
     *
     * @param bundle result
     */
    protected void onGetAuthToken(Bundle bundle) {
        String auth_token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        if (isAuthToken) {

            //Invalidate and regenerate the Auth token sometimes it expires
            accountManager.invalidateAuthToken("com.google", auth_token);
            accountManager.getAuthToken(accounts[mAccountPosition], "ah", false, new GetAuthTokenCallback(), null);

            Log.e("auth_token", auth_token);
            isAuthToken = false;
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                new GetCookieTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, auth_token);
            else
                new GetCookieTask().execute(auth_token);
        }
    }

    // Get the cookie and redirect the URL
    private class GetCookieTask extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String... tokens) {

            HttpResponse response = null;

            try {
                // Don't follow redirects
                http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);

                HttpGet http_get = new HttpGet("http://remote.sensors.aginova.com/_ah/login?continue=http://remote.sensors.aginova.com/user/mobile&auth=" + tokens[0]);
                response = http_client.execute(http_get);
                if (response.getStatusLine().getStatusCode() != 302)
                    // Response should be a redirect
                    return false;

                for (Cookie cookie : http_client.getCookieStore().getCookies()) {
                    if (cookie.getName().equals("SACSID"))
                        return true;
                }


            } catch (Exception e) {
                Log.e("GetCookieTask Exception", e.toString());
            } finally {
                http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
                try {
                    response.getEntity().consumeContent();
                } catch (Exception e) {
                    Log.e("GetCookieTask Finally Exception ", e.toString());
                }
            }
            return false;
        }

        protected void onPostExecute(Boolean result) {
            Log.e("Boolean", String.valueOf(result));

            String httpType = "http";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                new AuthenticatedRequestTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, httpType + "://remote.sensors.aginova.com/user/mobile");
            else
                new AuthenticatedRequestTask().execute(httpType + "://remote.sensors.aginova.com/user/mobile");

        }
    }

    // Async task to get the details after Log in
    private class AuthenticatedRequestTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            try {
                HttpGet http_get = new HttpGet(urls[0]);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                http_client.execute(http_get).getEntity().writeTo(out);
                out.close();
                return out.toString();

            } catch (Exception e) {

                Log.e("AuthenticatedRequestTask doInBackground", e.toString());
            }
            return null;
        }

        @SuppressWarnings("deprecation")
        protected void onPostExecute(String result) {
            try {
                Log.e("result ", result);
                if (result != null) {
                    mTextViewResponse.setText("Successfully logged in");
                }
            } catch (Exception e) {
                mTextViewResponse.setText("Login failed");
                Log.e("AuthenticatedRequestTask onPostExecute", e.toString());
            }

        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ACCOUNT_RESULT) {
            Log.e("resultCode", String.valueOf(resultCode));
            if (resultCode == RESULT_OK) {
                accountManager.getAuthToken(accounts[mAccountPosition], "ah", false, new GetAuthTokenCallback(), null);
            }
        }
    }

}
