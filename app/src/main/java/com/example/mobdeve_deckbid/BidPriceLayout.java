package com.example.mobdeve_deckbid;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public final class BidPriceLayout extends LinearLayout {
    public BidPriceLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        int stroke = Math.max(1, Math.round(getResources().getDisplayMetrics().density));
        GradientDrawable divider = new GradientDrawable();
        divider.setSize(stroke, stroke);
        divider.setColor(context.getColor(R.color.border));
        setDividerDrawable(divider);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int naturalWidth = getPaddingLeft() + getPaddingRight()
                + Math.max(0, getChildCount() - 1) * getDividerDrawable().getIntrinsicWidth();
        int unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(unbounded, unbounded);
            naturalWidth += getChildAt(i).getMeasuredWidth();
        }
        boolean fits = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                || naturalWidth <= MeasureSpec.getSize(widthSpec);
        setOrientation(fits ? HORIZONTAL : VERTICAL);
        super.onMeasure(widthSpec, heightSpec);
    }
}
