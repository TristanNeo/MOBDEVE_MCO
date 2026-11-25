package com.example.mobdeve_deckbid;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.TextView;

final class CardAppearance {
    private CardAppearance() { }

    static int logo(String game) {
        switch (game) {
            case "Pokémon": return R.drawable.logo_pokemon;
            case "One Piece": return R.drawable.logo_one_piece;
            case "Yu-Gi-Oh!": return R.drawable.logo_yugioh;
            case "Magic: The Gathering": return R.drawable.logo_mtg;
            default: return 0;
        }
    }

    static void condition(TextView view, String grade) {
        int[] rim, fill;
        int ink;
        if ("PSA 10".equals(grade)) {
            rim = new int[]{0xFFF2AFA9, 0xFFE5CF86, 0xFF91D9C4, 0xFF9BBBBE, 0xFFC7A2E2};
            fill = new int[]{0xFF443348, 0xFF233E46, 0xFF382F49};
            ink = 0xFFF7F0FF;
        } else if ("PSA 9".equals(grade)) {
            rim = new int[]{0xFF9D692A, 0xFFF5CD83, 0xFFB78338};
            fill = new int[]{0xFF55371E, 0xFF3D2B1B};
            ink = 0xFFFFD295;
        } else {
            rim = new int[]{0xFF85753B, 0xFFD8C866};
            fill = new int[]{0xFF3F3C24, 0xFF302F20};
            ink = 0xFFF3E397;
        }
        float density = view.getResources().getDisplayMetrics().density;
        LayerDrawable pill = new LayerDrawable(new android.graphics.drawable.Drawable[]{band(rim, density), band(fill, density)});
        int edge = Math.max(1, Math.round(density));
        pill.setLayerInset(1, edge, edge, edge, edge);
        view.setBackground(pill);
        view.setPadding(Math.round(10 * density), Math.round(3 * density), Math.round(10 * density), Math.round(3 * density));
        view.setText(grade);
        view.setTextColor(ink);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private static GradientDrawable band(int[] stops, float density) {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, stops);
        shape.setCornerRadius(32 * density);
        return shape;
    }
}
