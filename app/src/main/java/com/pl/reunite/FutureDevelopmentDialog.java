/*
 * (c) 2012 Martin van Zuilekom (http://martin.cubeactive.com)
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
 *
 */

package com.pl.reunite;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.text.format.DateFormat;
import android.util.Log;
import android.webkit.WebView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Class to show a change log dialog
 */
public class FutureDevelopmentDialog {
    private static final String TAG = "FutureDevelopmentDialog";

    private final Context mContext;
    private String mStyle = "h1 { margin-left: 0px; font-size: 12pt; }"
            + "li { margin-left: 0px; font-size: 9pt; }"
            + "ul { padding-left: 30px; }"
            + ".summary { font-size: 9pt; color: #606060; display: block; clear: left; }"
            + ".date { font-size: 9pt; color: #606060;  display: block; }";

    protected DialogInterface.OnDismissListener mOnDismissListener;

    public FutureDevelopmentDialog(final Context context) {
        mContext = context;
    }

    protected Context getContext() {
        return mContext;
    }

    //Get the current app version
    private String getAppVersion() {
        String versionName = "";
        try {
            final PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (NameNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return versionName;
    }

    //Parse a date string from the xml and format it using the local date format
    private String parseDate(final String dateString) {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        try {
            final Date parsedDate = dateFormat.parse(dateString);
            return DateFormat.getDateFormat(getContext()).format(parsedDate);
        } catch (ParseException ignored) {
            //If there is a problem parsing the date just return the original string
            return dateString;
        }
    }

    //Parse a the release tag and appends it to the futuredevelopment builder
    private void parseReleaseTag(final StringBuilder FutureDevelopmentBuilder, final XmlPullParser resourceParser) throws XmlPullParserException, IOException {
        FutureDevelopmentBuilder.append("<h1>Version: ").append(resourceParser.getAttributeValue(null, "version")).append("</h1>");

        //Add summary if available
        if (resourceParser.getAttributeValue(null, "summary") != null) {
            FutureDevelopmentBuilder.append("<span class='summary'>").append(resourceParser.getAttributeValue(null, "summary")).append("</span>");
        }

        //Add date if available
        if (resourceParser.getAttributeValue(null, "date") != null) {
            FutureDevelopmentBuilder.append("<span class='date'>").append(parseDate(resourceParser.getAttributeValue(null, "date"))).append("</span>");
        }

        FutureDevelopmentBuilder.append("<ul>");

        //Parse child nodes
        int eventType = resourceParser.getEventType();
        while ((eventType != XmlPullParser.END_TAG) || (resourceParser.getName().equals("change"))) {
            if ((eventType == XmlPullParser.START_TAG) && (resourceParser.getName().equals("change"))) {
                eventType = resourceParser.next();
                FutureDevelopmentBuilder.append("<li>" + resourceParser.getText() + "</li>");
            }
            eventType = resourceParser.next();
        }
        FutureDevelopmentBuilder.append("</ul>");
    }

    //CSS style for the html
    private String getStyle() {
        return String.format("<style type=\"text/css\">%s</style>", mStyle);
    }

    public void setStyle(final String style) {
        mStyle = style;
    }

    public FutureDevelopmentDialog setOnDismissListener(final DialogInterface.OnDismissListener onDismissListener) {
        mOnDismissListener = onDismissListener;
        return this;
    }

    //Get the FutureDevelopment in html code, this will be shown in the dialog's webview
    public String getHTMLFutureDevelopment(final int resourceId, final Resources resources, final int version) {
        boolean releaseFound = false;
        final StringBuilder FutureDevelopmentBuilder = new StringBuilder();
        FutureDevelopmentBuilder.append("<html><head>").append(getStyle()).append("</head><body>");
        final XmlResourceParser xml = resources.getXml(resourceId);
        try {
            int eventType = xml.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if ((eventType == XmlPullParser.START_TAG) && (xml.getName().equals("release"))) {
                    //Check if the version matches the release tag.
                    //When version is 0 every release tag is parsed.
                    final int versioncode = Integer.parseInt(xml.getAttributeValue(null, "versioncode"));
                    if ((version == 0) || (versioncode == version)) {
                        parseReleaseTag(FutureDevelopmentBuilder, xml);
                        releaseFound = true; //At lease one release tag has been parsed.
                    }
                }
                eventType = xml.next();
            }
        } catch (XmlPullParserException e) {
            Log.e(TAG, e.getMessage(), e);
            return "";
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            return "";
        } finally {
            xml.close();
        }
        FutureDevelopmentBuilder.append("</body></html>");

        //Check if there was a release tag parsed, if not return an empty string.
        if (releaseFound) {
            return FutureDevelopmentBuilder.toString();
        } else {
            return "";
        }
    }

    //Returns change log in HTML format 
    public String getHTML() {
        //Get resources
        final String packageName = mContext.getPackageName();
        final Resources resources;
        try {
            resources = mContext.getPackageManager().getResourcesForApplication(packageName);
        } catch (NameNotFoundException ignored) {
            return "";
        }

        //Create HTML change log
        return getHTMLFutureDevelopment(R.xml.future_development_dis, resources, 0);
    }

    //Call to show the change log dialog
    public void show() {
        show(0);
    }

    protected void show(final int version) {
        //Get resources
        final String packageName = mContext.getPackageName();
        final Resources resources;
        try {
            resources = mContext.getPackageManager().getResourcesForApplication(packageName);
        } catch (NameNotFoundException ignored) {
            return;
        }

        //Get dialog title	        	
        String title = resources.getString(R.string.title_future_development);
        title = String.format("%s v%s", title, getAppVersion());

        //Create html future development log
        final String htmlFutureDevelopment = getHTMLFutureDevelopment(R.xml.future_development_dis, resources, version);

        /**
         * to write a html file
         */
        if (!htmlFutureDevelopment.isEmpty()) {
            writeToFile(htmlFutureDevelopment);
        }

        //Get button strings
        final String closeString = resources.getString(R.string.future_development_close);

        //Check for empty change log
        if (htmlFutureDevelopment.length() == 0) {
            //It seems like there is nothing to show, just bail out.
            return;
        }

        //Create web view and load html
        final WebView webView = new WebView(mContext);
        webView.loadDataWithBaseURL(null, htmlFutureDevelopment, "text/html", "utf-8", null);
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setTitle(title)
                .setView(webView)
                .setPositiveButton(closeString, new Dialog.OnClickListener() {
                    public void onClick(final DialogInterface dialogInterface, final int i) {
                        dialogInterface.dismiss();
                    }
                })
                .setOnCancelListener( new OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(final DialogInterface dialog) {
                if (mOnDismissListener != null) {
                    mOnDismissListener.onDismiss(dialog);
                }
            }
        });
        dialog.show();
    }

    private void writeToFile(String data) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(mContext.openFileOutput("future_development.html", Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }
}
