package com.blockbench.android;

import android.os.Bundle;
import android.view.View;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(BlockbenchPlugin.class);

        super.onCreate(savedInstanceState);

        getBridge()
            .getWebView()
            .setLayerType(View.LAYER_TYPE_HARDWARE, null);
        getBridge()
            .getWebView()
            .addJavascriptInterface(
                new AndroidFS(this),
                "BlockbenchFS"
            );
    }
}
