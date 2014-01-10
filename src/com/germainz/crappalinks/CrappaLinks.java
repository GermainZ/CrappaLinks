package com.germainz.crappalinks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class CrappaLinks implements IXposedHookLoadPackage {

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        String pkg = lpparam.packageName;
		if (!pkg.equals("com.quoord.tapatalkpro.activity") &&
                !pkg.equals("com.quoord.tapatalkHD") &&
                !pkg.equals("com.quoord.tapatalkxdapre.activity"))
            return;
        final Class<?> TagHandler = findClass("com.quoord.tapatalkpro.adapter.forum.MessageContentAdapter", lpparam.classLoader);
        // Not sure when openUrlBySkimlink/doSkimlik are called instead of openUrlByVglink/doVglink,
        // it's never happened with me but better safe than sorry. Both methods take the same
        // argument so we can replace them with the same method.
        if (pkg.equals("com.quoord.tapatalkxdapre.activity")) {
            doHookMethod(TagHandler, "openUrlBySkimlink");
            doHookMethod(TagHandler, "openUrlByViglink");
        } else {
        doHookMethod(TagHandler, "doVglink");
        doHookMethod(TagHandler, "doSkimlik");
        }
    }

    private void doHookMethod(final Class<?> TagHandler, String method) {
        findAndHookMethod(TagHandler, method, String.class, new XC_MethodReplacement() {
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

