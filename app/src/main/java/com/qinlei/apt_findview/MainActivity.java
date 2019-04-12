package com.qinlei.apt_findview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.qinlei.apt_annotation.BindView;
import com.qinlei.apt_library.ProxyTool;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.tv_hello)
    TextView tvHello;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ProxyTool.bind(this);
        tvHello.setText("Hello APT");
    }
}
