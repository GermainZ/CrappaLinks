package com.germainz.crappalinks;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;

public class CrappaLinks implements IXposedHookZygoteInit {

    private final static XSharedPreferences PREFS = new XSharedPreferences("com.germainz.crappalinks");
    private final static boolean PREF_UNSHORTEN_URLS = PREFS.getBoolean("pref_unshorten_urls", true);

    public void initZygote(StartupParam startupParam) throws Throwable {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent = (Intent) param.args[0];

                // We're only interested in ACTION_VIEW intents.
                String intentAction = intent.getAction();
                if (intentAction == null || !intentAction.equals("android.intent.action.VIEW"))
                    return;

                // If the data isn't a URL (http/https) do nothing.
                Uri intentData = intent.getData();
                if (intentData == null || !intentData.toString().startsWith("http"))
                    return;

                // Unmask the URL (nested masked URLs, too.)
                Uri unmaskedUrl = intentData;
                Uri finalUrl = unmaskedUrl;
                int i = Helper.getMaskedId(unmaskedUrl);
                while (i >= 0) {
                    unmaskedUrl = Helper.unmaskLink(unmaskedUrl, i);
                    if (unmaskedUrl.equals(finalUrl))
                        break;
                    finalUrl = unmaskedUrl;
                    i = Helper.getMaskedId(unmaskedUrl);
                }

                // Does the URL need to be unshortened?
                // The hasExtra check is to check if the URL is sent from our own app after being
                // unshortened, so we,don't start an infinite loop.
                if (PREF_UNSHORTEN_URLS && !intent.hasExtra("crappalinks") &&
                        Helper.isRedirect(unmaskedUrl.getHost())) {
                    // Have the shortened URL open with our activity. It'll be handled there.
                    intent.setComponent(ComponentName.unflattenFromString(
                            "com.germainz.crappalinks/com.germainz.crappalinks.Resolver"));
                }

                // We just set the intent's data to the unmasked URL. If this URL needs to be unshortened,
                // it'll open with our activity (since we explicitly set the component above) which
                // will unshorten it then send a new intent.
                intent.setDataAndType(unmaskedUrl, intent.getType());
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            findAndHookMethod("android.app.ContextImpl", null, "startActivity", Intent.class, Bundle.class, hook);
            findAndHookMethod(Activity.class, "startActivity", Intent.class, Bundle.class, hook);
        } else {
            findAndHookMethod("android.app.ContextImpl", null, "startActivity", Intent.class, hook);
            findAndHookMethod(Activity.class, "startActivity", Intent.class, hook);
        }

    }

}
