package com.germainz.crappalinks;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class CrappaLinks implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        String pkg = lpparam.packageName;
        if (pkg.equals("com.quoord.tapatalkpro.activity") ||
                pkg.equals("com.quoord.tapatalkHD") ||
                pkg.equals("com.quoord.tapatalkxdapre.activity")) {
            final Class<?> TagHandler = findClass("com.quoord.tapatalkpro.adapter.forum.MessageContentAdapter", lpparam.classLoader);
            // Not sure when openUrlBySkimlink/doSkimlik are called instead of openUrlByVglink/doVglink,
            // it's never happened with me but better safe than sorry. Both methods take the same
            // argument so we can replace them with the same method.
            if (pkg.equals("com.quoord.tapatalkxdapre.activity")) {
                hookCrappaTalk(TagHandler, "openUrlBySkimlink");
                hookCrappaTalk(TagHandler, "openUrlByViglink");
            } else {
                hookCrappaTalk(TagHandler, "doVglink");
                hookCrappaTalk(TagHandler, "doSkimlik");
            }
        } else if (pkg.equals("com.android.vending")) {
            final Class<?> TagHandler = findClass("com.google.android.finsky.api.model.Document", lpparam.classLoader);
            final Class<?> TagHandler2 = findClass("com.google.android.play.layout.PlayTextView$SelfishUrlSpan", lpparam.classLoader);
            findAndHookMethod(TagHandler, "getRawDescription", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // I should probably find a way to do the changes when the link is actually clicked,
                    // possibly in com.google.android.play.layout.PlayTextView$SelfishUrlSpan
                    String s = (String) param.getResult();
                    s = s.replaceAll("<a href=\"https://www\\.google\\.com/url\\?q=", "<a href=\"");
                    s = s.replaceAll("&amp;sa=[^\"]*\"", "\"");
                    param.setResult(s);
                }
            });
        } else if (pkg.equals("com.facebook.katana")) {
            final Class<?> TagHandler = findClass("com.facebook.katana.urimap.Fb4aUriIntentMapper", lpparam.classLoader);
            final Class<?> TagHandler2 = findClass("com.facebook.intent.ufiservices.DefaultUriIntentGenerator", lpparam.classLoader);
            findAndHookMethod(TagHandler, "a", Context.class, String.class, new XC_MethodHook() {
                // This method returns an intent generated from the masked uri, if the Facebook app
                // can't handle it (external links.)
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Intent intent = (Intent) param.getResult();
                    String uri = unmaskFacebook((String) param.args[1]);
                    intent.setData(Uri.parse(uri));
                    param.setResult(intent);
                }
            });
            findAndHookMethod(TagHandler2, "a", String.class, new XC_MethodHook() {
                // This method launches an intent to view the given string, without doing any checks.
                // So we just have to the given argument.
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = unmaskFacebook((String) param.args[0]);
                }
            });
        } else if (pkg.equals("com.vkontakte.android")) {
            final Class<?> TagHandler = findClass("com.vkontakte.android.LinkRedirActivity", lpparam.classLoader);
            findAndHookMethod(TagHandler, "openBrowser", Uri.class, new XC_MethodReplacement() {
                // This method takes one argument, the unmasked link, and generates an intent for it.
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    Uri uri = (Uri) param.args[0];
                    Intent intent;
                    intent = new Intent("android.intent.action.VIEW", uri);
                    intent = intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Application app = AndroidAppHelper.currentApplication();
                    try {
                        app.startActivity(intent);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                    return null;
                }
            });
        } else if (pkg.equals("com.alphascope")) {
            final Class<?> TagHandler = findClass("com.alphascope.lib.util.ViglinkLinkify", lpparam.classLoader);
            findAndHookMethod(TagHandler, "getVigLinkURL", String.class, new XC_MethodReplacement() {
                // This method takes one argument (the unmasked link,) masks it, then returns the masked URL.
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return param.args[0];
                }
            });
        }
    }

    private String unmaskFacebook(String s) {
        // masked links look like (http|https)://m.facebook.com/l.php?u=<actual_link>&h=<some_token>
        if (!s.startsWith("http://m.facebook.com/l.php?u=") &&
                !s.startsWith("https://m.facebook.com/l.php?u="))
            return s; // it's not an external link, we shouldn't mess with it
        s = s.replaceAll("^https?://m\\.facebook\\.com/l\\.php\\?u=", "");
        s = s.replaceAll("&h.*$", "");
        try {
            s = URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return s;
    }

    private void hookCrappaTalk(final Class<?> TagHandler, String method) {
        findAndHookMethod(TagHandler, method, String.class, new XC_MethodReplacement() {
            // These methods all take the unmasked link as their argument, and launch and intent to
            // view it after masking it.
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                String s = (String) param.args[0]; // this is the original URL
                Activity mContext = (Activity) getObjectField(param.thisObject, "mContext");
                Intent intent;
                intent = new Intent("android.intent.action.VIEW", Uri.parse(s));
                try {
                    mContext.startActivity(intent);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
                return null;
            }
        });
    }

}

