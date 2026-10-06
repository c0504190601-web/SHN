package com.kioskmdm;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class UI {
    static final int INK = 0xff182336, MUTED = 0xff788497, ACCENT = 0xff4263df;
    static final int BACKGROUND = 0xfff5f6fa;

    static int dp(Context c, int n) {
        return (int) (n * c.getResources().getDisplayMetrics().density + .5f);
    }

    static GradientDrawable bg(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    static LinearLayout root(Activity a, String title) {
        a.getWindow().setStatusBarColor(BACKGROUND);
        a.getWindow().setNavigationBarColor(BACKGROUND);
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        // Keep controls outside status/navigation bars, including Android 15 edge-to-edge.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(a, 22), dp(a, 32), dp(a, 22), dp(a, 32));
        TextView heading = note(a, title);
        heading.setTextColor(INK);
        heading.setTextSize(30);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(0, dp(a, 4), 0, dp(a, 22));
        root.addView(heading);
        scroll.addView(root);
        a.setContentView(scroll);
        scroll.requestApplyInsets();
        return root;
    }

    static LinearLayout card(Activity a, LinearLayout parent, String title) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 16));
        card.setBackground(bg(Color.WHITE, dp(a, 22)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(a, 6), 0, dp(a, 10));
        parent.addView(card, params);
        TextView label = note(a, title);
        label.setTextSize(18);
        label.setTextColor(INK);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(label);
        return card;
    }

    static Button b(Activity a, String text) {
        Button button = new Button(a);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(a, 54));
        button.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),
                bg(ACCENT, dp(a, 16)), null));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(a, 6), 0, dp(a, 6));
        button.setLayoutParams(params);
        return button;
    }

    static Button secondary(Activity a, String text) {
        Button button = b(a, text);
        button.setTextColor(ACCENT);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x203568e8),
                bg(0xffeaf0ff, dp(a, 16)), null));
        return button;
    }

    static Switch sw(Activity a, String text, boolean on) {
        Switch sw = new Switch(a);
        sw.setText(text);
        sw.setTextSize(16);
        sw.setTextColor(INK);
        sw.setChecked(on);
        sw.setSwitchPadding(dp(a, 18));
        sw.setGravity(Gravity.CENTER_VERTICAL);
        sw.setMinHeight(dp(a, 62));
        sw.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 14));
        sw.setBackground(bg(Color.WHITE, dp(a, 16)));
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{ACCENT, 0xff8a96a9}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xffcbd9fc, 0xffe3e8ef}));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(a, 4), 0, dp(a, 4));
        sw.setLayoutParams(params);
        return sw;
    }

    static TextView note(Activity a, String text) {
        TextView view = new TextView(a);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(MUTED);
        view.setGravity(Gravity.START);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        view.setLineSpacing(dp(a, 3), 1);
        view.setPadding(0, dp(a, 8), 0, dp(a, 10));
        return view;
    }

    static void msg(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_SHORT).show();
    }
}
