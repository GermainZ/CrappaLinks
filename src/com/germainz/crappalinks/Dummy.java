package com.germainz.crappalinks;

import android.app.Activity;
import android.os.Bundle;

public class Dummy extends Activity {
    // This activity exists for one reason: some apps don't check if the intent is null before firing
    // it, or try to get some information from it (so we can't just return an empty intent there.)
    // Instead of trying to find and hook more methods to 'fix' this, we'll just turn the implicit
    // intent into an explicit one, and force it to open with our app (this activity,) then immediately
    // finish the activity.
    // That being said, this activity is 'invisible' since we're using Theme.NoDisplay,
    // so the user won't be disturbed.
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}