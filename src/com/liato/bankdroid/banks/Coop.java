/*
 * Copyright (C) 2010 Nullbyte <http://nullbyte.eu>
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

package com.liato.bankdroid.banks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;

import com.liato.bankdroid.Account;
import com.liato.bankdroid.Bank;
import com.liato.bankdroid.BankException;
import com.liato.bankdroid.Helpers;
import com.liato.bankdroid.LoginException;
import com.liato.bankdroid.R;
import com.liato.bankdroid.Transaction;
import com.liato.urllib.Urllib;

public class Coop extends Bank {
    private static final String TAG = "Coop";
    private static final String NAME = "Coop";
    private static final String NAME_SHORT = "coop";
    private static final String URL = "https://www.coop.se/mina-sidor/oversikt/";
    private static final int BANKTYPE_ID = Bank.COOP;

    private Pattern reViewState = Pattern.compile("__VIEWSTATE\"\\s+value=\"([^\"]+)\"");
    //private Pattern reBalanceVisa = Pattern.compile("MedMera\\s*Visa</h3>\\s*<h6>Disponibelt\\s*belopp[^<]*</h6>\\s*<ul>(.*?)</ul>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private Pattern reBalance = Pattern.compile("saldo\">([^<]+)<", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private Pattern reTransactions = Pattern.compile("<td>\\s*(\\d{4}-\\d{2}-\\d{2})\\s*</td>\\s*<td>([^<]+)</td>\\s*<td>([^<]*)</td>\\s*<td>([^<]*)</td>\\s*<td[^>]*>(?:\\s*<a[^>]+>)?([^<]+)(?:</a>\\s*)?</td>", Pattern.CASE_INSENSITIVE);
    private String response;

    public Coop(Context context) {
        super(context);
        super.TAG = TAG;
        super.NAME = NAME;
        super.NAME_SHORT = NAME_SHORT;
        super.BANKTYPE_ID = BANKTYPE_ID;
        super.URL = URL;
    }

    public Coop(String username, String password, Context context) throws BankException, LoginException {
        this(context);
        this.update(username, password);
    }

    @Override
    public Urllib login() throws LoginException, BankException {
        urlopen = new Urllib();
        Matcher matcher;
        try {
            response = urlopen.open("https://www.coop.se/Mina-sidor/Oversikt/");
            matcher = reViewState.matcher(response);
            if (!matcher.find()) {
                throw new BankException(res.getText(R.string.unable_to_find).toString()+" viewstate.");
            }
            String strViewState = matcher.group(1);
            List <NameValuePair> postData = new ArrayList <NameValuePair>();
            postData.add(new BasicNameValuePair("ctl00$ContentPlaceHolderTodo$ContentPlaceHolderMainPageContainer$ContentPlaceHolderMainPageWithNavigationAndGlobalTeaser$ContentPlaceHolderPreContent$RegisterMediumUserForm$TextBoxUserName", username));
            postData.add(new BasicNameValuePair("ctl00$ContentPlaceHolderTodo$ContentPlaceHolderMainPageContainer$ContentPlaceHolderMainPageWithNavigationAndGlobalTeaser$ContentPlaceHolderPreContent$RegisterMediumUserForm$TextBoxPassword", password));
            postData.add(new BasicNameValuePair("ctl00$ContentPlaceHolderTodo$ContentPlaceHolderMainPageContainer$ContentPlaceHolderMainPageWithNavigationAndGlobalTeaser$ContentPlaceHolderPreContent$RegisterMediumUserForm$ButtonLogin", "Logga in"));
            postData.add(new BasicNameValuePair("__VIEWSTATE", strViewState));
            postData.add(new BasicNameValuePair("__EVENTTARGET", ""));
            postData.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
            response = urlopen.open("https://www.coop.se/Mina-sidor/Oversikt/", postData);
            Log.d(TAG, urlopen.getCurrentURI());
            if (response.contains("forfarande logga in med ditt personnummer")) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                if (prefs.getBoolean("debug_mode", false) && prefs.getBoolean("debug_coop_sendmail", false)) {
                    Intent i = new Intent(android.content.Intent.ACTION_SEND);
                    i.setType("plain/text");
                    i.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"android@nullbyte.eu"});
                    i.putExtra(android.content.Intent.EXTRA_SUBJECT, "Bankdroid - Coop Error");
                    i.putExtra(android.content.Intent.EXTRA_TEXT, response);
                    context.startActivity(i);
                }
                throw new LoginException(res.getText(R.string.invalid_username_password).toString());
            }
        }
        catch (ClientProtocolException e) {
            throw new BankException(e.getMessage());
        }
        catch (IOException e) {
            throw new BankException(e.getMessage());
        }
        return urlopen;		
    }

    @Override
    public void update() throws BankException, LoginException {
        super.update();
        if (username == null || password == null || username.length() == 0 || password.length() == 0) {
            throw new LoginException(res.getText(R.string.invalid_username_password).toString());
        }

        urlopen = login();
        Matcher matcher;
        Account account;


        class RequestDetails {
            public String url, name, id;
            public RequestDetails(String url, String name, String id) {
                this.url = url;
                this.name = name;
                this.id = id;
            }
        }    
        ArrayList<RequestDetails> arrRD = new ArrayList<RequestDetails>();
        arrRD.add(new RequestDetails("https://www.coop.se/Mina-sidor/Oversikt/Kontoutdrag-MedMera-Visa/", "MedMera Visa", "1"));
        arrRD.add(new RequestDetails("https://www.coop.se/Mina-sidor/Oversikt/MedMera-Konto/", "MedMera Konto", "2"));

        for (RequestDetails rd : arrRD) {
            try {
                response = urlopen.open(rd.url);
                matcher = reBalance.matcher(response);
                if (matcher.find()) {
                    account = new Account(rd.name, Helpers.parseBalance(matcher.group(1).trim()), rd.id);
                    balance = balance.add(Helpers.parseBalance(matcher.group(1)));
                    matcher = reTransactions.matcher(response);
                    ArrayList<Transaction> transactions = new ArrayList<Transaction>();
                    while (matcher.find()) {
                        /*
                         * Capture groups:
                         * GROUP                EXAMPLE DATA
                         * 1: Date              2010-11-04
                         * 2: Activity          Köp
                         * 3: User              John Doe
                         * 4: Place             Coop Extra Stenungsund
                         * 5: Amount            -809,37 kr
                         *                      * 
                         */

                        String title = Html.fromHtml(matcher.group(4)).toString().trim().length() > 0 ? Html.fromHtml(matcher.group(4)).toString().trim() : Html.fromHtml(matcher.group(2)).toString().trim();
                        if (Html.fromHtml(matcher.group(3)).toString().trim().length() > 0) {
                            title = title + " (" + Html.fromHtml(matcher.group(3)).toString().trim() + ")";
                        }
                        transactions.add(new Transaction(matcher.group(1).trim(),
                                title,
                                Helpers.parseBalance(matcher.group(5))));
                    }
                    account.setTransactions(transactions);
                    accounts.add(account);
                }
            }
            catch (ClientProtocolException e) {
                //404 or 500 response
            }
            catch (IOException e) {
            }                        
        }

        if (accounts.isEmpty()) {
            throw new BankException(res.getText(R.string.no_accounts_found).toString());
        }
        super.updateComplete();
    }
}