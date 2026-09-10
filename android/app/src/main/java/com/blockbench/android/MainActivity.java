package com.blockbench.android;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(BlockbenchPlugin.class);

        super.onCreate(savedInstanceState);

        getBridge()
            .getWebView()
            .addJavascriptInterface(
                new AndroidFS(this),
                "BlockbenchFS"
            );
    }
}
